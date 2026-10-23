import JavaScriptCore
import XCTest

@testable import VazieCore

private let id = "00000000-0000-4000-8000-000000000001"

private func outbound(_ link: String, file: StaticString = #filePath, line: UInt = #line) -> VlessOutbound {
    guard case let .recognized(draft) = Parsers.parse(link) else {
        XCTFail("not recognized: \(link)", file: file, line: line)
        fatalError()
    }
    return draft.outbound
}

final class SharedTests: XCTestCase {
    func testALinkSurvivesParseSerializeParse() {
        let links = [
            "vless://\(id)@relay.example.net:443?security=reality&pbk=KEY&sid=ab&sni=www.example.com&fp=chrome&flow=xtls-rprx-vision&spx=%2F&foo=bar#Home%20server",
            "vless://\(id)@relay.example.net:443?type=ws&security=tls&sni=relay.example.net&path=%2Fws&host=cdn.example.net&alpn=h2%2Chttp%2F1.1",
            "vless://\(id)@[2001:db8::1]:8443?type=grpc&security=tls&serviceName=svc&mode=multi",
        ]
        for link in links {
            guard case let .recognized(draft) = Parsers.parse(link) else { return XCTFail(link) }
            let again = Parsers.parse(VlessSerializer.serialize(draft.outbound, name: draft.suggestedName))
            XCTAssertEqual(again, .recognized(draft), link)
        }
    }

    func testSubscriptionLinksAreHttpsOnlyWithNoUserInfo() {
        XCTAssertEqual(SubscriptionLink.parse("https://sub.example.com/token/abc?x=1")?.host, "sub.example.com")
        XCTAssertEqual(SubscriptionLink.parse("https://Sub.Example.com:8443/t")?.host, "sub.example.com")
        XCTAssertNil(SubscriptionLink.parse("https://user:pw@sub.example.com/t"))
        XCTAssertNil(SubscriptionLink.parse("https://sub.example.com/t with space"))
        XCTAssertNil(SubscriptionLink.parse("https://sub.example.com:99999999/t"))
        XCTAssertNil(SubscriptionLink.parse("vless://x"))
        XCTAssertTrue(SubscriptionLink.isInsecure("http://sub.example.com/t"))
        XCTAssertEqual(SubscriptionLink.parse("https://[2001:db8::1]:8443/t")?.host, "[2001:db8::1]")
    }

    func testASubscriptionIsLinksOrBase64OfLinksAndCountsWhatItLeftOut() {
        let good = "vless://\(id)@a.example.net:443#A"
        let other = "vless://\(id)@b.example.net:443?type=kcp#B"
        let plain = Subscription.read("\(good)\n\n# comment\n\(other)\nvmess://zzz\n")
        XCTAssertEqual(plain.recognized.count, 1)
        XCTAssertEqual(plain.skipped, 2)
        let encoded = Data("\(good)\n\(good)".utf8).base64EncodedString()
            .replacingOccurrences(of: "=", with: "").replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
        XCTAssertEqual(Subscription.read(encoded).recognized.count, 2)
        XCTAssertEqual(Subscription.read("<html>nope</html>").recognized.count, 0)
        let many = (0..<120).map { "vless://\(id)@h\($0).example.net:443" }.joined(separator: "\n")
        let capped = Subscription.read(many)
        XCTAssertEqual(capped.recognized.count, 100)
        XCTAssertTrue(capped.truncated)
    }

    func testWhatTheEngineCannotRunIsRefusedBeforeAnythingStarts() {
        XCTAssertEqual(Support.check(outbound("vless://\(id)@h.example:443?security=tls&allowInsecure=1")), .security)
        XCTAssertEqual(Support.check(outbound("vless://\(id)@h.example:443?security=tls&fp=unsafe")), .security)
        XCTAssertNil(Support.check(outbound("vless://\(id)@h.example:443?security=tls&fp=chrome")))
    }

    func testTheXrayOutboundIsTheAppsRawNetworkPreQuantumFingerprintRealityDefaults() throws {
        let list = XrayConfig.outbounds(outbound("vless://\(id)@h.example:443?security=reality&pbk=KEY&fp=chrome&flow=xtls-rprx-vision"))
        let proxy = list[0]
        XCTAssertEqual(proxy["protocol"] as? String, "vless")
        let stream = proxy["streamSettings"] as! [String: Any]
        XCTAssertEqual(stream["network"] as? String, "raw")
        let reality = stream["realitySettings"] as! [String: Any]
        XCTAssertEqual(reality["fingerprint"] as? String, "hellochrome_120")
        XCTAssertEqual(reality["shortId"] as? String, "")
        XCTAssertEqual(reality["spiderX"] as? String, "/")
        let vnext = (proxy["settings"] as! [String: Any])["vnext"] as! [[String: Any]]
        let user = (vnext[0]["users"] as! [[String: Any]])[0]
        XCTAssertEqual(user["flow"] as? String, "xtls-rprx-vision")
        XCTAssertEqual(user["encryption"] as? String, "none")
    }

    func testTheEnginesInboundsAreLoopbackOnlyAndLogsAreOff() throws {
        let document = XrayConfig.compose(outbound: outbound("vless://\(id)@h.example:443"), httpPort: 1080, socksPort: 1081, apiPort: 1082)
        for inbound in document["inbounds"] as! [[String: Any]] { XCTAssertEqual(inbound["listen"] as? String, "127.0.0.1") }
        XCTAssertEqual((document["log"] as! [String: Any])["loglevel"] as? String, "none")
        // It is a document the engine can read: valid JSON, with the user's id only inside the outbound.
        let json = String(decoding: try XrayConfig.json(document), as: UTF8.self)
        XCTAssertEqual(json.components(separatedBy: id).count - 1, 1)
    }

    func testSitesAreNormalizedFromWhateverWasTyped() {
        XCTAssertEqual(SplitTunnel.normalizeSite("https://Sub.Example.com/path?q=1"), "sub.example.com")
        XCTAssertEqual(SplitTunnel.normalizeSite("*.example.com"), "example.com")
        XCTAssertEqual(SplitTunnel.normalizeSite("example.com:8080"), "example.com")
        XCTAssertEqual(SplitTunnel.normalizeSite("192.168.1.1"), "192.168.1.1")
        XCTAssertNil(SplitTunnel.normalizeSite("300.1.1.1"))
        XCTAssertNil(SplitTunnel.normalizeSite("not a host"))
        XCTAssertNil(SplitTunnel.normalizeSite(""))
        XCTAssertNil(SplitTunnel.normalizeSite("-bad.example.com"))
        XCTAssertEqual(SplitTunnel.normalizeSite("[::1]:80"), "[::1]")
        XCTAssertNil(SplitTunnel.site(ofURL: "file:///etc/hosts"))
        XCTAssertEqual(SplitTunnel.site(ofURL: "https://www.example.com/a"), "www.example.com")
        let added = SplitTunnel(mode: .exclude, sites: []).adding("https://b.example.com")
        XCTAssertEqual(added?.sites, ["b.example.com"])
    }

    func testSplitTunnelingDecidesPerSiteAndAModeWithNoSitesChangesNothing() {
        XCTAssertTrue(SplitTunnel(mode: .exclude, sites: []).routesThroughTunnel(host: "a.com"))
        XCTAssertFalse(SplitTunnel(mode: .exclude, sites: ["a.com"]).routesThroughTunnel(host: "x.a.com"))
        XCTAssertTrue(SplitTunnel(mode: .exclude, sites: ["a.com"]).routesThroughTunnel(host: "nota.com"))
        XCTAssertTrue(SplitTunnel(mode: .include, sites: ["a.com"]).routesThroughTunnel(host: "a.com"))
        XCTAssertFalse(SplitTunnel(mode: .include, sites: ["a.com"]).routesThroughTunnel(host: "b.com"))
    }

    private func runPac(_ split: SplitTunnel, _ host: String) -> String {
        let context = JSContext()!
        context.evaluateScript(split.pacScript(httpPort: 4242))
        let find = context.objectForKeyedSubscript("FindProxyForURL")!
        return find.call(withArguments: ["https://\(host)/", host])!.toString()
    }

    func testTheGeneratedPacAgreesAndNeverFallsBackToDirectForTunnelTraffic() {
        let hosts = ["a.com", "x.a.com", "b.com", "localhost", "192.168.1.5", "10.0.0.1", "8.8.8.8", "printer.local", "intranet"]
        let locals: Set = ["localhost", "192.168.1.5", "10.0.0.1", "printer.local", "intranet"]
        let splits = [SplitTunnel.off, SplitTunnel(mode: .exclude, sites: ["a.com"]), SplitTunnel(mode: .include, sites: ["a.com"])]
        for split in splits {
            for host in hosts {
                let answer = runPac(split, host)
                if locals.contains(host) {
                    XCTAssertEqual(answer, "DIRECT", "\(split.mode) \(host)")
                } else {
                    XCTAssertEqual(answer, split.routesThroughTunnel(host: host) ? "PROXY 127.0.0.1:4242" : "DIRECT", "\(split.mode) \(host)")
                }
            }
        }
    }

    func testDurationsTrafficAndLastUsedReadAsTheAppReadsThem() {
        XCTAssertEqual(Format.sessionDuration(seconds: 252), "04:12")
        XCTAssertEqual(Format.sessionDuration(seconds: 3725), "1:02:05")
        XCTAssertEqual(Format.trafficReading(900), Format.Reading(amount: "900", unit: .b))
        XCTAssertEqual(Format.trafficReading(1536), Format.Reading(amount: "1", unit: .kb))
        XCTAssertEqual(Format.trafficReading(5 * 1024 * 1024 + 900_000), Format.Reading(amount: "5.8", unit: .mb))
        XCTAssertNil(Format.trafficReading(nil))

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        func ms(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int) -> Int64 {
            Int64(calendar.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!.timeIntervalSince1970 * 1000)
        }
        let now = ms(2026, 10, 19, 15, 0)
        XCTAssertEqual(Format.lastUsed(nil, now: now, calendar: calendar), .never)
        XCTAssertEqual(Format.lastUsed(now - 60_000, now: now, calendar: calendar), .justNow)
        XCTAssertEqual(Format.lastUsed(now + 60_000, now: now, calendar: calendar), .justNow)
        XCTAssertEqual(Format.lastUsed(ms(2026, 10, 19, 9, 5), now: now, calendar: calendar), .today(hour: 9, minute: 5))
        XCTAssertEqual(Format.lastUsed(ms(2026, 10, 18, 23, 0), now: now, calendar: calendar), .yesterday)
        XCTAssertEqual(Format.lastUsed(ms(2026, 9, 1, 12, 0), now: now, calendar: calendar), .earlier(year: 2026, month: 9, day: 1))
    }

    func testANameThatLooksLikeAnAddressNeverReachesAMenu() {
        XCTAssertEqual(Format.safeMenuLabel("Home server"), "Home server")
        XCTAssertNil(Format.safeMenuLabel("relay.example.net"))
        XCTAssertNil(Format.safeMenuLabel("203.0.113.7"))
        XCTAssertNil(Format.safeMenuLabel("a@b"))
        XCTAssertNil(Format.safeMenuLabel(id))
        XCTAssertEqual(Format.safeMenuLabel("A very long configuration name indeed"), "A very long configu…")
    }

    func testBackendAnswersAreReadDefensively() throws {
        let links = Backend.readLinks([
            "links": [
                ["key": "ok", "section": "AUTHOR", "url": "https://t.me/x", "title": ["ru": "а", "en": "a"]],
                ["key": "bad-url", "section": "AUTHOR", "url": "http://t.me/x", "title": ["ru": "а", "en": "a"]],
                ["key": "bad-section", "section": "NOPE", "url": "https://t.me/x", "title": ["ru": "а", "en": "a"]],
                ["key": "mail", "section": "SUPPORT", "url": "mailto:support@vazie.app", "title": ["ru": "п", "en": "s"]],
            ]
        ])
        XCTAssertEqual(links?.links.map(\.key), ["ok", "mail"])
        XCTAssertNil(Backend.readLinks(["links": [Any]()]))
        XCTAssertEqual(Backend.readFaq(["items": [Any]()])?.items.count, 0)
        XCTAssertNil(Backend.readFaq(["items": [["key": "k"]]]))
        let faq = Backend.readFaq([
            "items": [["key": "k", "question": ["ru": "в"], "answer": ["ru": "о\nк", "en": "a"], "link": ["url": "//evil.example", "label": ["ru": "л"]]]]
        ])
        XCTAssertNil(faq?.items.first?.link)
        XCTAssertEqual(faq?.items.first?.answer.en, "a")
    }

    func testStoredPreferencesAreNotATrustedEnum() throws {
        let raw: [String: Any] = [
            "appearance": "neon", "appIcon": "nope", "appIconPlate": "ink",
            "split": ["mode": "weird", "sites": ["https://A.com/x", "not a host", 3]], "viewedGuides": ["menu_bar", "bogus"],
        ]
        let prefs = Prefs.sanitize(try JSONSerialization.data(withJSONObject: raw))
        XCTAssertEqual(prefs.appearance, .nightIndigo)
        XCTAssertEqual(prefs.appIcon, .orbit)
        XCTAssertEqual(prefs.appIconPlate, .ink)
        XCTAssertEqual(prefs.split, SplitTunnel(mode: .off, sites: ["a.com"]))
        XCTAssertEqual(prefs.viewedGuides, [.menuBar])
        let mono = Prefs.sanitize(try JSONSerialization.data(withJSONObject: ["appIcon": "monochrome", "appIconPlate": "deep"]))
        XCTAssertEqual(mono.appIconPlate, .ink)
        // What the app writes, it reads back.
        var written = Prefs()
        written.appearance = .milk
        written.appIcon = .cottonCandy
        written.split = SplitTunnel(mode: .include, sites: ["a.com", "b.com"])
        written.dismissedTips = ["split"]
        XCTAssertEqual(Prefs.sanitize(try JSONEncoder().encode(written)), Prefs.sanitize(try JSONEncoder().encode(written)))
        XCTAssertEqual(Prefs.sanitize(try JSONEncoder().encode(written)).appIcon, .cottonCandy)
    }

    func testPluralsFollowTheLanguage() {
        XCTAssertEqual(Translator.pluralForm(.ru, 1), "one")
        XCTAssertEqual(Translator.pluralForm(.ru, 2), "few")
        XCTAssertEqual(Translator.pluralForm(.ru, 5), "many")
        XCTAssertEqual(Translator.pluralForm(.ru, 11), "many")
        XCTAssertEqual(Translator.pluralForm(.ru, 21), "one")
        XCTAssertEqual(Translator.pluralForm(.ru, 112), "many")
        XCTAssertEqual(Translator.pluralForm(.en, 1), "one")
        XCTAssertEqual(Translator.pluralForm(.en, 0), "other")
        XCTAssertEqual(Translator(language: .en).n("config_done_many_title", 3), "3 configurations added")
        XCTAssertEqual(Translator(language: .ru).n("config_done_many_title", 3), "Добавлены 3 конфигурации")
        XCTAssertEqual(Translator(language: .en).t("config_done_title", "Home"), "Home added")
        XCTAssertEqual(Translator(language: .en).t("home_route_session", "04:12", "VLESS"), "04:12 · VLESS")
        XCTAssertEqual(Translator(language: .en).t("no_such_key"), "no_such_key")
    }

    func testTheHomeScreenHasOneActionPerState() {
        func summary(_ id: String, runnable: Bool = true) -> ProfileSummary {
            var s = ProfileSummary(StoredProfile(id: id, name: id, createdAt: 1, outbound: outbound("vless://\(Self.uuid)@h.example:443")))
            s.runnable = runnable
            return s
        }
        func action(_ state: ConnectionState, profiles: [ProfileSummary]? = nil, subject: ConnectionSubject? = nil) -> Presentation {
            present(HomeView.make(snapshot: Snapshot(state: state, subject: subject, diagnostics: nil), profiles: profiles ?? [summary("a")], selectedId: "a"))
        }
        XCTAssertEqual(present(HomeView.make(snapshot: .idle, profiles: [], selectedId: nil)).action, .add)
        let unselected = present(HomeView.make(snapshot: .idle, profiles: [summary("a")], selectedId: nil))
        XCTAssertEqual(unselected.action, .connect)
        XCTAssertFalse(unselected.actionEnabled)
        XCTAssertEqual(action(.idle).action, .connect)
        XCTAssertEqual(action(.preparing).action, .cancel)
        XCTAssertEqual(action(.connecting(profileId: "a")).action, .cancel)
        XCTAssertEqual(action(.connected(profileId: "a", since: Date())).action, .disconnect)
        XCTAssertEqual(action(.failed(.engine)).action, .retry)
        XCTAssertEqual(action(.noInternet).action, .check)
        let blocked = action(.idle, profiles: [summary("a", runnable: false)])
        XCTAssertFalse(blocked.actionEnabled)
        XCTAssertEqual(blocked.line, .words(key: "home_subtitle_unsupported"))
        // The tunnel's profile outranks the selection.
        let view = HomeView.make(
            snapshot: Snapshot(state: .connecting(profileId: "b"), subject: ConnectionSubject(id: "b", name: "b"), diagnostics: nil),
            profiles: [summary("a"), summary("b")], selectedId: "a")
        guard case let .ready(name, _, _, _, _) = view else { return XCTFail() }
        XCTAssertEqual(name, "b")
    }

    private static let uuid = "00000000-0000-4000-8000-000000000001"
}

final class LatencyTests: XCTestCase {
    func testMadeUpAddressesAreRecognisedAndRealOnesAreNot() {
        XCTAssertTrue(DNS.isFake("198.18.0.7"))
        XCTAssertTrue(DNS.isFake("198.19.255.1"))
        XCTAssertFalse(DNS.isFake("198.20.0.1"))
        XCTAssertFalse(DNS.isFake("1.1.1.1"))
        XCTAssertTrue(DNS.isIPv4("203.0.113.7"))
        XCTAssertFalse(DNS.isIPv4("example.com"))
    }

    func testTheQueryAndTheReplyAreRead() {
        let query = [UInt8](DNS.query("relay.example.net", id: 0x1234))
        XCTAssertEqual(Array(query[0..<4]), [0x12, 0x34, 0x01, 0x00])
        XCTAssertEqual(Array(query.suffix(4)), [0x00, 0x01, 0x00, 0x01])
        // The reply: the question again, then a CNAME and an A record whose name is a pointer.
        var reply: [UInt8] = [0x12, 0x34, 0x81, 0x80, 0, 1, 0, 2, 0, 0, 0, 0]
        reply += Array(query[12...])
        reply += [0xC0, 0x0C, 0x00, 0x05, 0x00, 0x01, 0, 0, 0, 60, 0x00, 0x02, 0xC0, 0x0C]
        reply += [0xC0, 0x0C, 0x00, 0x01, 0x00, 0x01, 0, 0, 0, 60, 0x00, 0x04, 203, 0, 113, 9]
        XCTAssertEqual(DNS.firstAddress(in: Data(reply)), "203.0.113.9")
        XCTAssertNil(DNS.firstAddress(in: Data([1, 2, 3])))
    }

    func testAnAddressBehindAFakeResolverIsNotMeasured() async {
        let ms = await Probe.latency(host: "198.18.0.9", port: 443, attempts: 1, timeout: 1)
        XCTAssertNil(ms)
    }

    func testARealServerIsMeasuredOverThePhysicalInterfaceNotAnotherVpns() async throws {
        let ms = await Probe.latency(host: "one.one.one.one", port: 443, attempts: 2, timeout: 4)
        guard let ms else { throw XCTSkip("no internet from this machine") }
        print("LATENCY one.one.one.one: \(ms) ms")
        XCTAssertLessThan(ms, 1500)
    }
}
