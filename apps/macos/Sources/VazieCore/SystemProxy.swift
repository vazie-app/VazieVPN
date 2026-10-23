import CryptoKit
import Darwin
import CFNetwork
import Foundation
import Network

/// How the tunnel reaches this Mac's apps: through the system's own proxy settings. A browser, and any program that
/// follows the network preferences, then goes through the tunnel; what the settings say is put back exactly as it
/// was when the tunnel ends — and, if the app dies first, the next launch puts it back.
public enum ProxyPlan: Equatable, Sendable {
    /// Plain HTTP(S) and SOCKS proxies, with the local networks exempt.
    case direct(httpPort: Int, socksPort: Int)
    /// A proxy auto-config script served on the loopback address (a split tunnel).
    case script(url: String)
}

public protocol SystemProxyControlling: Sendable {
    func apply(_ plan: ProxyPlan) async throws
    /// Puts the settings back; harmless when nothing was changed.
    func restore() async
    /// Whether a previous run left the settings changed.
    func needsRecovery() -> Bool
    /// Whether the settings the Mac's programs actually see are the plan's. Another VPN or proxy app that sets its own
    /// proxy outranks the per-service settings this app writes, and then nothing goes through the tunnel.
    func isInEffect(_ plan: ProxyPlan) -> Bool
}

extension SystemProxyControlling {
    public func isInEffect(_ plan: ProxyPlan) -> Bool { true }
}

/// What a program that follows the system proxy is told, as the system reports it right now.
public struct EffectiveProxy: Equatable, Sendable {
    public var https: (enabled: Bool, host: String, port: Int)
    public var script: (enabled: Bool, url: String)

    public static func read() -> EffectiveProxy {
        let raw = (CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any]) ?? [:]
        func int(_ key: String) -> Int { (raw[key] as? NSNumber)?.intValue ?? 0 }
        return EffectiveProxy(
            https: (int("HTTPSEnable") == 1, raw["HTTPSProxy"] as? String ?? "", int("HTTPSPort")),
            script: (int("ProxyAutoConfigEnable") == 1, raw["ProxyAutoConfigURLString"] as? String ?? ""))
    }

    public func matches(_ plan: ProxyPlan) -> Bool {
        switch plan {
        case let .direct(http, _): return https.enabled && https.host == "127.0.0.1" && https.port == http
        case let .script(url): return script.enabled && script.url.hasPrefix(url)
        }
    }

    public static func == (a: EffectiveProxy, b: EffectiveProxy) -> Bool {
        a.https == b.https && a.script == b.script
    }
}

public enum SystemProxyError: Error, Equatable { case noServices, refused }

/// `networksetup`, which is how the Network preferences are scripted. Injected so the tests can read its answers.
public protocol NetworkSetupRunning: Sendable {
    func run(_ arguments: [String]) async -> ProcessRunner.Result?
}

public struct NetworkSetup: NetworkSetupRunning {
    public init() {}
    public func run(_ arguments: [String]) async -> ProcessRunner.Result? {
        await ProcessRunner.execute(URL(fileURLWithPath: "/usr/sbin/networksetup"), arguments, timeout: 15)
    }
}

public actor SystemProxy: SystemProxyControlling {
    struct Endpoint: Codable, Equatable {
        var enabled: Bool
        var server: String
        var port: Int
    }

    struct Snapshot: Codable, Equatable {
        var service: String
        var web: Endpoint
        var secure: Endpoint
        var socks: Endpoint
        var scriptEnabled: Bool
        var scriptURL: String
        /// `nil` when no list was set.
        var bypass: [String]?
    }

    /// Addresses that stay off the tunnel under a plain proxy: the Mac's own networks, which the server cannot reach.
    static let bypass = [
        "127.0.0.1", "localhost", "::1", "*.local", "*.localhost", "*.lan", "*.home.arpa", "*.internal",
        "169.254.0.0/16", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10",
    ]

    private let runner: NetworkSetupRunning
    private let snapshotURL: URL
    private let only: Set<String>?
    private let keys: VaultKeyStore
    private var scriptVersion = 0

    /// `only` narrows the services that are touched; the tests use it to work on one that carries no traffic.
    public init(
        runner: NetworkSetupRunning = NetworkSetup(), snapshotURL: URL = Paths.proxySnapshot, only: Set<String>? = nil,
        keys: VaultKeyStore = KeychainKeyStore()
    ) {
        self.runner = runner
        self.snapshotURL = snapshotURL
        self.only = only
        self.keys = keys
    }

    nonisolated public func needsRecovery() -> Bool { FileManager.default.fileExists(atPath: snapshotURL.path) }

    nonisolated public func isInEffect(_ plan: ProxyPlan) -> Bool { EffectiveProxy.read().matches(plan) }

    private func text(_ arguments: [String]) async -> String? {
        guard let result = await runner.run(arguments), result.status == 0 else { return nil }
        return String(decoding: result.output, as: UTF8.self)
    }

    private func succeeds(_ arguments: [String]) async -> Bool {
        guard let result = await runner.run(arguments) else { return false }
        return result.status == 0
    }

    // -- reading -------------------------------------------------------------------------------------

    /// The hardware services (Wi-Fi, Ethernet, a tethered phone) that are switched on. A VPN's own service has no
    /// `enN` device, and is left alone.
    func services() async -> [String] {
        guard let order = await text(["-listnetworkserviceorder"]) else { return [] }
        let all = Self.parseServices(order)
        return only.map { allowed in all.filter(allowed.contains) } ?? all
    }

    private static let serviceLine = try! NSRegularExpression(pattern: #"^\((\d+|\*)\) (.+)$"#)
    private static let deviceLine = try! NSRegularExpression(pattern: #"^\(Hardware Port: .*, Device: ([^)]*)\)$"#)

    static func parseServices(_ order: String) -> [String] {
        var out: [String] = []
        var pending: String?
        for rawLine in order.split(separator: "\n", omittingEmptySubsequences: true) {
            let line = String(rawLine).trimmingCharacters(in: .whitespaces)
            let whole = NSRange(line.startIndex..., in: line)
            if let match = serviceLine.firstMatch(in: line, range: whole), let mark = Range(match.range(at: 1), in: line),
                let name = Range(match.range(at: 2), in: line)
            {
                // A disabled service is listed as "(*) Name"; it is not ours to change.
                pending = line[mark] == "*" ? nil : String(line[name])
            } else if let match = deviceLine.firstMatch(in: line, range: whole), let device = Range(match.range(at: 1), in: line),
                let service = pending
            {
                let name = line[device]
                if name.hasPrefix("en") || name.hasPrefix("bridge") { out.append(service) }
                pending = nil
            }
        }
        return out
    }

    static func parseEndpoint(_ output: String) -> Endpoint {
        var enabled = false
        var server = ""
        var port = 0
        for line in output.split(separator: "\n") {
            let parts = line.split(separator: ":", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            guard parts.count == 2 else { continue }
            switch parts[0] {
            case "Enabled": enabled = parts[1].lowercased() == "yes"
            case "Server": server = parts[1] == "(null)" ? "" : parts[1]
            case "Port": port = Int(parts[1]) ?? 0
            default: break
            }
        }
        return Endpoint(enabled: enabled, server: server, port: port)
    }

    static func parseScript(_ output: String) -> (enabled: Bool, url: String) {
        var enabled = false
        var url = ""
        for line in output.split(separator: "\n") {
            if line.hasPrefix("Enabled:") { enabled = line.dropFirst("Enabled:".count).trimmingCharacters(in: .whitespaces).lowercased() == "yes" }
            if line.hasPrefix("URL:") {
                let value = line.dropFirst("URL:".count).trimmingCharacters(in: .whitespaces)
                url = value == "(null)" ? "" : value
            }
        }
        return (enabled, url)
    }

    static func parseBypass(_ output: String) -> [String]? {
        if output.contains("aren't any") || output.contains("There aren") { return nil }
        let list = output.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return list.isEmpty ? nil : list
    }

    private func snapshot(of service: String) async -> Snapshot? {
        guard let web = await text(["-getwebproxy", service]), let secure = await text(["-getsecurewebproxy", service]),
            let socks = await text(["-getsocksfirewallproxy", service]), let script = await text(["-getautoproxyurl", service])
        else { return nil }
        let bypass = await text(["-getproxybypassdomains", service]).flatMap(Self.parseBypass)
        let parsedScript = Self.parseScript(script)
        return Snapshot(
            service: service, web: Self.parseEndpoint(web), secure: Self.parseEndpoint(secure), socks: Self.parseEndpoint(socks),
            scriptEnabled: parsedScript.enabled, scriptURL: parsedScript.url, bypass: bypass)
    }

    // -- writing -------------------------------------------------------------------------------------

    /// What the settings said before this app touched them, authenticated like the network record: a program that
    /// rewrites the file cannot make the next launch put a proxy of its choosing on the Mac's services.
    private struct Envelope: Codable {
        var body: Data
        var mac: Data
    }

    private func readSnapshots() -> [Snapshot]? {
        guard FileManager.default.fileExists(atPath: snapshotURL.path) else { return nil }
        var info = stat()
        guard lstat(snapshotURL.path, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG, info.st_uid == getuid(), (info.st_mode & 0o022) == 0,
            let data = try? Data(contentsOf: snapshotURL), data.count < 1 << 20,
            let envelope = try? JSONDecoder().decode(Envelope.self, from: data),
            let key = try? keys.key(),
            HMAC<SHA256>.isValidAuthenticationCode(envelope.mac, authenticating: envelope.body, using: key),
            let snapshots = try? JSONDecoder().decode([Snapshot].self, from: envelope.body)
        else {
            // Neither trusted nor lost: moved aside, and the settings are then taken as they are.
            let bad = snapshotURL.appendingPathExtension("bad")
            try? FileManager.default.removeItem(at: bad)
            try? FileManager.default.moveItem(at: snapshotURL, to: bad)
            return nil
        }
        return snapshots
    }

    private func writeSnapshots(_ snapshots: [Snapshot]) throws {
        let directory = snapshotURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let key = try keys.key()
        let body = try JSONEncoder().encode(snapshots)
        let mac = Data(HMAC<SHA256>.authenticationCode(for: body, using: key))
        let data = try JSONEncoder().encode(Envelope(body: body, mac: mac))
        let temporary = directory.appendingPathComponent(".proxy-\(UUID().uuidString).tmp")
        let fd = open(temporary.path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0o600)
        guard fd >= 0 else { throw SystemProxyError.refused }
        var written = 0
        let ok = data.withUnsafeBytes { raw -> Bool in
            while written < data.count {
                let n = Darwin.write(fd, raw.baseAddress!.advanced(by: written), data.count - written)
                if n <= 0 { return false }
                written += n
            }
            return true
        }
        close(fd)
        guard ok, rename(temporary.path, snapshotURL.path) == 0 else {
            try? FileManager.default.removeItem(at: temporary)
            throw SystemProxyError.refused
        }
    }

    public func apply(_ plan: ProxyPlan) async throws {
        // What the settings said before this app touched them, kept across a second `apply` (a split changed while
        // connected) and across a crash.
        var snapshots = readSnapshots() ?? []
        let names = await services()
        guard !names.isEmpty else { throw SystemProxyError.noServices }
        for name in names where !snapshots.contains(where: { $0.service == name }) {
            guard let taken = await snapshot(of: name) else { throw SystemProxyError.refused }
            snapshots.append(taken)
        }
        try writeSnapshots(snapshots)

        for name in names {
            switch plan {
            case let .direct(http, socks):
                let port = String(http)
                let ok = await all([
                    ["-setautoproxystate", name, "off"],
                    ["-setwebproxy", name, "127.0.0.1", port],
                    ["-setsecurewebproxy", name, "127.0.0.1", port],
                    ["-setsocksfirewallproxy", name, "127.0.0.1", String(socks)],
                    ["-setproxybypassdomains", name] + Self.bypass,
                ])
                guard ok else { await restore(); throw SystemProxyError.refused }
            case let .script(url):
                scriptVersion += 1
                let ok = await all([
                    ["-setwebproxystate", name, "off"],
                    ["-setsecurewebproxystate", name, "off"],
                    ["-setsocksfirewallproxystate", name, "off"],
                    ["-setautoproxyurl", name, "\(url)?v=\(scriptVersion)"],
                    ["-setautoproxystate", name, "on"],
                ])
                guard ok else { await restore(); throw SystemProxyError.refused }
            }
        }
    }

    /// Runs the commands in order, and stops at the first that fails.
    private func all(_ commands: [[String]]) async -> Bool {
        for command in commands where !(await succeeds(command)) { return false }
        return true
    }

    public func restore() async {
        guard let snapshots = readSnapshots() else { return }
        var complete = true
        for s in snapshots {
            func put(_ set: String, _ state: String, _ e: Endpoint) async -> Bool {
                // An empty server is how "nothing was set" is put back: `networksetup` takes "" and 0 for it.
                let ok = await succeeds([set, s.service, e.server, String(e.port)])
                return await succeeds([state, s.service, e.enabled ? "on" : "off"]) && ok
            }
            var ok = await put("-setwebproxy", "-setwebproxystate", s.web)
            ok = await put("-setsecurewebproxy", "-setsecurewebproxystate", s.secure) && ok
            ok = await put("-setsocksfirewallproxy", "-setsocksfirewallproxystate", s.socks) && ok
            if !s.scriptURL.isEmpty { ok = await succeeds(["-setautoproxyurl", s.service, s.scriptURL]) && ok }
            ok = await succeeds(["-setautoproxystate", s.service, s.scriptEnabled ? "on" : "off"]) && ok
            ok = await succeeds(["-setproxybypassdomains", s.service] + (s.bypass ?? ["Empty"])) && ok
            if !ok { complete = false }
        }
        // Only a restore that went through lets the record go; otherwise the next launch tries again.
        if complete { try? FileManager.default.removeItem(at: snapshotURL) }
    }
}

/// Serves the proxy auto-config script on the loopback address, and nowhere else.
public final class PACServer: @unchecked Sendable {
    private var listener: NWListener?
    private let lock = NSLock()
    private var script = ""
    public private(set) var port: Int = 0

    public init() {}

    public func update(_ script: String) {
        lock.lock()
        self.script = script
        lock.unlock()
    }

    public func start() async throws {
        let parameters = NWParameters.tcp
        parameters.requiredInterfaceType = .loopback
        let listener = try NWListener(using: parameters, on: .any)
        self.listener = listener
        listener.newConnectionHandler = { [weak self] connection in self?.serve(connection) }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let once = Once()
            listener.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if once.first() { continuation.resume() }
                case let .failed(error):
                    if once.first() { continuation.resume(throwing: error) }
                default:
                    break
                }
            }
            listener.start(queue: .global())
        }
        port = Int(listener.port?.rawValue ?? 0)
    }

    public var url: String { "http://127.0.0.1:\(port)/proxy.pac" }

    public func stop() {
        listener?.cancel()
        listener = nil
    }

    private func serve(_ connection: NWConnection) {
        // Only the loopback address may ask.
        if case let .hostPort(host, _) = connection.endpoint, !"\(host)".contains("127.0.0.1"), !"\(host)".contains("::1") {
            connection.cancel()
            return
        }
        connection.start(queue: .global())
        connection.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] _, _, _, _ in
            guard let self else { connection.cancel(); return }
            self.lock.lock()
            let body = Data(self.script.utf8)
            self.lock.unlock()
            let head = "HTTP/1.1 200 OK\r\nContent-Type: application/x-ns-proxy-autoconfig\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
            connection.send(content: Data(head.utf8) + body, completion: .contentProcessed { _ in connection.cancel() })
        }
    }

    private final class Once: @unchecked Sendable {
        private let lock = NSLock()
        private var done = false
        func first() -> Bool {
            lock.lock(); defer { lock.unlock() }
            if done { return false }
            done = true
            return true
        }
    }
}

/// The VPN apps that are connected on this Mac right now, as the system lists them. Any of them may hold the system
/// proxy or the DNS, so the one that is named is the likeliest reason a tunnel nothing uses.
public enum OtherVPN {
    static func parse(_ listing: String) -> [String] {
        listing.split(separator: "\n").compactMap { line in
            guard line.contains("(Connected)"), let open = line.firstIndex(of: "\""), let close = line[line.index(after: open)...].firstIndex(of: "\"") else { return nil }
            return String(line[line.index(after: open)..<close])
        }
    }

    public static func connected() async -> [String] {
        guard let output = await ProcessRunner.run(URL(fileURLWithPath: "/usr/sbin/scutil"), ["--nc", "list"], timeout: 5) else { return [] }
        return parse(String(decoding: output, as: UTF8.self))
    }
}
