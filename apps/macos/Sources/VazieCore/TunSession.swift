import Darwin
import Foundation

// The whole-Mac mode. The engine runs as root with a TUN interface, started through the system's administrator prompt by a
// small supervisor script (`packaging/tun-run.sh`, carried inside this program's signed executable).
//
// What the app trusts and what it does not:
//  - The script is written by the prompt's command into a directory only root can change, from the text in this program. The
//    copy in the app bundle is never run as root.
//  - The directory also holds a pipe the user can write to (and cannot replace), and a status file everyone can read. The
//    app sends `CONFIG <nonce> <json>` and `STOP`; the script answers in the status file with the same nonce.
//  - The configuration is checked by the script against an allow-list before the engine sees it, and the engine is a copy
//    the script verified (the app's team signed it, or its SHA-256 is pinned).

/// Where the supervisor lives. The production value is the system's; the tests point it at a temporary directory.
public struct TunPaths: Sendable {
    public var run: URL

    public init(run: URL) { self.run = run }

    public static let system = TunPaths(run: URL(fileURLWithPath: "/private/var/run/app.vazie.vpn.tun", isDirectory: true))

    var status: URL { run.appendingPathComponent("status") }
    var command: URL { run.appendingPathComponent("cmd") }
    var pid: URL { run.appendingPathComponent("pid") }
    var info: URL { run.appendingPathComponent("info") }
}

/// What the supervisor says about itself, one line.
public struct TunStatus: Equatable, Sendable {
    public enum Kind: Equatable, Sendable {
        case booting, ready, starting, running
        /// The engine is gone and the kill switch keeps the Mac's traffic from leaving.
        case lostBlocked
        /// The engine is gone and the network has been put back: traffic goes out directly.
        case lostOpen
        case failed(String)
        case stopped
        case unknown
    }

    public var kind: Kind
    public var nonce: String?

    public static func parse(_ text: String) -> TunStatus {
        let words = text.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ").map(String.init)
        func nonce(_ word: String?) -> String? {
            guard let word, word.count >= 8, word.count <= 32, word.allSatisfy({ $0.isHexDigit && ($0.isNumber || $0.isLowercase) }) else { return nil }
            return word
        }
        switch words.first {
        case "booting": return TunStatus(kind: .booting, nonce: nil)
        case "ready": return TunStatus(kind: .ready, nonce: nil)
        case "starting": return TunStatus(kind: .starting, nonce: nonce(words.dropFirst().first))
        case "running": return TunStatus(kind: .running, nonce: nonce(words.dropFirst().first))
        case "stopped": return TunStatus(kind: .stopped, nonce: nil)
        case "lost":
            return TunStatus(kind: words.dropFirst().first == "blocked" ? .lostBlocked : .lostOpen, nonce: nil)
        case "failed":
            let code = words.dropFirst().first ?? "unknown"
            return TunStatus(kind: .failed(code), nonce: nonce(words.dropFirst(2).first))
        default: return TunStatus(kind: .unknown, nonce: nil)
        }
    }
}

public enum PrivilegedResult: Equatable, Sendable {
    /// The supervisor was started and left running in the background (or finished, for a foreground run, with this status).
    case started
    case finished(Int32)
    /// The person closed the prompt.
    case cancelled
    /// The prompt could not be shown, or the command was refused.
    case refused
}

/// Runs a script as root. The system's prompt is the production one; the tests run the script as the user.
public protocol PrivilegedRunner: Sendable {
    func launch(script: String, arguments: [String], wait: Bool, prompt: String) async -> PrivilegedResult
}

/// `osascript … with administrator privileges`: one prompt (Touch ID works), one shell command. The command writes the
/// script into the root-only directory and runs it from there.
public struct AdministratorPrompt: PrivilegedRunner {
    public let runDirectory: String
    /// `false` only for the tests, which cannot be root.
    let rootOwned: Bool

    public init(runDirectory: String = TunPaths.system.run.path, rootOwned: Bool = true) {
        self.runDirectory = runDirectory
        self.rootOwned = rootOwned
    }

    static func quote(_ text: String) -> String { "'" + text.replacingOccurrences(of: "'", with: "'\\''") + "'" }

    /// The shell text the prompt runs. The script travels as base64 so that no quoting of it can go wrong.
    static func bootstrap(script: String, arguments: [String], wait: Bool, runDirectory: String, rootOwned: Bool) -> String {
        let encoded = Data(script.utf8).base64EncodedString()
        let run = quote(runDirectory)
        let owner = rootOwned
            ? "[ \"$(stat -f %u \"$R\")\" = 0 ] || exit 7; chown root:wheel \"$R\"; chmod 755 \"$R\""
            : "chmod 755 \"$R\""
        let start = "/bin/sh \"$R/run.sh\" " + arguments.map(quote).joined(separator: " ")
        return [
            "set -e; umask 077",
            "R=\(run)",
            // A symlink where the directory should be is removed (its parent is root's, so only root could have put it
            // there); a directory is kept so that a dead supervisor's leftovers can still be undone.
            "if [ -L \"$R\" ]; then rm -f \"$R\"; fi",
            "[ -d \"$R\" ] || mkdir -m 755 \"$R\"",
            owner,
            "rm -f \"$R/status\"",
            "printf %s \(quote(encoded)) | /usr/bin/base64 -D > \"$R/run.tmp\"",
            "chmod 500 \"$R/run.tmp\"; mv -f \"$R/run.tmp\" \"$R/run.sh\"",
            wait ? start : "\(start) </dev/null >/dev/null 2>&1 &",
        ].joined(separator: "\n")
    }

    static func appleScript(_ shell: String, prompt: String) -> String {
        func literal(_ s: String) -> String {
            "\"" + s.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"") + "\""
        }
        return "do shell script \(literal(shell)) with prompt \(literal(prompt)) with administrator privileges"
    }

    public func launch(script: String, arguments: [String], wait: Bool, prompt: String) async -> PrivilegedResult {
        let shell = Self.bootstrap(script: script, arguments: arguments, wait: wait, runDirectory: runDirectory, rootOwned: rootOwned)
        guard let result = await ProcessRunner.execute(URL(fileURLWithPath: "/usr/bin/osascript"), ["-e", Self.appleScript(shell, prompt: prompt)], timeout: 180)
        else { return .refused }
        if result.status != 0 {
            let text = String(decoding: result.error, as: UTF8.self)
            // -128 is "User canceled."
            return text.contains("-128") || text.lowercased().contains("cancel") ? .cancelled : .refused
        }
        return wait ? .finished(0) : .started
    }
}

/// The way out the Mac has by itself, read the way the supervisor reads it: the first default route on a hardware
/// interface. A VPN's own `default` is not it.
public enum PhysicalRoute {
    public struct Route: Equatable, Sendable {
        public var interface: String
        public var gateway: String
    }

    public static func parse(_ table: String) -> Route? {
        for line in table.split(separator: "\n") {
            let words = line.split(whereSeparator: { $0 == " " || $0 == "\t" }).map(String.init)
            guard words.count >= 4, words[0] == "default" else { continue }
            let interface = words[3]
            if interface.hasPrefix("en") || interface.hasPrefix("bridge") { return Route(interface: interface, gateway: words[1]) }
        }
        return nil
    }

    public static func current() async -> Route? {
        guard let output = await ProcessRunner.run(URL(fileURLWithPath: "/usr/sbin/netstat"), ["-rn", "-f", "inet"], timeout: 5) else { return nil }
        return parse(String(decoding: output, as: UTF8.self))
    }

    /// The interface a destination is reached by, as `route get` says it; `nil` when it cannot be asked.
    public static func interface(toward destination: String) async -> String? {
        guard let output = await ProcessRunner.run(URL(fileURLWithPath: "/sbin/route"), ["-n", "get", destination], timeout: 4) else { return nil }
        for line in String(decoding: output, as: UTF8.self).split(separator: "\n") {
            let parts = line.split(separator: ":", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            if parts.count == 2, parts[0] == "interface" { return parts[1] }
        }
        return nil
    }
}

/// What a supervisor that is no longer there may have left on this Mac, read from the status files it writes.
public enum TunLeftovers {
    public struct Report: Equatable, Sendable {
        /// A supervisor process is alive (then nothing is a leftover).
        public var supervisorAlive: Bool
        /// The last thing it said was not "stopped": it did not finish its clean-up.
        public var unfinished: Bool
        /// It was holding the Mac's traffic with the kill switch when it went.
        public var wasBlocking: Bool
    }

    public static func inspect(paths: TunPaths = .system) -> Report {
        func pidAlive() -> Bool {
            guard let text = try? String(contentsOf: paths.pid, encoding: .utf8), let pid = Int32(text.trimmingCharacters(in: .whitespacesAndNewlines)), pid > 1 else { return false }
            return kill(pid, 0) == 0 || errno == EPERM
        }
        let alive = pidAlive()
        let status = TunStatus.parse((try? String(contentsOf: paths.status, encoding: .utf8)) ?? "")
        let finished = status.kind == .stopped || status.kind == .unknown
        let blocking = (TunSession.value("killswitch", in: paths.info) ?? "") != "" && (TunSession.value("killswitch", in: paths.info) != "off")
        return Report(supervisorAlive: alive, unfinished: !alive && !finished, wasBlocking: !alive && !finished && blocking)
    }

    /// The emergency way back: the same supervisor script, asked to undo what an earlier one left. One prompt.
    public static func restore(runner: PrivilegedRunner = AdministratorPrompt(), prompt: String) async -> Bool {
        switch await runner.launch(script: TunScriptSource.text, arguments: ["--recover"], wait: true, prompt: prompt) {
        case .finished(0), .started: return true
        default: return false
        }
    }
}

/// Anything a running whole-Mac tunnel can do beyond what every tunnel does: say its engine was lost while the supervisor
/// lives on, and be started again without asking for the password again.
public protocol RestartableTunnel: Tunnel {
    /// Called when the engine is gone but the supervisor is not: `blocked` says the kill switch is holding the traffic.
    func onLost(_ handler: @escaping @Sendable (_ blocked: Bool) -> Void)
    /// Starts the engine again inside the same supervisor, for the Mac's way out as it is now.
    func restart(outbound: VlessOutbound, address: String, split: SplitTunnel) async throws
    var killSwitchActive: Bool { get }
    var interfaceName: String? { get }
    /// The address the server's name was resolved to when the tunnel was opened. A restart under the block cannot look the
    /// name up again (DNS is what the block stops), so it uses this.
    var serverAddress: String? { get }
    /// The supervisor process is alive, so the engine can be started again without the password.
    var supervisorIsAlive: Bool { get }
    /// The engine is gone and the kill switch is keeping the Mac's traffic from leaving.
    var trafficIsHeld: Bool { get }
}

public final class TunSession: RestartableTunnel, @unchecked Sendable {
    public private(set) var httpPort: Int
    public private(set) var socksPort: Int
    private var apiPort: Int
    private let binary: URL
    private let paths: TunPaths
    private let killSwitch: Bool
    private let lock = NSLock()
    private var exitHandler: (@Sendable () -> Void)?
    private var lostHandler: (@Sendable (Bool) -> Void)?
    private var stopped = false
    private var lostReported = false
    private var tunName: String?
    public private(set) var serverAddress: String?
    private var watcher: Task<Void, Never>?
    fileprivate var routeProvider: @Sendable () async -> PhysicalRoute.Route? = { await PhysicalRoute.current() }

    private init(binary: URL, paths: TunPaths, killSwitch: Bool, http: Int, socks: Int, api: Int) {
        self.binary = binary
        self.paths = paths
        self.killSwitch = killSwitch
        httpPort = http
        socksPort = socks
        apiPort = api
    }

    // -- starting ------------------------------------------------------------------------------------------------------

    static func newNonce() -> String { UUID().uuidString.lowercased().replacingOccurrences(of: "-", with: "") }

    static func threePorts() throws -> (Int, Int, Int) {
        var ports = Set<Int>()
        while ports.count < 3 {
            guard let port = XraySession.freePort() else { throw TunnelStartError.bind }
            ports.insert(port)
        }
        let list = Array(ports)
        return (list[0], list[1], list[2])
    }

    /// The document the supervisor is sent, for the way out the Mac has now.
    static func document(outbound: VlessOutbound, address: String, split: SplitTunnel, ports: (Int, Int, Int), route: PhysicalRoute.Route) throws -> Data {
        let tunName = "utun\(Int.random(in: 60...89))"
        let document = XrayConfig.composeTun(
            outbound: XrayConfig.pinned(outbound, to: address), httpPort: ports.0, socksPort: ports.1, apiPort: ports.2, tunName: tunName,
            split: split, interface: route.interface)
        guard let data = try? XrayConfig.json(document), !data.contains(0x0A) else { throw TunnelStartError.config }
        return data
    }

    public static func start(
        binary: URL, outbound: VlessOutbound, address: String, split: SplitTunnel, killSwitch: Bool, prompt: String,
        paths: TunPaths = .system, runner: PrivilegedRunner = AdministratorPrompt(), team: String? = CodeIdentity.teamIdentifier(),
        pinned: [String] = Engine.pinnedBinaries, script: String = TunScriptSource.text, startupTimeout: TimeInterval = 40,
        readyTimeout: TimeInterval = 25, route routeProvider: @escaping @Sendable () async -> PhysicalRoute.Route? = { await PhysicalRoute.current() }
    ) async throws -> TunSession {
        guard FileManager.default.isExecutableFile(atPath: binary.path) else { throw TunnelStartError.engine }
        guard let route = await routeProvider() else { throw TunnelStartError.network }
        let ports = try threePorts()
        let data = try document(outbound: outbound, address: address, split: split, ports: ports, route: route)

        // The supervisor, through the system's prompt. It returns once the script is running in the background.
        let arguments = [
            String(getuid()), String(getpid()), binary.path, team ?? "-", pinned.isEmpty ? "-" : pinned.joined(separator: ","), killSwitch ? "1" : "0",
        ]
        switch await runner.launch(script: script, arguments: arguments, wait: false, prompt: prompt) {
        case .started, .finished: break
        case .cancelled: throw TunnelStartError.cancelled
        case .refused: throw TunnelStartError.privilege
        }

        let session = TunSession(binary: binary, paths: paths, killSwitch: killSwitch, http: ports.0, socks: ports.1, api: ports.2)
        session.routeProvider = routeProvider
        session.serverAddress = address
        // Ready: the engine is verified and the pipe is open. A refusal here is the engine's integrity, said as it is.
        let ready = await session.wait(timeout: readyTimeout) { $0.kind == .ready || { if case .failed = $0.kind { return true } else { return false } }($0) }
        guard let ready else { throw TunnelStartError.timeout }
        if case let .failed(code) = ready.kind { throw session.error(for: code) }

        do { try await session.send(data, timeout: startupTimeout) } catch {
            session.stop()
            throw error
        }
        session.watch()
        return session
    }

    /// Sends a document and waits for the answer that carries its nonce.
    private func send(_ data: Data, timeout: TimeInterval) async throws {
        let nonce = Self.newNonce()
        let line = Data("CONFIG \(nonce) ".utf8) + data + Data("\n".utf8)
        let written = await Task.detached { Self.write(line, to: self.paths.command, within: 8) }.value
        guard written else { throw TunnelStartError.config }
        let answer = await wait(timeout: timeout) { status in
            guard status.nonce == nonce else { return false }
            if case .running = status.kind { return true }
            if case .failed = status.kind { return true }
            return false
        }
        guard let answer else { throw TunnelStartError.timeout }
        if case let .failed(code) = answer.kind { throw error(for: code) }
        markUp()
    }

    private func markUp() {
        lock.lock()
        tunName = Self.value("tun", in: paths.info)
        lostReported = false
        lock.unlock()
    }

    private func isStopped() -> Bool {
        lock.lock(); defer { lock.unlock() }
        return stopped
    }

    private func exitHandlerIfWanted() -> (@Sendable () -> Void)? {
        lock.lock(); defer { lock.unlock() }
        return stopped ? nil : exitHandler
    }

    /// The handler to call for a loss that has not been reported yet, once.
    private func lostHandlerOnce() -> (@Sendable (Bool) -> Void)? {
        lock.lock(); defer { lock.unlock() }
        if lostReported { return nil }
        lostReported = true
        return lostHandler
    }

    func error(for code: String) -> TunnelStartError {
        switch code {
        case "config", "input": return .config
        case "engine", "parent", "busy": return .engine
        case "integrity": return .integrity
        case "network": return .network
        case "tun", "address": return .tun
        case "route": return .routes
        case "dns": return .dns
        case "killswitch": return .killSwitch
        case "traffic": return .bypassed
        default: return .engine
        }
    }

    func readStatus() -> TunStatus {
        TunStatus.parse((try? String(contentsOf: paths.status, encoding: .utf8)) ?? "")
    }

    private func wait(timeout: TimeInterval, until condition: (TunStatus) -> Bool) async -> TunStatus? {
        let deadline = Date().addingTimeInterval(timeout)
        while true {
            let status = readStatus()
            if condition(status) { return status }
            if Date() >= deadline || Task.isCancelled { return nil }
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
    }

    static func value(_ key: String, in file: URL) -> String? {
        guard let text = try? String(contentsOf: file, encoding: .utf8) else { return nil }
        for line in text.split(separator: "\n") where line.hasPrefix(key + "=") { return String(line.dropFirst(key.count + 1)) }
        return nil
    }

    /// Writes to the pipe once a reader is there; a pipe nobody reads fails to open with ENXIO.
    static func write(_ data: Data, to fifo: URL, within seconds: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(seconds)
        var fd: Int32 = -1
        while Date() < deadline {
            fd = open(fifo.path, O_WRONLY | O_NONBLOCK | O_NOFOLLOW)
            if fd >= 0 { break }
            usleep(100_000)
        }
        guard fd >= 0 else { return false }
        defer { close(fd) }
        var offset = 0
        return data.withUnsafeBytes { raw -> Bool in
            while offset < data.count {
                let n = Darwin.write(fd, raw.baseAddress!.advanced(by: offset), data.count - offset)
                if n > 0 { offset += n; continue }
                if n < 0 && (errno == EAGAIN || errno == EINTR) && Date() < deadline { usleep(5_000); continue }
                return false
            }
            return true
        }
    }

    // -- while it runs -------------------------------------------------------------------------------------------------

    /// Whether the supervisor process is alive: its number is in a file everyone can read.
    func supervisorAlive() -> Bool {
        guard let text = try? String(contentsOf: paths.pid, encoding: .utf8), let pid = Int32(text.trimmingCharacters(in: .whitespacesAndNewlines)), pid > 1
        else { return false }
        return kill(pid, 0) == 0 || errno == EPERM
    }

    private func watch() {
        watcher = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self else { return }
                if self.isStopped() { return }
                if !self.supervisorAlive() {
                    self.exitHandlerIfWanted()?()
                    return
                }
                let status = self.readStatus()
                if status.kind == .lostBlocked || status.kind == .lostOpen {
                    self.lostHandlerOnce()?(status.kind == .lostBlocked)
                }
            }
        }
    }

    public func onExit(_ handler: @escaping @Sendable () -> Void) {
        lock.lock()
        exitHandler = handler
        lock.unlock()
    }

    public func onLost(_ handler: @escaping @Sendable (Bool) -> Void) {
        lock.lock()
        lostHandler = handler
        lock.unlock()
    }

    public var killSwitchActive: Bool { killSwitch && Self.value("killswitch", in: paths.info) != nil }

    public var interfaceName: String? {
        lock.lock(); defer { lock.unlock() }
        return Self.value("iface", in: paths.info)
    }

    public var tunInterface: String? {
        lock.lock(); defer { lock.unlock() }
        return tunName
    }

    public func restart(outbound: VlessOutbound, address: String, split: SplitTunnel) async throws {
        guard supervisorAlive() else { throw TunnelStartError.privilege }
        guard let route = await routeProvider() else { throw TunnelStartError.network }
        // The ports are kept: the app's own checks and counters are pointed at them.
        let data = try Self.document(outbound: outbound, address: address, split: split, ports: (httpPort, socksPort, apiPort), route: route)
        try await send(data, timeout: 40)
    }

    public func stop() {
        lock.lock()
        stopped = true
        lock.unlock()
        watcher?.cancel()
        _ = Self.write(Data("STOP\n".utf8), to: paths.command, within: 1)
    }

    /// Stops, and returns once the supervisor says the network is back as it was (or gives up after `timeout`).
    public func shutdown() async {
        stop()
        _ = await wait(timeout: 10) { $0.kind == .stopped || !self.supervisorAlive() }
    }

    public func traffic() async -> Traffic? {
        guard let output = await ProcessRunner.run(
            binary, ["api", "statsquery", "--server=127.0.0.1:\(apiPort)", "-pattern", "outbound>>>proxy>>>traffic"], timeout: 3)
        else { return nil }
        return XraySession.parseTraffic(output)
    }

    /// The kind of thing the health check looks at, without a request: is the supervisor up, what does it say, and do the
    /// Mac's packets for the internet go to the tunnel's interface.
    public func routesIntact() async -> Bool {
        guard let name = tunInterface else { return false }
        return await PhysicalRoute.interface(toward: "1.1.1.1") == name
    }

    public func currentStatus() -> TunStatus { readStatus() }
    public var supervisorIsAlive: Bool { supervisorAlive() }
    public var trafficIsHeld: Bool {
        // `blocked` is also written when a restart under the block fails: the status then names that failure, but the block holds.
        readStatus().kind == .lostBlocked || Self.value("killswitch", in: paths.info) == "blocked"
    }
}

/// The production engine for the whole-Mac mode.
public struct TunEngine: TunnelEngine {
    private let binary: @Sendable () -> URL?
    private let prompt: @Sendable () -> String
    private let killSwitch: @Sendable () -> Bool
    private let recovery: NetworkRecovering?
    private let paths: TunPaths
    private let runner: PrivilegedRunner
    private let route: @Sendable () async -> PhysicalRoute.Route?
    private let team: String?
    private let pinned: [String]

    public init(
        binary: @escaping @Sendable () -> URL? = { Engine.binary() }, prompt: @escaping @Sendable () -> String,
        killSwitch: @escaping @Sendable () -> Bool = { false }, recovery: NetworkRecovering? = nil,
        paths: TunPaths = .system, runner: PrivilegedRunner = AdministratorPrompt(),
        route: @escaping @Sendable () async -> PhysicalRoute.Route? = { await PhysicalRoute.current() },
        team: String? = CodeIdentity.teamIdentifier(), pinned: [String] = Engine.pinnedBinaries
    ) {
        self.route = route
        self.team = team
        self.pinned = pinned
        self.binary = binary
        self.prompt = prompt
        self.killSwitch = killSwitch
        self.recovery = recovery
        self.paths = paths
        self.runner = runner
    }

    public func start(_ outbound: VlessOutbound) async throws -> Tunnel { try await start(outbound, split: .off) }

    public func start(_ outbound: VlessOutbound, split: SplitTunnel) async throws -> Tunnel {
        guard let binary = binary() else { throw TunnelStartError.engine }
        // The name is looked up before the tunnel exists, over the physical interface, and given to the engine as an address.
        guard let address = await resolve(outbound) else { throw TunnelStartError.timeout }
        // The Mac's DNS and IPv6, written down before anything changes them.
        _ = await recovery?.begin()
        return try await TunSession.start(
            binary: binary, outbound: outbound, address: address, split: split, killSwitch: killSwitch(), prompt: prompt(), paths: paths, runner: runner,
            team: team, pinned: pinned, route: route)
    }

    private func resolve(_ outbound: VlessOutbound) async -> String? {
        let route = await self.route()
        let interface = await PhysicalInterface.named(route?.interface)
        return await DNS.resolve(outbound.endpoint.host, via: interface)
    }

    public func restart(_ tunnel: Tunnel, outbound: VlessOutbound, split: SplitTunnel) async throws -> Bool {
        guard let session = tunnel as? RestartableTunnel else { return false }
        // The name is looked up again only when there is no address from before: under the kill switch DNS is exactly what
        // is blocked, and the address the block was built for is the one to go on with.
        var address = session.serverAddress
        if address == nil { address = await resolve(outbound) }
        guard let address else { throw TunnelStartError.timeout }
        try await session.restart(outbound: outbound, address: address, split: split)
        return true
    }

    public func readiness(of tunnel: Tunnel) async -> Probe.Stage? { await Probe.readiness(httpPort: tunnel.httpPort) }
    public func latency(host: String, port: Int) async -> Int? { await Probe.latency(host: host, port: port) }
    public func carriesSystemTraffic(_ tunnel: Tunnel) async -> Bool { await Probe.systemTrafficReachesTunnel(tunnel) }
}
