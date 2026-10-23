import XCTest

@testable import VazieCore

/// The root supervisor, its protocol and its defences. Run as the ordinary user against stand-ins (see `SupervisorHarness`):
/// they prove what the script asks of the system and what it refuses, not that macOS will do it. The parts that need root
/// are listed in MACOS_HARDENING_REPORT.md as manual tests.
final class SupervisorTests: XCTestCase {
    var harness: SupervisorHarness!

    override func setUpWithError() throws { harness = try SupervisorHarness() }
    override func tearDown() { harness.cleanUp() }

    private func waitStatus(_ prefix: String, seconds: Double = 10) -> Bool { harness.waitFor(seconds) { harness.status.hasPrefix(prefix) } }

    // -- the protocol --------------------------------------------------------------------------------------------------

    func testStatusLinesAreParsedWithTheirNonce() {
        XCTAssertEqual(TunStatus.parse("ready\n").kind, .ready)
        XCTAssertEqual(TunStatus.parse("running deadbeef01"), TunStatus(kind: .running, nonce: "deadbeef01"))
        XCTAssertEqual(TunStatus.parse("failed route deadbeef01"), TunStatus(kind: .failed("route"), nonce: "deadbeef01"))
        XCTAssertEqual(TunStatus.parse("lost blocked").kind, .lostBlocked)
        XCTAssertEqual(TunStatus.parse("lost open").kind, .lostOpen)
        XCTAssertEqual(TunStatus.parse("failed integrity").kind, .failed("integrity"))
        // A nonce that is not hex, or too short, is not one.
        XCTAssertNil(TunStatus.parse("running ZZZZZZZZ").nonce)
        XCTAssertNil(TunStatus.parse("running abc").nonce)
        XCTAssertEqual(TunStatus.parse("garbage").kind, .unknown)
    }

    func testTheEmbeddedScriptIsTheOneInThePackagingFolder() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("packaging/tun-run.sh")
        let file = try String(contentsOf: url, encoding: .utf8)
        XCTAssertEqual(TunScriptSource.text + "\n", file + (file.hasSuffix("\n") ? "" : "\n"), "run scripts/embed-tun-script.sh")
    }

    func testAStartRunsThroughTheSupervisorAndAStopPutsEverythingBack() async throws {
        let session = try await harness.session()
        XCTAssertEqual(session.currentStatus().kind, .running)
        // The engine got the checked document through its standard input, and no file holds it.
        XCTAssertTrue(harness.waitFor { harness.read("received.json").contains("\"outbounds\"") })
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")
        XCTAssertEqual(harness.ipv6, "Off")
        XCTAssertTrue(harness.calls.contains("route -q add -host 203.0.113.5 192.168.31.1"), harness.calls)
        XCTAssertTrue(harness.calls.contains("route -q add -net 0.0.0.0/1 -interface utun"), harness.calls)
        XCTAssertTrue(harness.calls.contains("route -q add -net 128.0.0.0/1 -interface utun"), harness.calls)
        XCTAssertFalse(harness.calls.contains("pfctl"), "the kill switch was not asked for")
        // The directory every user of the Mac can read holds neither the keys nor where the server is.
        for name in ["status", "pid", "info"] {
            let text = (try? String(contentsOf: harness.run.appendingPathComponent(name), encoding: .utf8)) ?? ""
            XCTAssertFalse(text.contains("00000000-0000-4000"), name)
            XCTAssertFalse(text.contains("203.0.113.5"), "\(name) tells every local user where the VPN server is")
        }
        XCTAssertTrue((try? String(contentsOf: harness.run.appendingPathComponent("info"), encoding: .utf8))?.contains("iface=en1") == true)

        await session.shutdown()
        XCTAssertEqual(harness.status, "stopped")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8", "the DNS is back as it was")
        XCTAssertEqual(harness.ipv6, "Automatic")
        XCTAssertTrue(harness.calls.contains("route -q delete -net 0.0.0.0/1"), harness.calls)
        XCTAssertFalse(harness.exists("xray"), "the engine copy is removed")
        XCTAssertFalse(harness.exists("cmd"))
    }

    func testAStopTwiceIsHarmless() async throws {
        let session = try await harness.session()
        await session.shutdown()
        let before = harness.calls
        await session.shutdown()
        session.stop()
        XCTAssertEqual(harness.status, "stopped")
        XCTAssertEqual(harness.calls, before, "nothing is done a second time")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
    }

    func testDnsThatWasNeverSetIsPutBackAsEmpty() async throws {
        harness.write("dns.Wi-Fi", "")
        let session = try await harness.session()
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")
        await session.shutdown()
        XCTAssertEqual(harness.dns, "", "back to automatic")
        XCTAssertTrue(harness.calls.contains("-setdnsservers Wi-Fi Empty"), harness.calls)
    }

    func testIPv6ThatWasOffIsLeftAlone() async throws {
        harness.write("v6.Wi-Fi", "Off")
        let session = try await harness.session()
        await session.shutdown()
        XCTAssertFalse(harness.calls.contains("-setv6"), harness.calls)
    }

    func testWhatSomebodyElseChangedAfterTheTunnelWasUpIsNotOverwritten() async throws {
        let session = try await harness.session()
        harness.write("dns.Wi-Fi", "4.4.4.4\n")
        harness.write("v6.Wi-Fi", "Automatic\n")
        await session.shutdown()
        XCTAssertEqual(harness.dns, "4.4.4.4", "another app's DNS is not put back to the old one")
        XCTAssertFalse(harness.calls.contains("-setdnsservers Wi-Fi 9.9.9.9"), harness.calls)
    }

    // -- the document is checked ---------------------------------------------------------------------------------------

    private func checker(_ json: Data) throws -> String {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        let script = harness.dir.appendingPathComponent("check.sh")
        try TunScriptSource.text.write(to: script, atomically: true, encoding: .utf8)
        process.arguments = [script.path, "--check-config"]
        let input = Pipe(), output = Pipe()
        process.standardInput = input
        process.standardOutput = output
        process.standardError = FileHandle.nullDevice
        try process.run()
        input.fileHandleForWriting.write(json)
        try input.fileHandleForWriting.close()
        let data = output.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        return String(decoding: data, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func document(_ link: String? = nil, split: SplitTunnel = .off) throws -> [String: Any] {
        let data = try TunSession.document(outbound: harness.outbound(link ?? "vless://00000000-0000-4000-8000-000000000001@relay.example.net:443?security=tls&sni=relay.example.net"), address: "203.0.113.5", split: split, ports: (18080, 18081, 18082), route: PhysicalRoute.Route(interface: "en1", gateway: "192.168.31.1"))
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }

    private func json(_ object: [String: Any]) throws -> Data { try JSONSerialization.data(withJSONObject: object) }

    /// Everything the app can produce is accepted: a mismatch between the generator and the allow-list would be a tunnel
    /// that never opens.
    func testTheCheckerAcceptsWhatTheAppGenerates() throws {
        let uuid = "00000000-0000-4000-8000-000000000001"
        let links = [
            "vless://\(uuid)@relay.example.net:443?security=tls&sni=relay.example.net",
            "vless://\(uuid)@relay.example.net:443?security=reality&pbk=Zm9vYmFyYmF6cXV4&sni=cdn.example.com&fp=chrome&sid=ab12&flow=xtls-rprx-vision&type=tcp",
            "vless://\(uuid)@relay.example.net:443?security=tls&type=ws&path=%2Fp&host=cdn.example.com",
            "vless://\(uuid)@relay.example.net:443?security=tls&type=grpc&serviceName=svc&alpn=h2",
            "vless://\(uuid)@relay.example.net:8443?security=none&type=tcp",
        ]
        for link in links {
            let first = try checker(json(document(link)))
            XCTAssertTrue(first.hasPrefix("OK\nTUN=utun"), "\(link)\n\(first)")
            XCTAssertTrue(first.contains("SERVER=203.0.113.5\nPORT=\(link.contains(":8443") ? 8443 : 443)\nIFACE=en1\nSPLIT=0"), first)
        }
        for mode in [SplitMode.exclude, .include] {
            let out = try checker(json(document(split: SplitTunnel(mode: mode, sites: ["a.example", "sub.b.example", "192.0.2.7", "[2001:db8::1]"]))))
            XCTAssertTrue(out.hasPrefix("OK"), "\(mode): \(out)")
            XCTAssertTrue(out.contains("SPLIT=1"))
        }
        // The largest split the app allows still fits.
        let sites = (0..<SplitTunnel.maxSites).map { "site\($0).example" }
        XCTAssertTrue(try checker(json(document(split: SplitTunnel(mode: .exclude, sites: sites)))).hasPrefix("OK"))
    }

    func testTheCheckerRefusesWhatCouldHurtWhenRunAsRoot() throws {
        func mutate(_ change: (inout [String: Any]) -> Void) throws -> String {
            var doc = try document()
            change(&doc)
            return try checker(json(doc))
        }
        // A log file written as root.
        XCTAssertTrue(try mutate { $0["log"] = ["loglevel": "none", "access": "/etc/sudoers.d/x"] }.hasPrefix("ERR log"))
        XCTAssertTrue(try mutate { $0["log"] = ["loglevel": "debug"] }.hasPrefix("ERR"))
        // Listening beyond the loopback, or on a path.
        XCTAssertTrue(try mutate { doc in
            var inbounds = doc["inbounds"] as! [[String: Any]]
            for i in inbounds.indices where inbounds[i]["protocol"] as? String == "http" { inbounds[i]["listen"] = "0.0.0.0" }
            doc["inbounds"] = inbounds
        }.hasPrefix("ERR inbound.listen"))
        XCTAssertTrue(try mutate { doc in
            var inbounds = doc["inbounds"] as! [[String: Any]]
            for i in inbounds.indices where inbounds[i]["protocol"] as? String == "socks" { inbounds[i]["listen"] = "/tmp/evil.sock" }
            doc["inbounds"] = inbounds
        }.hasPrefix("ERR"))
        // A keys the app never writes.
        XCTAssertTrue(try mutate { $0["dns"] = ["hosts": ["a": "b"]] }.hasPrefix("ERR config.dns"))
        XCTAssertTrue(try mutate { $0["transport"] = [:] }.hasPrefix("ERR"))
        XCTAssertTrue(try mutate { $0["metrics"] = ["listen": "0.0.0.0:1"] }.hasPrefix("ERR"))
        // A server that is a name (it must be the address the app resolved), or an outbound that is not one of ours.
        XCTAssertTrue(try mutate { doc in
            var outbounds = doc["outbounds"] as! [[String: Any]]
            var settings = outbounds[0]["settings"] as! [String: Any]
            var vnext = settings["vnext"] as! [[String: Any]]
            vnext[0]["address"] = "evil.example"
            settings["vnext"] = vnext
            outbounds[0]["settings"] = settings
            doc["outbounds"] = outbounds
        }.hasPrefix("ERR vless.address"))
        XCTAssertTrue(try mutate { doc in
            var outbounds = doc["outbounds"] as! [[String: Any]]
            outbounds.append(["tag": "x", "protocol": "shadowsocks"])
            doc["outbounds"] = outbounds
        }.hasPrefix("ERR"))
        // A physical interface that is not one (the engine would bind to something else).
        XCTAssertTrue(try mutate { doc in
            var outbounds = doc["outbounds"] as! [[String: Any]]
            var stream = outbounds[0]["streamSettings"] as! [String: Any]
            stream["sockopt"] = ["interface": "../../etc"]
            outbounds[0]["streamSettings"] = stream
            doc["outbounds"] = outbounds
        }.hasPrefix("ERR"))
        // Not a document at all, or one that is far too large.
        XCTAssertEqual(try checker(Data("not json".utf8)), "ERR json")
        XCTAssertEqual(try checker(Data("[]".utf8)).hasPrefix("ERR"), true)
        XCTAssertEqual(try checker(Data(repeating: 0x20, count: 300_000)), "ERR size")
    }

    func testARefusedDocumentNeverReachesTheEngineOrTheNetwork() async throws {
        // The supervisor, started the way the app starts it; then a hostile document through the pipe.
        try harness.spawn(arguments: harness.arguments())
        XCTAssertTrue(waitStatus("ready"))
        var doc = try document()
        doc["log"] = ["loglevel": "none", "access": "/etc/evil"]
        let line = Data("CONFIG aabbccdd11 ".utf8) + (try json(doc)) + Data("\n".utf8)
        XCTAssertTrue(TunSession.write(line, to: harness.run.appendingPathComponent("cmd"), within: 5))
        XCTAssertTrue(waitStatus("failed config aabbccdd11"), harness.status)
        XCTAssertEqual(harness.read("received.json"), "", "the engine never saw it")
        XCTAssertFalse(harness.calls.contains("route -q add"), harness.calls)
        XCTAssertFalse(harness.calls.contains("-setdnsservers"), harness.calls)
        // A document that is not preceded by a proper nonce is ignored altogether.
        XCTAssertTrue(TunSession.write(Data("CONFIG zz {}\nCONFIG\nGARBAGE\n".utf8), to: harness.run.appendingPathComponent("cmd"), within: 5))
        Thread.sleep(forTimeInterval: 1.5)
        XCTAssertTrue(harness.status.hasPrefix("failed config"), harness.status)
    }

    // -- the engine's integrity ----------------------------------------------------------------------------------------

    func testAnEngineThatIsNeitherSignedByTheTeamNorPinnedIsRefused() async throws {
        do {
            _ = try await harness.session(hashes: [String(repeating: "0", count: 64)])
            XCTFail("an unknown engine was run")
        } catch let error as TunnelStartError {
            XCTAssertEqual(error, .integrity)
        }
        XCTAssertEqual(harness.status, "failed integrity")
        XCTAssertFalse(harness.exists("xray"), "the unverified copy is not left where it could be run")
        XCTAssertFalse(harness.exists("cmd"), "and no pipe is opened for it")
        XCTAssertFalse(harness.calls.contains("route"), harness.calls)
    }

    func testAnEngineSignedByTheAppsTeamIsAccepted() async throws {
        harness.write("codesign.ok", "")
        let session = try await harness.session(team: "ABCDE12345", hashes: [String(repeating: "0", count: 64)])
        XCTAssertTrue(harness.calls.contains("codesign --verify --strict -R=anchor apple generic and certificate leaf[subject.OU] = \"ABCDE12345\""), harness.calls)
        await session.shutdown()
    }

    func testARestartNeedsNoNameLookupBecauseTheAddressIsKept() async throws {
        // The profile names its server; the address it was resolved to when the tunnel opened is what a restart goes on with.
        let engine = TunEngine(
            binary: { self.harness.engine }, prompt: { "" }, killSwitch: { true }, paths: TunPaths(run: harness.run), runner: harness.runner,
            route: harness.route, team: nil, pinned: [harness.engineSha])
        let byAddress = harness.outbound("vless://00000000-0000-4000-8000-000000000001@203.0.113.5:443?security=tls&sni=relay.example.net")
        let tunnel = try await engine.start(byAddress, split: .off)
        guard let session = tunnel as? TunSession else { return XCTFail() }
        XCTAssertEqual(session.serverAddress, "203.0.113.5")
        // The block is on, and the name `nowhere.invalid` could not be resolved by anyone.
        let byName = harness.outbound("vless://00000000-0000-4000-8000-000000000001@nowhere.invalid:443?security=tls")
        let restarted = try await engine.restart(tunnel, outbound: byName, split: .off)
        XCTAssertTrue(restarted)
        XCTAssertTrue(harness.status.hasPrefix("running"))
        await session.shutdown()
    }

    func testTheEngineIsCheckedAfterItIsCopiedNotBefore() async throws {
        // The original is changed after the app has pointed at it: the copy the script made is what was hashed, so a
        // swap between check and run is impossible; here the swap comes first, so the pin no longer matches.
        let tampered = harness.dir.appendingPathComponent("tampered")
        try "#!/bin/sh\necho owned\n".write(to: tampered, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: tampered.path)
        try harness.spawn(arguments: [String(getuid()), String(getpid()), tampered.path, "-", harness.engineSha, "0"])
        XCTAssertTrue(waitStatus("failed integrity"), harness.status)
    }

    // -- hostile surroundings ------------------------------------------------------------------------------------------

    func testArgumentsThatAreNotWhatTheyShouldBeAreRefused() throws {
        for bad in [
            ["x", "1", harness.engine.path, "-", "-", "0"], [String(getuid()), "$(id)", harness.engine.path, "-", "-", "0"],
            [String(getuid()), "1", "relative/xray", "-", "-", "0"], [String(getuid()), "1", harness.engine.path, "lowercase1", "-", "0"],
            [String(getuid()), "1", harness.engine.path, "-", "abc;touch /tmp/pwn", "0"], [String(getuid()), "1", harness.engine.path, "-", "-", "2"],
        ] {
            let process = try harness.spawn(arguments: bad)
            process.waitUntilExit()
            XCTAssertEqual(process.terminationStatus, 2, "\(bad)")
        }
        XCTAssertFalse(FileManager.default.fileExists(atPath: "/tmp/pwn"))
    }

    func testARunDirectoryThatIsASymlinkIsNotFollowed() throws {
        let real = harness.dir.appendingPathComponent("elsewhere")
        try FileManager.default.createDirectory(at: real, withIntermediateDirectories: true)
        let link = harness.dir.appendingPathComponent("link")
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: real)
        let script = harness.dir.appendingPathComponent("s.sh")
        try TunScriptSource.text.write(to: script, atomically: true, encoding: .utf8)
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        process.arguments = [script.path] + harness.arguments()
        process.environment = harness.environment.merging(["VAZIE_RUN": link.path]) { $1 }
        try process.run()
        process.waitUntilExit()
        XCTAssertEqual(process.terminationStatus, 2)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: real.path), [], "nothing was written through the link")
    }

    func testAStatusFileThatIsASymlinkIsReplacedNotWrittenThrough() throws {
        let victim = harness.dir.appendingPathComponent("victim")
        try "precious".write(to: victim, atomically: true, encoding: .utf8)
        try FileManager.default.createSymbolicLink(at: harness.run.appendingPathComponent("status"), withDestinationURL: victim)
        try FileManager.default.createSymbolicLink(at: harness.run.appendingPathComponent("cmd"), withDestinationURL: victim)
        try FileManager.default.createSymbolicLink(at: harness.run.appendingPathComponent("pid"), withDestinationURL: victim)
        try harness.spawn(arguments: harness.arguments())
        XCTAssertTrue(waitStatus("ready"))
        XCTAssertEqual(try String(contentsOf: victim, encoding: .utf8), "precious")
        var isLink = false
        for name in ["status", "cmd"] {
            let attrs = try FileManager.default.attributesOfItem(atPath: harness.run.appendingPathComponent(name).path)
            isLink = attrs[.type] as? FileAttributeType == .typeSymbolicLink
            XCTAssertFalse(isLink, name)
        }
    }

    func testTheSessionNeverFollowsASymlinkWhereThePipeShouldBe() throws {
        let victim = harness.dir.appendingPathComponent("victim")
        try "precious".write(to: victim, atomically: true, encoding: .utf8)
        let link = harness.dir.appendingPathComponent("pipe")
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: victim)
        XCTAssertFalse(TunSession.write(Data("STOP\n".utf8), to: link, within: 0.5))
        XCTAssertEqual(try String(contentsOf: victim, encoding: .utf8), "precious")
    }

    func testTwoSupervisorsAreNotRunAtOnce() throws {
        let first = try harness.spawn(arguments: harness.arguments())
        XCTAssertTrue(waitStatus("ready"))
        let second = try harness.spawn(arguments: harness.arguments())
        second.waitUntilExit()
        XCTAssertEqual(second.terminationStatus, 3)
        XCTAssertTrue(first.isRunning)
        XCTAssertEqual(harness.status, "ready", "the one that was running keeps its status")
    }

    func testAnArgumentWithQuotesStaysOneArgumentThroughThePromptsCommand() throws {
        // The command the system's prompt runs, executed here as the user against a script that records what it got.
        let hostile = "/tmp/it's; touch /tmp/vz-pwn; echo \"$(id)\" #"
        let script = "#!/bin/sh\nfor a in \"$@\"; do printf '%s\\n' \"$a\"; done > \"$VAZIE_STATE/args.txt\"\n"
        let command = AdministratorPrompt.bootstrap(script: script, arguments: ["1", hostile, "-"], wait: true, runDirectory: harness.run.path, rootOwned: false)
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        process.arguments = ["-c", command]
        process.environment = harness.environment
        try process.run()
        process.waitUntilExit()
        XCTAssertEqual(process.terminationStatus, 0)
        XCTAssertEqual(harness.read("args.txt"), "1\n\(hostile)\n-")
        XCTAssertFalse(FileManager.default.fileExists(atPath: "/tmp/vz-pwn"))
        // The script was written read-only for its owner, into the directory, and not elsewhere.
        let attrs = try FileManager.default.attributesOfItem(atPath: harness.run.appendingPathComponent("run.sh").path)
        XCTAssertEqual((attrs[.posixPermissions] as? NSNumber)?.intValue, 0o500)
    }

    func testThePromptsCommandReplacesASymlinkInsteadOfWritingThroughIt() throws {
        let elsewhere = harness.dir.appendingPathComponent("target")
        try FileManager.default.createDirectory(at: elsewhere, withIntermediateDirectories: true)
        let place = harness.dir.appendingPathComponent("rundir")
        try FileManager.default.createSymbolicLink(at: place, withDestinationURL: elsewhere)
        let command = AdministratorPrompt.bootstrap(script: "#!/bin/sh\nexit 0\n", arguments: [], wait: true, runDirectory: place.path, rootOwned: false)
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        process.arguments = ["-c", command]
        try process.run()
        process.waitUntilExit()
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: elsewhere.path), [], "nothing went through the link")
        XCTAssertTrue(FileManager.default.fileExists(atPath: place.appendingPathComponent("run.sh").path))
    }

    func testThePromptTextIsOneAppleScriptCommandWithTheSentenceInIt() {
        let source = AdministratorPrompt.appleScript("echo 'x'", prompt: "Open the \"tunnel\"")
        XCTAssertTrue(source.hasPrefix("do shell script "))
        XCTAssertTrue(source.hasSuffix("with administrator privileges"))
        XCTAssertTrue(source.contains("with prompt \"Open the \\\"tunnel\\\"\""))
    }

    // -- the supervisor's own lifetime ---------------------------------------------------------------------------------

    func testWhenTheAppIsGoneEverythingIsUndone() async throws {
        let parent = Process()
        parent.executableURL = URL(fileURLWithPath: "/bin/sleep")
        parent.arguments = ["600"]
        try parent.run()
        let supervisor = try harness.spawn(arguments: harness.arguments(parent: parent.processIdentifier))
        XCTAssertTrue(waitStatus("ready"))
        let nonce = "0123456789abcdef"
        XCTAssertTrue(TunSession.write(Data("CONFIG \(nonce) ".utf8) + (try json(document())) + Data("\n".utf8), to: harness.run.appendingPathComponent("cmd"), within: 5))
        XCTAssertTrue(waitStatus("running \(nonce)", seconds: 20), harness.status)
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")
        parent.terminate()
        XCTAssertTrue(harness.waitFor(15) { !supervisor.isRunning }, "the supervisor outlived the app")
        XCTAssertEqual(harness.status, "stopped")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
        XCTAssertEqual(harness.ipv6, "Automatic")
    }

    func testAProcessThatInheritedTheAppsNumberIsNotTheApp() async throws {
        // The stand-in `ps` reports a different start time for the parent once a file exists: the same number, another process.
        harness.tool("ps", "if [ -e \"$VAZIE_STATE/ps.changed\" ]; then echo 'Tue Oct 20 12:00:00 2026'; else exec /bin/ps \"$@\"; fi", guarded: false)
        let supervisor = try harness.spawn(arguments: harness.arguments())
        XCTAssertTrue(waitStatus("ready"))
        harness.write("ps.changed", "")
        XCTAssertTrue(harness.waitFor(15) { !supervisor.isRunning }, "a recycled process number kept the tunnel alive")
        XCTAssertEqual(harness.status, "stopped")
    }

    func testALeftoverFromAKilledSupervisorIsUndoneByTheNextStart() async throws {
        let first = try harness.spawn(arguments: harness.arguments(kill: true))
        XCTAssertTrue(waitStatus("ready"))
        let nonce = "fedcba9876543210"
        XCTAssertTrue(TunSession.write(Data("CONFIG \(nonce) ".utf8) + (try json(document())) + Data("\n".utf8), to: harness.run.appendingPathComponent("cmd"), within: 5))
        XCTAssertTrue(waitStatus("running \(nonce)", seconds: 20), harness.status)
        XCTAssertFalse(harness.pfRules.isEmpty)
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")
        // SIGKILL: no handler runs.
        kill(first.processIdentifier, SIGKILL)
        first.waitUntilExit()
        XCTAssertFalse(harness.pfRules.isEmpty, "the block is still there")
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")

        // The emergency path: the same script, asked to put things back.
        let recover = try harness.spawn(arguments: ["--recover"])
        recover.waitUntilExit()
        XCTAssertEqual(recover.terminationStatus, 0)
        XCTAssertEqual(harness.pfRules, "", "the anchor is flushed")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
        XCTAssertEqual(harness.ipv6, "Automatic")
        XCTAssertEqual(harness.status, "stopped")
        XCTAssertTrue(harness.calls.contains("pfctl -X 4242"), "the reference this run took is released")
        // And a second recovery has nothing to do.
        let again = try harness.spawn(arguments: ["--recover"])
        again.waitUntilExit()
        XCTAssertEqual(again.terminationStatus, 0)
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
    }

    func testANewStartUndoesWhatADeadSupervisorLeft() async throws {
        let first = try harness.spawn(arguments: harness.arguments())
        XCTAssertTrue(waitStatus("ready"))
        let nonce = "abababababababab"
        XCTAssertTrue(TunSession.write(Data("CONFIG \(nonce) ".utf8) + (try json(document())) + Data("\n".utf8), to: harness.run.appendingPathComponent("cmd"), within: 5))
        XCTAssertTrue(waitStatus("running \(nonce)", seconds: 20))
        kill(first.processIdentifier, SIGKILL)
        first.waitUntilExit()
        if let pid = harness.enginePid { kill(pid, SIGKILL) }
        // Starting again restores the DNS it finds changed, then takes the original values from the real network.
        let session = try await harness.session()
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")
        await session.shutdown()
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8", "the original survived the crash, not the tunnel's own values")
    }

    // -- failures while opening: nothing is left changed ---------------------------------------------------------------

    private func expectStart(_ expected: TunnelStartError, kill: Bool = false, file: StaticString = #filePath, line: UInt = #line) async {
        do {
            _ = try await harness.session(kill: kill)
            XCTFail("the tunnel opened", file: file, line: line)
        } catch let error as TunnelStartError {
            XCTAssertEqual(error, expected, file: file, line: line)
        } catch {
            XCTFail("\(error)", file: file, line: line)
        }
    }

    private func assertNothingLeft(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8", file: file, line: line)
        XCTAssertEqual(harness.ipv6, "Automatic", file: file, line: line)
        XCTAssertEqual(harness.pfRules, "", file: file, line: line)
        XCTAssertTrue(harness.calls.contains("route -q delete -net 0.0.0.0/1"), harness.calls, file: file, line: line)
    }

    func testRoutesThatCannotBeMadeLeaveTheNetworkAsItWas() async {
        harness.fail("route", matching: "add -net 128")
        await expectStart(.routes)
        assertNothingLeft()
    }

    func testDnsThatCannotBeChangedLeavesTheNetworkAsItWas() async {
        harness.fail("networksetup", matching: "-setdnsservers")
        await expectStart(.dns, kill: true)
        assertNothingLeft()
    }

    func testAnInterfaceThatCannotBeConfiguredLeavesNothing() async {
        harness.fail("ifconfig", matching: "inet")
        await expectStart(.tun)
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
    }

    func testATunnelThatCarriesNothingIsUndone() async {
        harness.write("curl.code", "000")
        await expectStart(.bypassed, kill: true)
        assertNothingLeft()
    }

    func testAKillSwitchThatCannotBeLoadedMeansNoTunnel() async {
        harness.fail("pfctl", matching: "-f -")
        await expectStart(.killSwitch, kill: true)
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
    }

    func testAKillSwitchThatTheSystemWouldNotEvaluateMeansNoTunnel() async {
        // The main ruleset has no `anchor "com.apple/*"`: rules in our anchor would never run, which is not a kill switch.
        harness.tool("pfctl", "if [ \"$1\" = \"-E\" ]; then echo 'Token : 1' >&2; exit 0; fi; if [ \"$1\" = \"-sr\" ]; then echo 'scrub-anchor \"x\"'; exit 0; fi; cat >/dev/null; exit 0")
        await expectStart(.killSwitch, kill: true)
    }

    func testANetworkThatMovedSinceTheDocumentWasMadeIsReportedNotUsed() async {
        harness.write("netstat.out", "default            10.0.0.1           UGScg                 en0\n")
        await expectStart(.network)
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
        XCTAssertFalse(harness.calls.contains("route -q add"), harness.calls)
    }

    func testAVpnsDefaultRouteIsNotTakenForTheMacsWayOut() async {
        // Another VPN's `default` on a tunnel is listed first; the supervisor takes the hardware one.
        let session = try? await harness.session()
        XCTAssertNotNil(session)
        XCTAssertTrue(harness.calls.contains("route -q add -host 203.0.113.5 192.168.31.1"), harness.calls)
        await session?.shutdown()
    }

    func testAnEngineThatWillNotStartIsReported() async {
        // The script exits at once: no interface ever appears.
        harness.tool("xray", "exit 1", guarded: false)
        try? FileManager.default.copyItem(at: harness.tools.appendingPathComponent("xray"), to: harness.dir.appendingPathComponent("dead"))
        let dead = harness.dir.appendingPathComponent("dead")
        try? FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: dead.path)
        let sha = try! Engine.sha256(of: dead)
        do {
            _ = try await TunSession.start(
                binary: dead, outbound: harness.outbound(), address: "203.0.113.5", split: .off, killSwitch: false, prompt: "", paths: TunPaths(run: harness.run),
                runner: harness.runner, team: nil, pinned: [sha], startupTimeout: 20, readyTimeout: 10, route: harness.route)
            XCTFail("opened")
        } catch let error as TunnelStartError {
            XCTAssertEqual(error, .engine)
        } catch { XCTFail("\(error)") }
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
    }
}
