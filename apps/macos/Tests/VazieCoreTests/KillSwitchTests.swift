import XCTest

@testable import VazieCore

/// The kill switch, as far as it can be shown without root: the rules it asks pf for (parsed by the real `pfctl -n`, which loads
/// nothing), when they are loaded and removed, and what the Mac is left with in every end. Whether pf then really drops the
/// packets is a manual test (see MACOS_HARDENING_REPORT.md): nothing here changes the machine's firewall.
final class KillSwitchTests: XCTestCase {
    var harness: SupervisorHarness!

    override func setUpWithError() throws { harness = try SupervisorHarness() }
    override func tearDown() { harness.cleanUp() }

    private func rules(split: Bool = false) throws -> String {
        let script = harness.dir.appendingPathComponent("rules.sh")
        try TunScriptSource.text.write(to: script, atomically: true, encoding: .utf8)
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        process.arguments = [script.path, "--print-pf-rules", "utun77", "en1", "203.0.113.5", "443", split ? "1" : "0"]
        process.environment = harness.environment
        let out = Pipe()
        process.standardOutput = out
        try process.run()
        let data = out.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        return String(decoding: data, as: UTF8.self)
    }

    /// `pfctl -n` reads and checks rules and loads none; as the user it needs no privilege.
    private func syntaxErrors(_ rules: String) throws -> String {
        let file = harness.dir.appendingPathComponent("check.rules")
        try rules.write(to: file, atomically: true, encoding: .utf8)
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/sbin/pfctl")
        process.arguments = ["-n", "-f", file.path]
        let err = Pipe()
        process.standardError = err
        process.standardOutput = FileHandle.nullDevice
        try process.run()
        let data = err.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        return String(decoding: data, as: UTF8.self).split(separator: "\n").filter { $0.contains("syntax error") || $0.contains("error") }.joined(separator: "\n")
    }

    // -- the rules -----------------------------------------------------------------------------------------------------

    func testTheRulesAreSyntacticallyValidForThePacketFilter() throws {
        for split in [false, true] {
            let text = try rules(split: split)
            XCTAssertEqual(try syntaxErrors(text), "", text)
        }
        // And the checker is not blind: a broken rule is reported.
        XCTAssertNotEqual(try syntaxErrors("block drop quick bogus nonsense\n"), "")
    }

    func testTheRulesBlockEverythingExceptWhatTheTunnelNeeds() throws {
        let text = try rules()
        let lines = text.split(separator: "\n").map(String.init).filter { !$0.hasPrefix("#") && !$0.isEmpty }
        // Last-resort block for both families, after which nothing can match (every pass above is `quick`).
        XCTAssertEqual(lines.last, "block drop quick all")
        XCTAssertTrue(lines.dropLast().allSatisfy { ($0.hasPrefix("pass ") || $0.hasPrefix("block ")) && $0.contains(" quick ") }, "a rule that is not quick could be overridden")
        // The ways out: loopback, the tunnel, the one server connection, DHCP, the local network, IPv6 link-local.
        XCTAssertTrue(lines.contains("pass quick on lo0 all"))
        XCTAssertTrue(lines.contains("pass quick on utun77 all"))
        XCTAssertTrue(lines.contains { $0.contains("on en1") && $0.contains("to 203.0.113.5 port 443") }, "only that server, only that port, only outward")
        XCTAssertTrue(lines.contains { $0.contains("port 68") && $0.contains("port 67") }, "DHCP, or the lease would lapse")
        XCTAssertTrue(lines.contains { $0.contains("192.168.0.0/16") })
        XCTAssertTrue(lines.contains { $0.contains("inet6") && $0.contains("fe80::/10") })
        // Nothing lets the public internet through on the physical interface, IPv4 or IPv6.
        for line in lines.dropLast() where line.hasPrefix("pass ") {
            XCTAssertFalse(line.contains("to any") && !line.contains("port 67") && !line.contains("from { 10.0.0.0/8") && !line.contains("inet6") && !line.contains("lo0") && !line.contains("utun77"), line)
        }
        XCTAssertFalse(text.contains("user root"), "without a split, nothing but the server link leaves")
    }

    func testNoNameLookupMayReachTheLocalNetworkWhileTheTunnelIsTheOnlyWayOut() throws {
        let lines = try rules().split(separator: "\n").map(String.init)
        let dns = try XCTUnwrap(lines.firstIndex { $0.hasPrefix("block drop quick on en1 proto { tcp, udp } from any to any port 53") })
        let lan = try XCTUnwrap(lines.firstIndex { $0.hasPrefix("pass out quick inet from any to {") })
        XCTAssertLessThan(dns, lan, "before the local network is let through, or a router's resolver would be reachable")
        // The tunnel's own traffic is passed earlier still, so the lookups inside it are untouched.
        XCTAssertLessThan(try XCTUnwrap(lines.firstIndex { $0.hasPrefix("pass quick on utun77 all") }), dns)
    }

    func testASplitAlsoLetsTheEnginesOwnDirectConnectionsLeave() throws {
        let text = try rules(split: true)
        XCTAssertTrue(text.contains("on en1 proto { tcp, udp } from any to any user root"), text)
        XCTAssertEqual(text.split(separator: "\n").last, "block drop quick all")
    }

    func testTheRulesAreLoadedOnlyIntoOurOwnAnchorAndNothingIsFlushed() async throws {
        let session = try await harness.session(kill: true)
        let calls = harness.calls
        XCTAssertTrue(calls.contains("pfctl -a com.apple/050.VazieVPN -f -"), calls)
        XCTAssertTrue(calls.contains("pfctl -E"), "pf is enabled by reference, so it is released by reference")
        for line in calls.split(separator: "\n") where line.hasPrefix("pfctl") {
            XCTAssertFalse(line.contains(" -F all") || line.contains(" -F states") || line.contains(" -d"), "\(line): a global change")
            // Every rule change names the anchor.
            if line.contains(" -f") || line.contains(" -F") { XCTAssertTrue(line.contains("-a com.apple/050.VazieVPN"), String(line)) }
        }
        XCTAssertTrue(harness.pfRules.hasSuffix("block drop quick all"))
        await session.shutdown()
        XCTAssertEqual(harness.pfRules, "", "our rules are gone")
        XCTAssertTrue(harness.calls.contains("pfctl -a com.apple/050.VazieVPN -F rules"))
        XCTAssertTrue(harness.calls.contains("pfctl -X 4242"), "our reference to pf is released, not pf switched off for everyone")
        XCTAssertFalse(harness.calls.contains("pfctl -d"))
    }

    func testWithoutTheOptionNoFirewallIsTouched() async throws {
        let session = try await harness.session(kill: false)
        XCTAssertFalse(harness.calls.contains("pfctl"))
        await session.shutdown()
        XCTAssertFalse(harness.calls.contains("pfctl"))
    }

    // -- losing the tunnel ---------------------------------------------------------------------------------------------

    private func lost(_ session: TunSession, blocked: Bool) async -> Bool {
        let flag = LostFlag()
        session.onLost { flag.set($0) }
        if let pid = harness.enginePid { kill(pid, SIGKILL) }
        return await harness.waitFor(15) { flag.value == blocked }
    }

    func testWhenTheEngineDiesTheBlockStaysAndNothingGoesOut() async throws {
        let session = try await harness.session(kill: true)
        let reported = await lost(session, blocked: true)
        XCTAssertTrue(reported, harness.status)
        XCTAssertEqual(harness.status, "lost blocked")
        XCTAssertFalse(harness.pfRules.isEmpty, "the block is still loaded")
        // The routes into the dead interface are gone; the DNS is still the tunnel's (there is no safe direct one to go back to).
        XCTAssertTrue(harness.calls.contains("route -q delete -net 0.0.0.0/1"))
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")
        XCTAssertTrue(session.killSwitchActive)
        // And a person who disconnects gets the internet back, in full.
        await session.shutdown()
        XCTAssertEqual(harness.pfRules, "")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
        XCTAssertEqual(harness.status, "stopped")
    }

    func testWithoutTheKillSwitchALostEngineLeavesTheNetworkAsItWasAndSaysSo() async throws {
        let session = try await harness.session(kill: false)
        let reported = await lost(session, blocked: false)
        XCTAssertTrue(reported, harness.status)
        XCTAssertEqual(harness.status, "lost open")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8", "traffic is direct, so the DNS is the Mac's own again")
        XCTAssertEqual(harness.ipv6, "Automatic")
        XCTAssertEqual(harness.pfRules, "")
        await session.shutdown()
    }

    func testTheSameSupervisorBringsTheEngineBackAndTheBlockIsRenewedForTheNewNetwork() async throws {
        let session = try await harness.session(kill: true)
        _ = await lost(session, blocked: true)
        XCTAssertTrue(harness.pfRules.contains("on en1"))
        // The document was made for Wi-Fi; the Mac has since moved to Ethernet (the routing table says so, the app has not
        // looked yet). The restart is refused rather than run on the wrong interface, and the block is not lifted.
        harness.write("netstat.out", "default            192.168.1.1        UGScg                 en0\n")
        do {
            try await session.restart(outbound: harness.outbound(), address: "203.0.113.5", split: .off)
            XCTFail("restarted onto a network the document was not made for")
        } catch let error as TunnelStartError {
            XCTAssertEqual(error, .network)
        }
        XCTAssertFalse(harness.pfRules.isEmpty, "a restart that could not happen does not lift the block")
        // Now the app has looked: the document names Ethernet. The block is renewed for it, Wi-Fi's DNS goes back and
        // Ethernet's is the one that is changed.
        harness.moveTo(interface: "en0", gateway: "192.168.1.1")
        try await session.restart(outbound: harness.outbound(), address: "203.0.113.5", split: .off)
        XCTAssertTrue(harness.status.hasPrefix("running"))
        XCTAssertTrue(harness.pfRules.contains("pass out quick on en0"), harness.pfRules)
        XCTAssertFalse(harness.pfRules.contains("on en1"), "the old interface is no longer allowed to carry the server link")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8", "Wi-Fi's own DNS is back")
        XCTAssertEqual(harness.read("dns.Ethernet"), "1.1.1.1 1.0.0.1")
        XCTAssertTrue(harness.calls.contains("route -q add -host 203.0.113.5 192.168.1.1"), harness.calls)
        await session.shutdown()
        XCTAssertEqual(harness.read("dns.Ethernet"), "", "Ethernet's was automatic and is again")
        XCTAssertEqual(harness.dns, "9.9.9.9 8.8.8.8")
    }

    func testAFailedRestartUnderTheBlockKeepsTheBlock() async throws {
        let session = try await harness.session(kill: true)
        _ = await lost(session, blocked: true)
        harness.write("curl.code", "000")
        do {
            try await session.restart(outbound: harness.outbound(), address: "203.0.113.5", split: .off)
            XCTFail("the tunnel carried nothing and was accepted")
        } catch let error as TunnelStartError {
            XCTAssertEqual(error, .bypassed)
        }
        XCTAssertFalse(harness.pfRules.isEmpty, "a tunnel that had been up and cannot be brought back must not open the Mac")
        XCTAssertEqual(harness.dns, "1.1.1.1 1.0.0.1")
        XCTAssertTrue(session.trafficIsHeld, "the status names the failure, but the app is still told the block holds")
        XCTAssertTrue((try? String(contentsOf: harness.run.appendingPathComponent("info"), encoding: .utf8))?.contains("killswitch=blocked") == true)
        await session.shutdown()
        XCTAssertEqual(harness.pfRules, "")
    }

    func testAKillSwitchTheSystemRemovedIsPutBack() async throws {
        let session = try await harness.session(kill: true)
        // Somebody flushed our anchor while the tunnel was up.
        try? FileManager.default.removeItem(at: harness.state.appendingPathComponent("pf.rules"))
        let back = await harness.waitFor(15) { !harness.pfRules.isEmpty }
        XCTAssertTrue(back, "the supervisor did not notice the block was gone")
        await session.shutdown()
    }

    func testWhenTheAppDiesTheBlockIsLiftedSoTheMacIsNeverLeftWithoutInternet() async throws {
        let parent = Process()
        parent.executableURL = URL(fileURLWithPath: "/bin/sleep")
        parent.arguments = ["600"]
        try parent.run()
        let supervisor = try harness.spawn(arguments: harness.arguments(parent: parent.processIdentifier, kill: true))
        XCTAssertTrue(harness.waitFor { harness.status == "ready" })
        let document = try TunSession.document(outbound: harness.outbound(), address: "203.0.113.5", split: .off, ports: (18080, 18081, 18082), route: PhysicalRoute.Route(interface: "en1", gateway: "192.168.31.1"))
        XCTAssertTrue(TunSession.write(Data("CONFIG 0123456789abcdef ".utf8) + document + Data("\n".utf8), to: harness.run.appendingPathComponent("cmd"), within: 5))
        XCTAssertTrue(harness.waitFor(20) { harness.status.hasPrefix("running") })
        XCTAssertFalse(harness.pfRules.isEmpty)
        parent.terminate()
        XCTAssertTrue(harness.waitFor(15) { !supervisor.isRunning })
        XCTAssertEqual(harness.pfRules, "", "an app that is gone cannot disconnect, so the block goes with it")
        XCTAssertEqual(harness.status, "stopped")
    }
}

final class LostFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: Bool?
    var value: Bool? { lock.lock(); defer { lock.unlock() }; return stored }
    func set(_ value: Bool) { lock.lock(); stored = value; lock.unlock() }
}
