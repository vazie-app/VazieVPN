import Darwin
import Foundation
import Network

public enum TunnelStartError: Error, Equatable, Sendable {
    /// There is no engine to run.
    case engine
    /// The configuration was not something the engine accepts.
    case config
    /// A loopback port could not be bound.
    case bind
    /// The engine did not come up in time.
    case timeout
    /// The administrator's prompt was cancelled.
    case cancelled
    /// The administrator's prompt could not be shown, or the helper could not be run.
    case privilege
    /// The interface opened and the Mac's traffic does not go through it.
    case bypassed
    /// The engine copy the root supervisor was given is not one it trusts (not signed by this app's team, not a pinned build).
    case integrity
    /// The Mac's way out is not the one the document was made for, or there is none.
    case network
    /// The tunnel interface could not be opened or given its address.
    case tun
    /// The routes into the tunnel could not be made.
    case routes
    /// The Mac's DNS could not be pointed at the tunnel.
    case dns
    /// The kill switch could not be put in place, so the tunnel was not opened without it.
    case killSwitch
}

/// What a running tunnel offers the controller. A fake stands in for it in the tests.
public protocol Tunnel: AnyObject, Sendable {
    var httpPort: Int { get }
    var socksPort: Int { get }
    func stop()
    /// Bytes through the tunnel so far, from the engine's own counters.
    func traffic() async -> Traffic?
    /// Called once if the engine ends by itself.
    func onExit(_ handler: @escaping @Sendable () -> Void)
    /// Stops, and returns once the Mac is as it was before. (A requirement, not only an extension: through an existential
    /// the extension's version would be called and the tunnel's own would never be waited for.)
    func shutdown() async
}

public protocol TunnelEngine: Sendable {
    func start(_ outbound: VlessOutbound) async throws -> Tunnel
    /// The same, with a split by site (the whole-Mac mode decides it in the engine; the proxy mode in the system's script).
    func start(_ outbound: VlessOutbound, split: SplitTunnel) async throws -> Tunnel
    /// Proves the tunnel is usable in the two ways it can separately fail; `nil` when it is.
    func readiness(of tunnel: Tunnel) async -> Probe.Stage?
    /// Milliseconds for a TCP handshake over this Mac's own network, or `nil`.
    func latency(host: String, port: Int) async -> Int?
    /// Whether a request made the way any program on this Mac makes one (following the system's proxy settings) is
    /// seen by the tunnel. The settings may say yes while another VPN's network extension answers instead.
    func carriesSystemTraffic(_ tunnel: Tunnel) async -> Bool
    /// Starts the engine again inside the tunnel that is already there (no new password), for the network as it is now.
    /// `false` when this engine cannot, and a new tunnel must be started instead.
    func restart(_ tunnel: Tunnel, outbound: VlessOutbound, split: SplitTunnel) async throws -> Bool
}

extension TunnelEngine {
    public func start(_ outbound: VlessOutbound, split: SplitTunnel) async throws -> Tunnel { try await start(outbound) }
    public func carriesSystemTraffic(_ tunnel: Tunnel) async -> Bool { true }
    /// Engines that cannot start again inside a running tunnel say so, and the caller starts a new one.
    public func restart(_ tunnel: Tunnel, outbound: VlessOutbound, split: SplitTunnel) async throws -> Bool { false }
}

extension Tunnel {
    /// Stops, and returns once the Mac is as it was before. Tunnels that stop at once need nothing more.
    public func shutdown() async { stop() }
}

/// One running Xray: started from a configuration composed here, stopped on request, when the app quits, or when
/// the engine dies on its own. The configuration goes down a pipe: the user's keys never touch the disk.
public final class XraySession: Tunnel, @unchecked Sendable {
    public let httpPort: Int
    public let socksPort: Int
    private let apiPort: Int
    private let binary: URL
    private let process: Process
    private let lock = NSLock()
    private var exitHandler: (@Sendable () -> Void)?
    private var exited = false
    private var stopping = false

    private init(binary: URL, process: Process, http: Int, socks: Int, api: Int) {
        self.binary = binary
        self.process = process
        httpPort = http
        socksPort = socks
        apiPort = api
    }

    static func freePort() -> Int? {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return nil }
        defer { close(fd) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_addr = in_addr(s_addr: UInt32(0x7F00_0001).bigEndian)
        address.sin_port = 0
        let bound = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        guard bound == 0 else { return nil }
        var length = socklen_t(MemoryLayout<sockaddr_in>.size)
        let named = withUnsafeMutablePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { getsockname(fd, $0, &length) }
        }
        guard named == 0 else { return nil }
        return Int(UInt16(bigEndian: address.sin_port))
    }

    /// Whether something accepts connections on a loopback port.
    static func accepts(port: Int, timeoutMs: Int32 = 150) -> Bool {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return false }
        defer { close(fd) }
        let flags = fcntl(fd, F_GETFL, 0)
        _ = fcntl(fd, F_SETFL, flags | O_NONBLOCK)
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_addr = in_addr(s_addr: UInt32(0x7F00_0001).bigEndian)
        address.sin_port = UInt16(port).bigEndian
        let result = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        if result == 0 { return true }
        guard errno == EINPROGRESS else { return false }
        var poller = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
        guard poll(&poller, 1, timeoutMs) > 0 else { return false }
        var error: Int32 = 0
        var length = socklen_t(MemoryLayout<Int32>.size)
        getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &length)
        return error == 0
    }

    public static func start(binary: URL, outbound: VlessOutbound, startupTimeout: TimeInterval = 8) async throws -> XraySession {
        guard FileManager.default.isExecutableFile(atPath: binary.path) else { throw TunnelStartError.engine }
        var ports = Set<Int>()
        while ports.count < 3 {
            guard let port = freePort() else { throw TunnelStartError.bind }
            ports.insert(port)
        }
        let list = Array(ports)
        let (http, socks, api) = (list[0], list[1], list[2])
        let document = XrayConfig.compose(outbound: outbound, httpPort: http, socksPort: socks, apiPort: api)
        guard let config = try? XrayConfig.json(document) else { throw TunnelStartError.config }

        let process = Process()
        process.executableURL = binary
        process.arguments = ["run", "-config", "stdin:"]
        let input = Pipe()
        let errors = Pipe()
        process.standardInput = input
        process.standardOutput = FileHandle.nullDevice
        process.standardError = errors

        let session = XraySession(binary: binary, process: process, http: http, socks: socks, api: api)
        let captured = LockedText()
        errors.fileHandleForReading.readabilityHandler = { handle in
            let data = handle.availableData
            if data.isEmpty { handle.readabilityHandler = nil; return }
            captured.append(String(decoding: data, as: UTF8.self))
        }
        process.terminationHandler = { [weak session] _ in session?.didExit() }
        do { try process.run() } catch { throw TunnelStartError.engine }
        // Written so that a launch after a crash can end an engine the crash left running.
        OrphanedEngine.remember(process.processIdentifier)

        do {
            try input.fileHandleForWriting.write(contentsOf: config)
            try input.fileHandleForWriting.close()
        } catch {
            session.stop()
            throw TunnelStartError.config
        }

        let deadline = Date().addingTimeInterval(startupTimeout)
        while true {
            if !process.isRunning {
                // Whatever the engine printed may quote the configuration, so it is classified here and never
                // forwarded.
                try? await Task.sleep(nanoseconds: 50_000_000)
                let text = captured.value.lowercased()
                throw text.contains("address already in use") ? TunnelStartError.bind : TunnelStartError.config
            }
            if accepts(port: http) && accepts(port: socks) { break }
            if Date() >= deadline {
                session.stop()
                throw TunnelStartError.timeout
            }
            if Task.isCancelled {
                session.stop()
                throw CancellationError()
            }
            try? await Task.sleep(nanoseconds: 60_000_000)
        }
        return session
    }

    private func didExit() {
        OrphanedEngine.forget(process.processIdentifier)
        lock.lock()
        exited = true
        let handler = stopping ? nil : exitHandler
        lock.unlock()
        handler?()
    }

    public func onExit(_ handler: @escaping @Sendable () -> Void) {
        lock.lock()
        exitHandler = handler
        let already = exited && !stopping
        lock.unlock()
        if already { handler() }
    }

    public func stop() {
        lock.lock()
        stopping = true
        lock.unlock()
        if process.isRunning {
            process.terminate()
            // The engine exits promptly on SIGTERM; a stuck one is not left behind.
            DispatchQueue.global().asyncAfter(deadline: .now() + 2) { [process] in
                if process.isRunning { kill(process.processIdentifier, SIGKILL) }
            }
        }
    }

    public func traffic() async -> Traffic? {
        guard let output = await ProcessRunner.run(
            binary, ["api", "statsquery", "--server=127.0.0.1:\(apiPort)", "-pattern", "outbound>>>proxy>>>traffic"], timeout: 3)
        else { return nil }
        return Self.parseTraffic(output)
    }

    /// `{"stat":[{"name":"outbound>>>proxy>>>traffic>>>downlink","value":"123"}, …]}`; a counter that has not moved
    /// yet is simply absent.
    public static func parseTraffic(_ output: Data) -> Traffic? {
        guard let document = try? JSONSerialization.jsonObject(with: output) as? [String: Any] else { return nil }
        var down: Int64 = 0
        var up: Int64 = 0
        for stat in (document["stat"] as? [[String: Any]]) ?? [] {
            let name = stat["name"] as? String ?? ""
            let value: Int64
            if let text = stat["value"] as? String { value = Int64(text) ?? 0 } else if let number = stat["value"] as? NSNumber { value = number.int64Value } else { value = 0 }
            if name.hasSuffix(">>>downlink") { down = value } else if name.hasSuffix(">>>uplink") { up = value }
        }
        return Traffic(rx: down, tx: up)
    }
}

private final class LockedText: @unchecked Sendable {
    private let lock = NSLock()
    private var text = ""
    func append(_ more: String) {
        lock.lock()
        if text.count < 4096 { text += more }
        lock.unlock()
    }
    var value: String { lock.lock(); defer { lock.unlock() }; return text }
}

/// The two checks a person's connection needs: how long a server takes to answer, and whether the tunnel carries
/// traffic at all.
public enum Probe {
    public enum Stage: String, Equatable, Sendable {
        /// A TLS handshake to a literal address, through the tunnel.
        case tls
        /// A name resolved and reached, through the tunnel.
        case dns
    }

    /// Milliseconds for a TCP handshake to `host:port`, the best of two tries, over this machine's own network and
    /// not the tunnel; `nil` when the server did not answer in time.
    ///
    /// The connection is bound to the physical interface (Wi-Fi, Ethernet) so that another VPN's tunnel cannot answer
    /// for the server, and the name is looked up by the system and, if the system answers with a made-up address
    /// (the `198.18.0.0/15` a "fake IP" DNS hands out), again over the physical interface at 1.1.1.1. A handshake
    /// that one of those tunnels completes by itself takes a millisecond and means nothing.
    public static func latency(host: String, port: Int, attempts: Int = 2, timeout: TimeInterval = 3) async -> Int? {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(clamping: port)) else { return nil }
        let interface = await PhysicalInterface.current()
        guard let address = await DNS.resolve(host, via: interface) else { return nil }
        var best: Int?
        for _ in 0..<attempts {
            if let ms = await handshake(address: address, port: nwPort, interface: interface, timeout: timeout) { best = min(best ?? ms, ms) }
        }
        return best
    }

    private static func handshake(address: String, port: NWEndpoint.Port, interface: NWInterface?, timeout: TimeInterval) async -> Int? {
        await withCheckedContinuation { (continuation: CheckedContinuation<Int?, Never>) in
            let parameters = NWParameters.tcp
            // Not through the system proxy, which while connected is this app's own tunnel: the server would be asked to
            // connect to itself and the answer would be nothing.
            parameters.preferNoProxies = true
            if let interface { parameters.requiredInterface = interface }
            let connection = NWConnection(host: NWEndpoint.Host(address), port: port, using: parameters)
            let started = DispatchTime.now()
            let lock = NSLock()
            var done = false
            @Sendable func finish(_ value: Int?) {
                lock.lock()
                if done { lock.unlock(); return }
                done = true
                lock.unlock()
                connection.cancel()
                continuation.resume(returning: value)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    let nanos = DispatchTime.now().uptimeNanoseconds - started.uptimeNanoseconds
                    finish(max(1, Int(nanos / 1_000_000)))
                case .failed, .cancelled:
                    finish(nil)
                default:
                    break
                }
            }
            connection.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + timeout) { finish(nil) }
        }
    }

    /// Asks the tunnel whether a request that follows the system's proxy settings reaches it: the engine's own byte
    /// counters must move.
    public static func systemTrafficReachesTunnel(_ tunnel: Tunnel) async -> Bool {
        let before = await tunnel.traffic()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 8
        configuration.urlCache = nil
        // Left as it is: this session follows the system's proxy settings, which is the point.
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        for _ in 0..<2 {
            _ = try? await session.data(from: URL(string: "https://cp.cloudflare.com/generate_204")!)
            let after = await tunnel.traffic()
            if let before, let after, after.tx > before.tx { return true }
        }
        return false
    }

    /// A session that sends everything through the loopback proxy of `httpPort`.
    static func proxied(httpPort: Int) -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.connectionProxyDictionary = [
            "HTTPEnable": 1, "HTTPProxy": "127.0.0.1", "HTTPPort": httpPort,
            "HTTPSEnable": 1, "HTTPSProxy": "127.0.0.1", "HTTPSPort": httpPort,
        ]
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 10
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        return URLSession(configuration: configuration, delegate: NoRedirect(), delegateQueue: nil)
    }

    private final class NoRedirect: NSObject, URLSessionTaskDelegate {
        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest) async -> URLRequest? { nil }
    }

    /// Proves the tunnel is usable in the two ways it can separately fail, the way the Android app does: reach an
    /// address and finish TLS with it, then reach a name. Any answer at all is a pass; no answer is not.
    public static func readiness(httpPort: Int, session: URLSession? = nil) async -> Stage? {
        let session = session ?? proxied(httpPort: httpPort)
        defer { if session !== URLSession.shared { session.invalidateAndCancel() } }
        do { _ = try await session.data(from: URL(string: "https://1.1.1.1/")!) } catch { return .tls }
        do { _ = try await session.data(from: URL(string: "https://cp.cloudflare.com/generate_204")!) } catch { return .dns }
        return nil
    }
}

/// The production engine: the Xray binary, run as a child process.
public struct XrayEngine: TunnelEngine {
    private let binary: @Sendable () -> URL?

    public init(binary: @escaping @Sendable () -> URL? = { Engine.binary() }) { self.binary = binary }

    public func start(_ outbound: VlessOutbound) async throws -> Tunnel {
        guard let binary = binary() else { throw TunnelStartError.engine }
        return try await XraySession.start(binary: binary, outbound: outbound)
    }

    public func readiness(of tunnel: Tunnel) async -> Probe.Stage? { await Probe.readiness(httpPort: tunnel.httpPort) }

    public func latency(host: String, port: Int) async -> Int? { await Probe.latency(host: host, port: port) }

    public func carriesSystemTraffic(_ tunnel: Tunnel) async -> Bool { await Probe.systemTrafficReachesTunnel(tunnel) }
}

/// The interface the Mac reaches the internet over by itself: Wi-Fi or Ethernet, not a VPN's.
enum PhysicalInterface {
    /// The one named, when the system lists it (the name the routing table gave); else the first Wi-Fi or Ethernet.
    static func named(_ name: String?) async -> NWInterface? {
        let all = await withCheckedContinuation { (continuation: CheckedContinuation<[NWInterface], Never>) in
            let monitor = NWPathMonitor()
            let lock = NSLock()
            var done = false
            monitor.pathUpdateHandler = { path in
                lock.lock()
                if done { lock.unlock(); return }
                done = true
                lock.unlock()
                monitor.cancel()
                continuation.resume(returning: path.availableInterfaces)
            }
            monitor.start(queue: .global())
        }
        if let name, let match = all.first(where: { $0.name == name }) { return match }
        return all.first { $0.type == .wiredEthernet || $0.type == .wifi }
    }

    static func current() async -> NWInterface? {
        await withCheckedContinuation { (continuation: CheckedContinuation<NWInterface?, Never>) in
            let monitor = NWPathMonitor()
            let lock = NSLock()
            var done = false
            monitor.pathUpdateHandler = { path in
                lock.lock()
                if done { lock.unlock(); return }
                done = true
                lock.unlock()
                monitor.cancel()
                continuation.resume(returning: path.availableInterfaces.first { $0.type == .wiredEthernet || $0.type == .wifi })
            }
            monitor.start(queue: .global())
        }
    }
}

/// Just enough DNS to find a server's address when the system's answer cannot be believed.
public enum DNS {
    /// `198.18.0.0/15`: the range "fake IP" resolvers hand out so that a tunnel can catch the connection that follows.
    public static func isFake(_ address: String) -> Bool {
        let parts = address.split(separator: ".").compactMap { Int($0) }
        return parts.count == 4 && parts[0] == 198 && (parts[1] == 18 || parts[1] == 19)
    }

    /// An address for `host`: itself if it is one, else the system's answer, else — when that is made up or missing —
    /// 1.1.1.1's, asked over `interface`.
    static func resolve(_ host: String, via interface: NWInterface?) async -> String? {
        if isIPv4(host) || host.contains(":") { return isFake(host) ? nil : host }
        if let system = systemAddress(host), !isFake(system), !system.hasPrefix("127.") { return system }
        return await lookup(host, via: interface)
    }

    static func isIPv4(_ text: String) -> Bool {
        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        return parts.count == 4 && parts.allSatisfy { Int($0).map { (0...255).contains($0) } == true }
    }

    private static func systemAddress(_ host: String) -> String? {
        var hints = addrinfo(ai_flags: 0, ai_family: AF_INET, ai_socktype: SOCK_STREAM, ai_protocol: 0, ai_addrlen: 0, ai_canonname: nil, ai_addr: nil, ai_next: nil)
        var result: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &result) == 0, let first = result else { return nil }
        defer { freeaddrinfo(result) }
        var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        guard getnameinfo(first.pointee.ai_addr, first.pointee.ai_addrlen, &buffer, socklen_t(buffer.count), nil, 0, NI_NUMERICHOST) == 0 else { return nil }
        return String(cString: buffer)
    }

    /// An A-record query for `name`.
    static func query(_ name: String, id: UInt16 = UInt16.random(in: 1...UInt16.max)) -> Data {
        var packet = Data([UInt8(id >> 8), UInt8(id & 0xFF), 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        for label in name.split(separator: ".") {
            packet.append(UInt8(min(63, label.utf8.count)))
            packet.append(contentsOf: label.utf8.prefix(63))
        }
        packet.append(contentsOf: [0x00, 0x00, 0x01, 0x00, 0x01])
        return packet
    }

    /// The first IPv4 address in a reply, or `nil`.
    static func firstAddress(in reply: Data) -> String? {
        let b = [UInt8](reply)
        guard b.count >= 12 else { return nil }
        let questions = Int(b[4]) << 8 | Int(b[5])
        let answers = Int(b[6]) << 8 | Int(b[7])
        var i = 12
        func skipName() -> Bool {
            while i < b.count {
                let length = Int(b[i])
                if length == 0 { i += 1; return true }
                if length & 0xC0 == 0xC0 { i += 2; return true }
                i += 1 + length
            }
            return false
        }
        for _ in 0..<questions {
            guard skipName(), i + 4 <= b.count else { return nil }
            i += 4
        }
        for _ in 0..<answers {
            guard skipName(), i + 10 <= b.count else { return nil }
            let type = Int(b[i]) << 8 | Int(b[i + 1])
            let length = Int(b[i + 8]) << 8 | Int(b[i + 9])
            i += 10
            guard i + length <= b.count else { return nil }
            if type == 1 && length == 4 { return b[i..<i + 4].map(String.init).joined(separator: ".") }
            i += length
        }
        return nil
    }

    private static func lookup(_ host: String, via interface: NWInterface?) async -> String? {
        await withCheckedContinuation { (continuation: CheckedContinuation<String?, Never>) in
            let parameters = NWParameters.udp
            parameters.preferNoProxies = true
            if let interface { parameters.requiredInterface = interface }
            let connection = NWConnection(host: "1.1.1.1", port: 53, using: parameters)
            let lock = NSLock()
            var done = false
            @Sendable func finish(_ value: String?) {
                lock.lock()
                if done { lock.unlock(); return }
                done = true
                lock.unlock()
                connection.cancel()
                continuation.resume(returning: value)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    connection.send(content: query(host), completion: .contentProcessed { _ in })
                    connection.receiveMessage { data, _, _, _ in finish(data.flatMap(firstAddress(in:)).flatMap { isFake($0) ? nil : $0 }) }
                case .failed, .cancelled:
                    finish(nil)
                default:
                    break
                }
            }
            connection.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + 2.5) { finish(nil) }
        }
    }
}

/// An engine whose app died without stopping it. The app records the engine's process id when it starts one, and the
/// next launch ends that process — if it is still an engine of this app and not somebody else's by then.
public enum OrphanedEngine {
    public static var file: URL { Paths.support.appendingPathComponent("mac/engine.pid") }

    static func remember(_ pid: Int32, at url: URL = file) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? Data("\(pid)".utf8).write(to: url, options: .atomic)
    }

    static func forget(_ pid: Int32, at url: URL = file) {
        if let text = try? String(contentsOf: url, encoding: .utf8), Int32(text) == pid { try? FileManager.default.removeItem(at: url) }
    }

    /// The executable path of a process, or `nil` when there is none.
    static func path(of pid: Int32) -> String? {
        var buffer = [CChar](repeating: 0, count: 4 * Int(MAXPATHLEN))
        let length = proc_pidpath(pid, &buffer, UInt32(buffer.count))
        return length > 0 ? String(cString: buffer) : nil
    }

    /// Ends the recorded engine if it is still running and still an Xray; returns whether one was ended.
    @discardableResult
    public static func clean(at url: URL = file, isEngine: (String) -> Bool = { $0.hasSuffix("/xray") }) -> Bool {
        defer { try? FileManager.default.removeItem(at: url) }
        guard let text = try? String(contentsOf: url, encoding: .utf8), let pid = Int32(text), pid > 1,
            let executable = path(of: pid), isEngine(executable)
        else { return false }
        kill(pid, SIGTERM)
        return true
    }
}

/// A check of a live connection that says where it breaks: the same sites, once straight through the tunnel's own
/// proxy and once the way any program on this Mac asks (following the system's settings).
public enum Diagnose {
    public struct Row: Equatable, Sendable, Identifiable {
        public enum Route: Sendable { case tunnel, system }
        public var id: String { "\(route)-\(site)" }
        public var route: Route
        public var site: String
        /// Milliseconds, or `nil` with `failure` saying why not.
        public var ms: Int?
        public var failure: String?
        /// For the exit address row: what the site said about where the request came from.
        public var detail: String?
    }

    static let sites = ["cp.cloudflare.com/generate_204", "www.youtube.com/generate_204", "www.google.com/generate_204", "example.com"]

    static func session(httpPort: Int?) -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.urlCache = nil
        if let httpPort {
            configuration.connectionProxyDictionary = [
                "HTTPEnable": 1, "HTTPProxy": "127.0.0.1", "HTTPPort": httpPort,
                "HTTPSEnable": 1, "HTTPSProxy": "127.0.0.1", "HTTPSPort": httpPort,
            ]
        }
        return URLSession(configuration: configuration)
    }

    static func probe(_ session: URLSession, _ site: String, _ route: Row.Route) async -> Row {
        let started = DispatchTime.now()
        do {
            let (data, response) = try await session.data(from: URL(string: "https://\(site)")!)
            let ms = Int((DispatchTime.now().uptimeNanoseconds - started.uptimeNanoseconds) / 1_000_000)
            var detail: String?
            if site.hasSuffix("/trace"), let text = String(data: data, encoding: .utf8) {
                let fields = Dictionary(uniqueKeysWithValues: text.split(separator: "\n").compactMap { line -> (String, String)? in
                    let parts = line.split(separator: "=", maxSplits: 1).map(String.init)
                    return parts.count == 2 ? (parts[0], parts[1]) : nil
                })
                detail = [fields["ip"], fields["loc"]].compactMap { $0 }.joined(separator: " · ")
            }
            _ = response
            return Row(route: route, site: site, ms: max(1, ms), failure: nil, detail: detail)
        } catch {
            let code = (error as? URLError).map { "\($0.code.rawValue)" } ?? "?"
            return Row(route: route, site: site, ms: nil, failure: code, detail: nil)
        }
    }

    public static func run(httpPort: Int) async -> [Row] {
        let tunnel = session(httpPort: httpPort)
        let system = session(httpPort: nil)
        defer { tunnel.invalidateAndCancel(); system.invalidateAndCancel() }
        var rows: [Row] = []
        for site in sites { rows.append(await probe(tunnel, site, .tunnel)) }
        rows.append(await probe(tunnel, "www.cloudflare.com/cdn-cgi/trace", .tunnel))
        for site in sites { rows.append(await probe(system, site, .system)) }
        rows.append(await probe(system, "www.cloudflare.com/cdn-cgi/trace", .system))
        return rows
    }
}
