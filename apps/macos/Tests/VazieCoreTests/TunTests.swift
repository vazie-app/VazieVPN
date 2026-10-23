import XCTest

@testable import VazieCore

private let uuid = "00000000-0000-4000-8000-000000000001"

final class TunConfigTests: XCTestCase {
    private func outbound(_ link: String) -> VlessOutbound {
        guard case let .recognized(draft) = Parsers.parse(link) else { fatalError(link) }
        return draft.outbound
    }

    private func rules(_ document: [String: Any]) -> [[String: Any]] { (document["routing"] as! [String: Any])["rules"] as! [[String: Any]] }

    func testTheTunInboundTakesTheTrafficAndTheServerIsKeptOffIt() throws {
        let document = XrayConfig.composeTun(outbound: outbound("vless://\(uuid)@relay.example.net:443?security=tls&sni=relay.example.net"), httpPort: 1, socksPort: 2, apiPort: 3, tunName: "utun77", split: .off)
        let inbounds = document["inbounds"] as! [[String: Any]]
        let tun = inbounds.first { $0["protocol"] as? String == "tun" }!
        let settings = tun["settings"] as! [String: Any]
        XCTAssertEqual(settings["name"] as? String, "utun77")
        XCTAssertNil(settings["autoSystemRoutingTable"], "the routes are the script's")
        // Everything else the app opens is on the loopback.
        for inbound in inbounds where inbound["protocol"] as? String != "tun" { XCTAssertEqual(inbound["listen"] as? String, "127.0.0.1") }
        // The Mac's own networks stay off the tunnel; the rest goes to the server.
        let list = rules(document)
        XCTAssertEqual(list[1]["outboundTag"] as? String, "direct")
        XCTAssertTrue((list[1]["ip"] as! [String]).contains("192.168.0.0/16"))
        XCTAssertEqual(list.last?["outboundTag"] as? String, "proxy")
        XCTAssertTrue((document["outbounds"] as! [[String: Any]]).contains { $0["tag"] as? String == "direct" })
        _ = try XrayConfig.json(document)
    }

    func testWhatGoesDirectOrToTheServerLeavesByThePhysicalInterface() {
        let document = XrayConfig.composeTun(outbound: outbound("vless://\(uuid)@h.example:443"), httpPort: 1, socksPort: 2, apiPort: 3, tunName: "utun77", split: .off, interface: "en1")
        let outbounds = document["outbounds"] as! [[String: Any]]
        for tag in ["direct", "proxy"] {
            let entry = outbounds.first { $0["tag"] as? String == tag }!
            let sockopt = (entry["streamSettings"] as! [String: Any])["sockopt"] as! [String: Any]
            XCTAssertEqual(sockopt["interface"] as? String, "en1", tag)
        }
    }

    func testASplitBecomesRoutingRulesByName() {
        let base = outbound("vless://\(uuid)@h.example:443")
        let exclude = rules(XrayConfig.composeTun(outbound: base, httpPort: 1, socksPort: 2, apiPort: 3, tunName: "utun77", split: SplitTunnel(mode: .exclude, sites: ["a.example"])))
        XCTAssertTrue(exclude.contains { ($0["domain"] as? [String]) == ["domain:a.example"] && $0["outboundTag"] as? String == "direct" })
        let include = rules(XrayConfig.composeTun(outbound: base, httpPort: 1, socksPort: 2, apiPort: 3, tunName: "utun77", split: SplitTunnel(mode: .include, sites: ["a.example"])))
        XCTAssertTrue(include.contains { ($0["domain"] as? [String]) == ["domain:a.example"] && $0["outboundTag"] as? String == "proxy" })
        XCTAssertTrue(include.contains { $0["network"] as? String == "tcp,udp" && $0["outboundTag"] as? String == "direct" })
        // A mode with no sites changes nothing.
        let none = rules(XrayConfig.composeTun(outbound: base, httpPort: 1, socksPort: 2, apiPort: 3, tunName: "utun77", split: SplitTunnel(mode: .exclude, sites: [])))
        XCTAssertEqual(none.count, 3)
    }

    func testTheServerNameIsLookedUpBeforeTheTunnelAndItsNameKeptForTls() {
        let pinned = XrayConfig.pinned(outbound("vless://\(uuid)@relay.example.net:443?security=reality&pbk=KEY"), to: "203.0.113.5")
        XCTAssertEqual(pinned.endpoint.host, "203.0.113.5")
        XCTAssertEqual(pinned.security.serverName, "relay.example.net")
        let ws = XrayConfig.pinned(outbound("vless://\(uuid)@relay.example.net:443?security=tls&type=ws&path=%2Fp"), to: "203.0.113.5")
        guard case let .ws(_, host) = ws.transport else { return XCTFail() }
        XCTAssertEqual(host, "relay.example.net")
        // An address that is already one is left alone.
        let same = XrayConfig.pinned(outbound("vless://\(uuid)@203.0.113.5:443?security=tls"), to: "203.0.113.5")
        XCTAssertNil(same.security.serverName)
    }

    func testAnActiveSplitSendsNameLookupsThroughTheTunnel() {
        let base = outbound("vless://\(uuid)@h.example:443")
        for mode in [SplitMode.exclude, .include] {
            let list = rules(XrayConfig.composeTun(outbound: base, httpPort: 1, socksPort: 2, apiPort: 3, tunName: "utun77", split: SplitTunnel(mode: mode, sites: ["a.example"])))
            let dns = list.firstIndex { $0["port"] as? String == "53" }
            let split = list.firstIndex { ($0["domain"] as? [String]) != nil }
            let direct = list.firstIndex { $0["network"] as? String == "tcp,udp" && $0["outboundTag"] as? String == "direct" }
            XCTAssertNotNil(dns, "\(mode)")
            XCTAssertEqual(list[dns!]["outboundTag"] as? String, "proxy")
            // Before the site rule and before the catch-all that sends the rest out directly.
            XCTAssertLessThan(dns!, split!)
            if let direct { XCTAssertLessThan(dns!, direct) }
            // The Mac's own networks still resolve locally.
            XCTAssertLessThan(list.firstIndex { ($0["ip"] as? [String])?.contains("192.168.0.0/16") == true }!, dns!)
        }
    }

    func testTheTunnelModeIsInThePreferencesAndDefaultsToTheSystemProxyUntilTheWholeMacIsProven() throws {
        XCTAssertEqual(Prefs().mode, .systemProxy)
        XCTAssertEqual(Prefs.sanitize(try JSONSerialization.data(withJSONObject: ["mode": "system_proxy"])).mode, .systemProxy)
        XCTAssertEqual(Prefs.sanitize(try JSONSerialization.data(withJSONObject: ["mode": "nonsense"])).mode, .systemProxy)
        XCTAssertEqual(Prefs.sanitize(try JSONSerialization.data(withJSONObject: ["mode": "whole_mac"])).mode, .wholeMac)
    }
}
