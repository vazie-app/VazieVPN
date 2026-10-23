import XCTest

@testable import VazieCore

/// A sandbox for the root supervisor: it is run as the ordinary user, in a temporary directory, against stand-ins for every
/// command that would change the Mac (`route`, `ifconfig`, `networksetup`, `pfctl`, `curl`, `codesign`). Nothing here touches
/// the network settings, the routing table or the firewall of the machine the tests run on.
///
/// The stand-ins keep state in files (`dns.<service>`, `v6.<service>`, `pf.rules`), so what a run leaves behind can be
/// asserted. A file `fail.<tool>` holding a regular expression makes that tool fail when its arguments match it.
final class SupervisorHarness: @unchecked Sendable {
    let dir: URL
    let tools: URL
    let run: URL
    let state: URL
    private let fm = FileManager.default
    private let lock = NSLock()
    private var processes: [Process] = []
    let engine: URL
    let engineSha: String

    init() throws {
        // Short path: the pipe's name and the sockets must stay under the system's limit.
        dir = URL(fileURLWithPath: "/private/tmp/vz-\(UUID().uuidString.prefix(8))")
        tools = dir.appendingPathComponent("tools")
        run = dir.appendingPathComponent("run")
        state = dir.appendingPathComponent("state")
        for url in [tools, run, state] { try fm.createDirectory(at: url, withIntermediateDirectories: true) }
        try fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: run.path)
        engine = dir.appendingPathComponent("xray")
        try """
            #!/bin/sh
            echo $$ >> "$VAZIE_STATE/engine.pids"
            cat > "$VAZIE_STATE/received.json"
            exec sleep 600
            """.write(to: engine, atomically: true, encoding: .utf8)
        try fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: engine.path)
        engineSha = try Engine.sha256(of: engine)
        try installTools()
    }

    func cleanUp() {
        lock.lock(); let all = processes; lock.unlock()
        for process in all where process.isRunning { process.terminate() }
        // Engines the supervisor started and a killed supervisor left (sleep 600) are ended too: each stand-in wrote its number.
        if let text = try? String(contentsOf: state.appendingPathComponent("engine.pids"), encoding: .utf8) {
            for line in text.split(separator: "\n") { if let pid = Int32(line), pid > 1 { kill(pid, SIGKILL) } }
        }
        try? fm.removeItem(at: dir)
    }

    // -- stand-ins -----------------------------------------------------------------------------------------------------

    func tool(_ name: String, _ body: String, guarded: Bool = true) {
        let prologue = guarded
            ? """
            echo "\(name) $*" >> "$VAZIE_STATE/calls.log"
            if [ -e "$VAZIE_STATE/fail.\(name)" ] && echo "$*" | grep -q -E -f "$VAZIE_STATE/fail.\(name)"; then exit 1; fi

            """
            : ""
        let url = tools.appendingPathComponent(name)
        try? ("#!/bin/sh\n" + prologue + body + "\n").write(to: url, atomically: true, encoding: .utf8)
        try? fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: url.path)
    }

    private func installTools() throws {
        tool("route", "exit 0")
        tool("ifconfig", "exit 0")
        tool("curl", "if [ -e \"$VAZIE_STATE/curl.code\" ]; then cat \"$VAZIE_STATE/curl.code\"; else echo 204; fi")
        tool("codesign", "[ -e \"$VAZIE_STATE/codesign.ok\" ]")
        tool("netstat", "cat \"$VAZIE_STATE/netstat.out\"", guarded: false)
        tool(
            "networksetup",
            """
            case "$1" in
              -listnetworkserviceorder) printf '(1) Wi-Fi\\n(Hardware Port: Wi-Fi, Device: en1)\\n\\n(2) Ethernet\\n(Hardware Port: Ethernet, Device: en0)\\n' ;;
              -getdnsservers)
                if [ -s "$VAZIE_STATE/dns.$2" ]; then tr ' ' '\\n' < "$VAZIE_STATE/dns.$2"; else echo "There aren't any DNS Servers set on $2."; fi ;;
              -setdnsservers) svc="$2"; shift 2; if [ "$1" = Empty ]; then : > "$VAZIE_STATE/dns.$svc"; else echo "$*" > "$VAZIE_STATE/dns.$svc"; fi ;;
              -getinfo) echo "IPv6: $(cat "$VAZIE_STATE/v6.$2" 2>/dev/null || echo Automatic)" ;;
              -setv6off) echo Off > "$VAZIE_STATE/v6.$2" ;;
              -setv6automatic) echo Automatic > "$VAZIE_STATE/v6.$2" ;;
            esac
            exit 0
            """)
        tool(
            "pfctl",
            """
            if [ "$1" = "-E" ]; then echo "pf enabled" >&2; echo "Token : 4242" >&2; exit 0; fi
            if [ "$1" = "-sr" ]; then echo 'anchor "com.apple/*" all'; exit 0; fi
            if [ "$1" = "-a" ]; then
              case "$3" in
                -f) cat > "$VAZIE_STATE/pf.rules" ;;
                -sr) [ -e "$VAZIE_STATE/pf.rules" ] && cat "$VAZIE_STATE/pf.rules" ;;
                -F) rm -f "$VAZIE_STATE/pf.rules" ;;
              esac
            fi
            exit 0
            """)
        write("netstat.out", "Destination        Gateway            Flags               Netif Expire\ndefault            link#21            UCSg                utun4\ndefault            192.168.31.1       UGScg                 en1\n")
        write("dns.Wi-Fi", "9.9.9.9 8.8.8.8\n")
        write("v6.Wi-Fi", "Automatic\n")
    }

    // -- state ---------------------------------------------------------------------------------------------------------

    func write(_ name: String, _ text: String) {
        try? text.write(to: state.appendingPathComponent(name), atomically: true, encoding: .utf8)
    }

    func read(_ name: String) -> String {
        ((try? String(contentsOf: state.appendingPathComponent(name), encoding: .utf8)) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func remove(_ name: String) { try? fm.removeItem(at: state.appendingPathComponent(name)) }

    func fail(_ tool: String, matching pattern: String) { write("fail.\(tool)", pattern + "\n") }

    var calls: String { read("calls.log") }
    var dns: String { read("dns.Wi-Fi") }
    var ipv6: String { read("v6.Wi-Fi") }
    var pfRules: String { read("pf.rules") }
    var status: String { ((try? String(contentsOf: run.appendingPathComponent("status"), encoding: .utf8)) ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
    func exists(_ name: String) -> Bool { fm.fileExists(atPath: run.appendingPathComponent(name).path) }

    var environment: [String: String] {
        ["VAZIE_TOOLS": tools.path, "VAZIE_RUN": run.path, "VAZIE_STATE": state.path, "HOME": NSHomeDirectory(), "PATH": "/usr/bin:/bin"]
    }

    // -- running -------------------------------------------------------------------------------------------------------

    /// Writes the script where the real prompt would, and starts it in the background as the ordinary user.
    @discardableResult
    func spawn(arguments: [String], script: String = TunScriptSource.text) throws -> Process {
        let target = run.appendingPathComponent("run.sh")
        try? fm.removeItem(at: target)
        try script.write(to: target, atomically: true, encoding: .utf8)
        try fm.setAttributes([.posixPermissions: 0o500], ofItemAtPath: target.path)
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        process.arguments = [target.path] + arguments
        process.environment = environment
        process.standardInput = FileHandle.nullDevice
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
        lock.lock(); processes.append(process); lock.unlock()
        return process
    }

    /// The arguments the app would pass.
    func arguments(parent: Int32 = getpid(), kill: Bool = false, team: String = "-", hashes: [String]? = nil) -> [String] {
        [String(getuid()), String(parent), engine.path, team, (hashes ?? [engineSha]).joined(separator: ","), kill ? "1" : "0"]
    }

    var runner: PrivilegedRunner { StandInRunner(harness: self) }

    func waitFor(_ seconds: Double = 10, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline { if condition() { return true }; Thread.sleep(forTimeInterval: 0.05) }
        return condition()
    }

    func waitFor(_ seconds: Double = 10, _ condition: () async -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline { if await condition() { return true }; try? await Task.sleep(nanoseconds: 50_000_000) }
        return await condition()
    }

    private var routeValue = PhysicalRoute.Route(interface: "en1", gateway: "192.168.31.1")

    var route: @Sendable () async -> PhysicalRoute.Route? {
        { [self] in
            lock.lock(); defer { lock.unlock() }
            return routeValue
        }
    }

    /// The Mac changes networks: what the routing table says, and what the app's own reading of it says.
    func moveTo(interface: String, gateway: String) {
        lock.lock(); routeValue = PhysicalRoute.Route(interface: interface, gateway: gateway); lock.unlock()
        write("netstat.out", "default            link#21            UCSg                utun4\ndefault            \(gateway)       UGScg                 \(interface)\n")
    }

    func outbound(_ link: String = "vless://00000000-0000-4000-8000-000000000001@relay.example.net:443?security=tls&sni=relay.example.net") -> VlessOutbound {
        guard case let .recognized(draft) = Parsers.parse(link) else { fatalError(link) }
        return draft.outbound
    }

    /// A session started through the real protocol against the stand-ins.
    func session(
        kill: Bool = false, split: SplitTunnel = .off, team: String? = nil, hashes: [String]? = nil,
        link: String? = nil
    ) async throws -> TunSession {
        try await TunSession.start(
            binary: engine, outbound: link.map { outbound($0) } ?? outbound(), address: "203.0.113.5", split: split, killSwitch: kill, prompt: "test",
            paths: TunPaths(run: run), runner: runner, team: team, pinned: hashes ?? [engineSha], startupTimeout: 20, readyTimeout: 10, route: route)
    }

    /// The pid the supervisor recorded for its engine.
    var enginePid: Int32? {
        (try? String(contentsOf: run.appendingPathComponent("xray.pid"), encoding: .utf8)).flatMap { Int32($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
    }
}

/// Stands in for the administrator prompt: the same script, the same arguments, no password, no root.
final class StandInRunner: PrivilegedRunner, @unchecked Sendable {
    let harness: SupervisorHarness
    init(harness: SupervisorHarness) { self.harness = harness }

    func launch(script: String, arguments: [String], wait: Bool, prompt: String) async -> PrivilegedResult {
        do { try harness.spawn(arguments: arguments, script: script) } catch { return .refused }
        return .started
    }
}
