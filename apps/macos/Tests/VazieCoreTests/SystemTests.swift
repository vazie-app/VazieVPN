import XCTest

@testable import VazieCore

/// `networksetup`, spoken to through a script of canned answers.
final class ScriptedNetworkSetup: NetworkSetupRunning, @unchecked Sendable {
    var answers: [String: String] = [:]
    var calls: [[String]] = []
    var failing: Set<String> = []

    func run(_ arguments: [String]) async -> ProcessRunner.Result? {
        calls.append(arguments)
        if failing.contains(arguments[0]) { return ProcessRunner.Result(status: 1, output: Data(), error: Data()) }
        let key = arguments.joined(separator: "|")
        return ProcessRunner.Result(status: 0, output: Data((answers[key] ?? answers[arguments[0]] ?? "").utf8), error: Data())
    }
}

final class SystemProxyTests: XCTestCase {
    private let order = """
        An asterisk (*) denotes that a network service is disabled.
        (1) Ethernet
        (Hardware Port: Ethernet, Device: en5)

        (2) Wi-Fi
        (Hardware Port: Wi-Fi, Device: en0)

        (3) Tailscale
        (Hardware Port: com.wireguard.macos.tunnel, Device: )

        (*) iPhone USB
        (Hardware Port: iPhone USB, Device: en6)

        (5) Thunderbolt Bridge
        (Hardware Port: Thunderbolt Bridge, Device: bridge0)
        """

    func testOnlyTheEnabledHardwareServicesAreChanged() {
        XCTAssertEqual(SystemProxy.parseServices(order), ["Ethernet", "Wi-Fi", "Thunderbolt Bridge"])
    }

    func testTheCurrentSettingsAreReadFromNetworksetupsWords() {
        let web = SystemProxy.parseEndpoint("Enabled: Yes\nServer: 127.0.0.1\nPort: 1082\nAuthenticated Proxy Enabled: 0\n")
        XCTAssertEqual(web, SystemProxy.Endpoint(enabled: true, server: "127.0.0.1", port: 1082))
        XCTAssertEqual(SystemProxy.parseEndpoint("Enabled: No\nServer: \nPort: 0\n").enabled, false)
        let script = SystemProxy.parseScript("URL: (null)\nEnabled: No\n")
        XCTAssertEqual(script.enabled, false)
        XCTAssertEqual(script.url, "")
        XCTAssertNil(SystemProxy.parseBypass("There aren't any bypass domains set on Wi-Fi.\n"))
        XCTAssertEqual(SystemProxy.parseBypass("*.local\n169.254/16\n"), ["*.local", "169.254/16"])
    }

    private func proxy(_ runner: ScriptedNetworkSetup, _ directory: URL) -> SystemProxy {
        runner.answers["-listnetworkserviceorder"] = "(1) Wi-Fi\n(Hardware Port: Wi-Fi, Device: en0)\n"
        runner.answers["-getwebproxy"] = "Enabled: Yes\nServer: 10.1.1.1\nPort: 3128\n"
        runner.answers["-getsecurewebproxy"] = "Enabled: No\nServer: \nPort: 0\n"
        runner.answers["-getsocksfirewallproxy"] = "Enabled: No\nServer: \nPort: 0\n"
        runner.answers["-getautoproxyurl"] = "URL: http://pac.corp/x.pac\nEnabled: Yes\n"
        runner.answers["-getproxybypassdomains"] = "*.corp\n"
        return SystemProxy(runner: runner, snapshotURL: directory.appendingPathComponent("system-proxy.json"), keys: MemoryKeyStore())
    }

    func testTheSettingsAreRememberedAndPutBackExactly() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let runner = ScriptedNetworkSetup()
        let system = proxy(runner, directory)

        XCTAssertFalse(system.needsRecovery())
        try await system.apply(.direct(httpPort: 1080, socksPort: 1081))
        XCTAssertTrue(system.needsRecovery(), "the record is kept while the settings are changed")
        XCTAssertTrue(runner.calls.contains(["-setwebproxy", "Wi-Fi", "127.0.0.1", "1080"]))
        XCTAssertTrue(runner.calls.contains(["-setsocksfirewallproxy", "Wi-Fi", "127.0.0.1", "1081"]))
        XCTAssertTrue(runner.calls.contains(["-setautoproxystate", "Wi-Fi", "off"]))

        runner.calls = []
        await system.restore()
        XCTAssertFalse(system.needsRecovery())
        XCTAssertTrue(runner.calls.contains(["-setwebproxy", "Wi-Fi", "10.1.1.1", "3128"]))
        XCTAssertTrue(runner.calls.contains(["-setwebproxystate", "Wi-Fi", "on"]))
        XCTAssertTrue(runner.calls.contains(["-setsecurewebproxystate", "Wi-Fi", "off"]))
        XCTAssertTrue(runner.calls.contains(["-setautoproxyurl", "Wi-Fi", "http://pac.corp/x.pac"]))
        XCTAssertTrue(runner.calls.contains(["-setautoproxystate", "Wi-Fi", "on"]))
        XCTAssertTrue(runner.calls.contains(["-setproxybypassdomains", "Wi-Fi", "*.corp"]))
    }

    func testARecordThatWasRewrittenIsNotPutBack() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let runner = ScriptedNetworkSetup()
        let system = proxy(runner, directory)
        try await system.apply(.direct(httpPort: 1080, socksPort: 1081))

        // A program that replaces the record with one of its own keeps the file's shape but cannot make the MAC.
        let file = directory.appendingPathComponent("system-proxy.json")
        var document = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        let body = try XCTUnwrap(document["body"] as? String)
        let forged = String(decoding: try XCTUnwrap(Data(base64Encoded: body)), as: UTF8.self).replacingOccurrences(of: "10.1.1.1", with: "6.6.6.6")
        document["body"] = Data(forged.utf8).base64EncodedString()
        try JSONSerialization.data(withJSONObject: document).write(to: file)

        runner.calls = []
        await system.restore()
        XCTAssertFalse(runner.calls.contains { $0.contains("6.6.6.6") }, "a forged record must never reach networksetup")
        XCTAssertTrue(FileManager.default.fileExists(atPath: file.appendingPathExtension("bad").path), "it is kept aside, not lost")
    }

    func testTheRecordIsReadableOnlyByItsOwner() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let system = proxy(ScriptedNetworkSetup(), directory)
        try await system.apply(.direct(httpPort: 1080, socksPort: 1081))
        let attributes = try FileManager.default.attributesOfItem(atPath: directory.appendingPathComponent("system-proxy.json").path)
        XCTAssertEqual((attributes[.posixPermissions] as? Int) ?? 0, 0o600)
    }

    func testChangingTheModeWhileConnectedKeepsTheFirstRecord() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let runner = ScriptedNetworkSetup()
        let system = proxy(runner, directory)
        try await system.apply(.direct(httpPort: 1080, socksPort: 1081))
        // What the settings say now is ours; a second apply must not record it as the person's own.
        runner.answers["-getwebproxy"] = "Enabled: Yes\nServer: 127.0.0.1\nPort: 1080\n"
        try await system.apply(.script(url: "http://127.0.0.1:2000/proxy.pac"))
        runner.calls = []
        await system.restore()
        XCTAssertTrue(runner.calls.contains(["-setwebproxy", "Wi-Fi", "10.1.1.1", "3128"]))
    }

    func testARefusedChangeGivesBackWhatWasChanged() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let runner = ScriptedNetworkSetup()
        let system = proxy(runner, directory)
        runner.failing = ["-setsecurewebproxy"]
        do {
            try await system.apply(.direct(httpPort: 1080, socksPort: 1081))
            XCTFail("should have been refused")
        } catch {
            XCTAssertEqual(error as? SystemProxyError, .refused)
        }
        XCTAssertTrue(runner.calls.contains(["-setwebproxy", "Wi-Fi", "10.1.1.1", "3128"]), "the first setting was put back")
    }

    func testNoServicesIsAnError() async {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let runner = ScriptedNetworkSetup()
        let system = SystemProxy(runner: runner, snapshotURL: directory.appendingPathComponent("s.json"), keys: MemoryKeyStore())
        do {
            try await system.apply(.direct(httpPort: 1, socksPort: 2))
            XCTFail()
        } catch {
            XCTAssertEqual(error as? SystemProxyError, .noServices)
        }
    }
}

final class EngineTests: XCTestCase {
    func testEveryPinnedChecksumIsASha256() {
        for asset in Engine.assets {
            XCTAssertEqual(asset.sha256.count, 64, asset.file)
            XCTAssertTrue(asset.sha256.allSatisfy(\.isHexDigit), asset.file)
        }
    }

    func testTrafficIsReadFromBothCounters() {
        let text = #"{"stat":[{"name":"outbound>>>proxy>>>traffic>>>uplink","value":"10"},{"name":"outbound>>>proxy>>>traffic>>>downlink","value":"2048"}]}"#
        XCTAssertEqual(XraySession.parseTraffic(Data(text.utf8)), Traffic(rx: 2048, tx: 10))
        XCTAssertEqual(XraySession.parseTraffic(Data("{}".utf8)), Traffic(rx: 0, tx: 0))
        XCTAssertNil(XraySession.parseTraffic(Data("nonsense".utf8)))
    }

    func testAnEngineWhoseChecksumIsWrongIsRefusedAndLeftNothingBehind() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("served.zip")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("not the engine".utf8).write(to: file)
        do {
            try await Engine.install(into: directory.appendingPathComponent("support"), from: file)
            XCTFail("a download that does not match the pin must not install")
        } catch {
            XCTAssertEqual(error as? EngineInstallFailure, .checksum)
        }
        XCTAssertNil(Engine.binary(support: directory.appendingPathComponent("support"), bundled: nil))
    }

    func testTheRealEngineRunsAndReportsItsCounters() async throws {
        guard let binary = Engine.binary() else { throw XCTSkip("the Xray engine is not installed on this Mac") }
        let draft = Parsers.parse("vless://00000000-0000-4000-8000-000000000001@127.0.0.1:9?security=tls&sni=example.com")
        guard case let .recognized(parsed) = draft else { return XCTFail() }
        let session = try await XraySession.start(binary: binary, outbound: parsed.outbound)
        defer { session.stop() }
        XCTAssertTrue(XraySession.accepts(port: session.httpPort))
        XCTAssertTrue(XraySession.accepts(port: session.socksPort))
        let traffic = await session.traffic()
        XCTAssertEqual(traffic, Traffic(rx: 0, tx: 0))
        let exited = expectation(description: "engine ends")
        session.onExit { exited.fulfill() }
        session.stop()
        // A stop the app asked for is not an exit to report.
        let result = await XCTWaiter.fulfillment(of: [exited], timeout: 1)
        XCTAssertEqual(result, .timedOut)
        try await Task.sleep(nanoseconds: 300_000_000)
        XCTAssertFalse(XraySession.accepts(port: session.httpPort))
    }

    func testTheRealEngineThatDiesOnItsOwnIsReported() async throws {
        guard let binary = Engine.binary() else { throw XCTSkip("the Xray engine is not installed on this Mac") }
        guard case let .recognized(parsed) = Parsers.parse("vless://00000000-0000-4000-8000-000000000001@127.0.0.1:9?security=tls&sni=example.com") else { return XCTFail() }
        let session = try await XraySession.start(binary: binary, outbound: parsed.outbound)
        let exited = expectation(description: "engine ends")
        session.onExit { exited.fulfill() }
        // From outside, as a crash would be.
        let pids = (try? await ProcessRunner.run(URL(fileURLWithPath: "/usr/bin/pgrep"), ["-f", "xray run -config stdin:"], timeout: 3)).flatMap { $0 }
        let mine = String(decoding: pids ?? Data(), as: UTF8.self).split(separator: "\n").compactMap { Int32($0) }
        XCTAssertFalse(mine.isEmpty)
        // Only the engine this test started: the newest.
        if let last = mine.max() { kill(last, SIGKILL) }
        await fulfillment(of: [exited], timeout: 3)
        session.stop()
    }
}

/// Against the real `networksetup`. Reading is always safe; the one that writes runs only when asked for, on a
/// service the person names, because it changes real settings (and puts them back).
final class RealNetworkSetupTests: XCTestCase {
    func testTheRealServiceListAndSettingsAreReadable() async throws {
        guard let listing = await NetworkSetup().run(["-listnetworkserviceorder"]), listing.status == 0 else {
            throw XCTSkip("networksetup is not available")
        }
        let services = SystemProxy.parseServices(String(decoding: listing.output, as: UTF8.self))
        XCTAssertFalse(services.isEmpty, "a Mac has a hardware network service")
        for service in services {
            let reply = await NetworkSetup().run(["-getwebproxy", service])
            let endpoint = SystemProxy.parseEndpoint(String(decoding: reply?.output ?? Data(), as: UTF8.self))
            XCTAssertTrue(endpoint.port >= 0, service)
        }
    }

    func testApplyingAndRestoringOnARealServiceLeavesItAsItWas() async throws {
        guard let name = ProcessInfo.processInfo.environment["VAZIE_E2E_PROXY_SERVICE"] else {
            throw XCTSkip("set VAZIE_E2E_PROXY_SERVICE to a service that carries no traffic to run this")
        }
        let setup = NetworkSetup()
        func read() async -> [String] {
            var out: [String] = []
            for flag in ["-getwebproxy", "-getsecurewebproxy", "-getsocksfirewallproxy", "-getautoproxyurl", "-getproxybypassdomains"] {
                out.append(String(decoding: (await setup.run([flag, name]))?.output ?? Data(), as: UTF8.self))
            }
            return out
        }
        let before = await read()
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let system = SystemProxy(snapshotURL: directory.appendingPathComponent("s.json"), only: [name], keys: MemoryKeyStore())

        try await system.apply(.direct(httpPort: 1080, socksPort: 1081))
        let during = await read()
        XCTAssertTrue(during[0].contains("Enabled: Yes") && during[0].contains("Port: 1080"), during[0])
        XCTAssertTrue(during[2].contains("Port: 1081"), during[2])
        XCTAssertTrue(during[4].contains("169.254.0.0/16"), during[4])

        // What the programs see: a Mac with another VPN's proxy active answers with that one, not ours.
        let effective = EffectiveProxy.read()
        let seen = system.isInEffect(.direct(httpPort: 1080, socksPort: 1081))
        print("EFFECTIVE https=\(effective.https) script=\(effective.script) ours-in-effect=\(seen)")

        try await system.apply(.script(url: "http://127.0.0.1:2000/proxy.pac"))
        let script = await read()
        XCTAssertTrue(script[0].contains("Enabled: No"), script[0])
        XCTAssertTrue(script[3].contains("http://127.0.0.1:2000/proxy.pac"), script[3])
        XCTAssertTrue(script[3].contains("Enabled: Yes"), script[3])

        await system.restore()
        let after = await read()
        // The enabled/disabled state and the proxy servers are exactly as they were.
        for index in [0, 1, 2, 4] { XCTAssertEqual(before[index], after[index], "setting \(index)") }
        XCTAssertTrue(after[3].contains("Enabled: No"), after[3])
        XCTAssertFalse(system.needsRecovery())
    }
}

final class PACServerTests: XCTestCase {
    func testTheScriptIsServedOnTheLoopbackAddressAndUpdatesAtOnce() async throws {
        let server = PACServer()
        server.update(SplitTunnel(mode: .exclude, sites: ["a.example"]).pacScript(httpPort: 4242))
        try await server.start()
        defer { server.stop() }
        XCTAssertTrue(server.url.hasPrefix("http://127.0.0.1:"))
        XCTAssertGreaterThan(server.port, 0)

        let configuration = URLSessionConfiguration.ephemeral
        configuration.connectionProxyDictionary = [:]
        let session = URLSession(configuration: configuration)
        let (data, response) = try await session.data(from: URL(string: server.url)!)
        XCTAssertEqual((response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Type"), "application/x-ns-proxy-autoconfig")
        XCTAssertTrue(String(decoding: data, as: UTF8.self).contains("a.example"))

        server.update(SplitTunnel(mode: .include, sites: ["b.example"]).pacScript(httpPort: 4242))
        let (again, _) = try await session.data(from: URL(string: server.url + "?v=2")!)
        XCTAssertTrue(String(decoding: again, as: UTF8.self).contains("b.example"))
        XCTAssertFalse(String(decoding: again, as: UTF8.self).contains("a.example"))
    }

    func testItDoesNotListenOnAnyOtherAddress() async throws {
        let server = PACServer()
        try await server.start()
        defer { server.stop() }
        // The machine's own LAN address, if it has one, is refused.
        var addresses: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addresses) == 0 else { return }
        defer { freeifaddrs(addresses) }
        var lan: String?
        for pointer in sequence(first: addresses, next: { $0?.pointee.ifa_next }) {
            guard let entry = pointer?.pointee, entry.ifa_addr?.pointee.sa_family == UInt8(AF_INET) else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            getnameinfo(entry.ifa_addr, socklen_t(entry.ifa_addr.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST)
            let text = String(cString: host)
            if !text.hasPrefix("127.") && !text.hasPrefix("169.254.") { lan = text; break }
        }
        guard let lan else { throw XCTSkip("no LAN address on this Mac") }
        var request = URLRequest(url: URL(string: "http://\(lan):\(server.port)/proxy.pac")!)
        request.timeoutInterval = 3
        let configuration = URLSessionConfiguration.ephemeral
        configuration.connectionProxyDictionary = [:]
        do {
            _ = try await URLSession(configuration: configuration).data(for: request)
            XCTFail("the script must not be reachable from the network")
        } catch {}
    }
}

final class OrphanedEngineTests: XCTestCase {
    func testAnEngineLeftByACrashIsEndedAndSomebodyElsesProcessIsNot() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("engine.pid")

        let orphan = Process()
        orphan.executableURL = URL(fileURLWithPath: "/bin/sleep")
        orphan.arguments = ["60"]
        try orphan.run()
        OrphanedEngine.remember(orphan.processIdentifier, at: file)
        // Not an engine of ours: left alone, and the record is dropped.
        XCTAssertFalse(OrphanedEngine.clean(at: file))
        XCTAssertTrue(orphan.isRunning)

        OrphanedEngine.remember(orphan.processIdentifier, at: file)
        XCTAssertTrue(OrphanedEngine.clean(at: file, isEngine: { $0.hasSuffix("/sleep") }))
        orphan.waitUntilExit()
        XCTAssertFalse(orphan.isRunning)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
        // Nothing recorded, nothing done.
        XCTAssertFalse(OrphanedEngine.clean(at: file))
    }
}

final class OtherVPNTests: XCTestCase {
    func testOnlyTheConnectedVpnsAreNamed() {
        let listing = """
            Available network connection services in the current set (*=enabled):
            * (Disconnected)   62FD11DC-D2DD-448B-A132-D8ECDC65BB81 VPN (io.tailscale.ipn.macos) "Tailscale"                      [VPN:io.tailscale.ipn.macos]
            * (Connected)      5EBCC6E4-7717-4463-A913-7C296A1CF1A4 VPN (com.liguangming.Shadowrocket) "Shadowrocket"                   [VPN:com.liguangming.Shadowrocket]
            * (Disconnected)   0A6AB196-2B40-42D5-AFB5-8B3DB440F886 VPN (com.blancvpn.app) "BlancVPN"                       [VPN:com.blancvpn.app]
            """
        XCTAssertEqual(OtherVPN.parse(listing), ["Shadowrocket"])
        XCTAssertEqual(OtherVPN.parse(""), [])
    }
}
