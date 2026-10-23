import XCTest

@testable import VazieCore

private let uuid = "00000000-0000-4000-8000-000000000001"

final class FakeTunnel: Tunnel, @unchecked Sendable {
    let httpPort = 18080
    let socksPort = 18081
    var stopped = false
    var handler: (@Sendable () -> Void)?
    var counters = Traffic(rx: 2048, tx: 10)
    func stop() { stopped = true }
    func traffic() async -> Traffic? { counters }
    func onExit(_ handler: @escaping @Sendable () -> Void) { self.handler = handler }
}

/// A whole-Mac tunnel as the model sees it: a helper that can be started again in place.
final class FakeRestartableTunnel: RestartableTunnel, @unchecked Sendable {
    let httpPort = 18080
    let socksPort = 18081
    var stopped = false
    var shutdowns = 0
    var exitHandler: (@Sendable () -> Void)?
    var lostHandler: (@Sendable (Bool) -> Void)?
    var alive = true
    var held = false
    var killSwitchActive = false
    var interfaceName: String? = "en1"
    var serverAddress: String? = "203.0.113.5"
    var supervisorIsAlive: Bool { alive }
    var trafficIsHeld: Bool { held }
    func stop() { stopped = true; alive = false }
    func shutdown() async { shutdowns += 1; stop() }
    func traffic() async -> Traffic? { Traffic(rx: 2048, tx: 10) }
    func onExit(_ handler: @escaping @Sendable () -> Void) { exitHandler = handler }
    func onLost(_ handler: @escaping @Sendable (Bool) -> Void) { lostHandler = handler }
    func restart(outbound: VlessOutbound, address: String, split: SplitTunnel) async throws {}
}

final class FakeHealth: TunnelHealthChecking, @unchecked Sendable {
    private let lock = NSLock()
    private var queue: [Health] = []
    private var calls = 0
    var steady: Health = .ok
    var checks: Int { lock.lock(); defer { lock.unlock() }; return calls }
    func next(_ verdicts: [Health]) { lock.lock(); queue = verdicts; lock.unlock() }
    func check(_ tunnel: Tunnel) async -> Health {
        lock.lock(); defer { lock.unlock() }
        calls += 1
        return queue.isEmpty ? steady : queue.removeFirst()
    }
}

final class FakeEngine: TunnelEngine, @unchecked Sendable {
    var tunnel = FakeTunnel()
    /// Hands out helper-style tunnels that can be restarted in place (the whole-Mac mode).
    var restartable = false
    var restartableTunnel = FakeRestartableTunnel()
    var restarts = 0
    var restartError: TunnelStartError?
    var restartCan = true
    var startError: TunnelStartError?
    /// Failures for the next starts, one per call; then `startError`.
    var startErrors: [TunnelStartError] = []
    /// Every tunnel this engine has made, to see none is left running.
    var made: [FakeTunnel] = []
    var readinessFailure: Probe.Stage?
    var latencyMs: Int? = 42
    var carries = true
    func carriesSystemTraffic(_ tunnel: Tunnel) async -> Bool { carries }
    var started = 0
    func start(_ outbound: VlessOutbound) async throws -> Tunnel {
        started += 1
        if !startErrors.isEmpty { throw startErrors.removeFirst() }
        if let startError { throw startError }
        if restartable {
            restartableTunnel = FakeRestartableTunnel()
            return restartableTunnel
        }
        tunnel = FakeTunnel()
        made.append(tunnel)
        return tunnel
    }
    func restart(_ tunnel: Tunnel, outbound: VlessOutbound, split: SplitTunnel) async throws -> Bool {
        guard restartCan else { return false }
        restarts += 1
        if let restartError { throw restartError }
        return true
    }
    func readiness(of tunnel: Tunnel) async -> Probe.Stage? { readinessFailure }
    func latency(host: String, port: Int) async -> Int? { latencyMs }
}

final class FakeProxy: SystemProxyControlling, @unchecked Sendable {
    var applied: [ProxyPlan] = []
    var restored = 0
    var failApply = false
    var recovery = false
    /// Whether what was written is what the Mac's programs see; another VPN's proxy outranks it when it is not.
    var inEffect = true
    func isInEffect(_ plan: ProxyPlan) -> Bool { inEffect }
    func apply(_ plan: ProxyPlan) async throws {
        if failApply { throw SystemProxyError.refused }
        applied.append(plan)
    }
    func restore() async { restored += 1 }
    func needsRecovery() -> Bool { recovery }
}

final class FakeScript: ScriptServing, @unchecked Sendable {
    var last = ""
    var running = false
    var url: String { "http://127.0.0.1:19999/proxy.pac" }
    func start() async throws { running = true }
    func update(_ script: String) { last = script }
    func stop() { running = false }
}

final class VPNList: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: [String] = []
    var value: [String] {
        get { lock.lock(); defer { lock.unlock() }; return stored }
        set { lock.lock(); stored = newValue; lock.unlock() }
    }
}

@MainActor
final class AppModelTests: XCTestCase {
    var directory: URL!
    var engine: FakeEngine!
    var proxy: FakeProxy!
    var script: FakeScript!
    var health: FakeHealth!
    var model: AppModel!
    let vpns = VPNList()
    var others: [String] {
        get { vpns.value }
        set { vpns.value = newValue }
    }
    /// One key for every model over the same files, as the Keychain gives one.
    let keys = MemoryKeyStore()

    override func setUp() async throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("vazie-\(UUID().uuidString)")
        engine = FakeEngine()
        proxy = FakeProxy()
        script = FakeScript()
        health = FakeHealth()
        model = makeModel()
        await model.load()
        // The tests below are of the system-proxy mode; the whole-Mac mode has its own.
        model.setPrefs { $0.mode = .systemProxy }
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: directory)
    }

    private func makeModel() -> AppModel {
        let vault = Vault(url: directory.appendingPathComponent("profiles.vault"), keys: keys)
        var created: AppModel!
        let repository = Repository(vault: vault, onChange: { Task { @MainActor in await created?.reloadProfiles() } })
        created = AppModel(
            repository: repository, prefsStore: PrefsStore(url: directory.appendingPathComponent("prefs.json")), runner: engine,
            proxy: proxy, script: script, linksCache: directory.appendingPathComponent("links.json"),
            faqCache: directory.appendingPathComponent("faq.json"), language: .en,
            engineLocator: { URL(fileURLWithPath: "/usr/bin/true") }, observeNetwork: false,
            connectedVPNs: { [others = vpns] in others.value }, health: health,
            routeProvider: { PhysicalRoute.Route(interface: "en1", gateway: "192.168.31.1") },
            policy: ReconnectPolicy(base: 0.01, cap: 0.05, maxAttempts: 3, spread: 0), healthTick: 0.02, healthEvery: 1, wakeSettle: 0)
        return created
    }

    private func addProfile(_ name: String = "Home", host: String = "relay.example.net") async -> String {
        await model.createFromLink("vless://\(uuid)@\(host):443?security=tls&sni=\(host)", name: name)!
    }

    func testAnImportedConfigurationIsStoredEncryptedAndSelected() async throws {
        let id = await addProfile()
        XCTAssertEqual(model.profiles.map(\.id), [id])
        XCTAssertEqual(model.selectedId, id)
        // The file holds nothing readable: neither the user id nor the host.
        let bytes = try Data(contentsOf: directory.appendingPathComponent("profiles.vault"))
        XCTAssertNil(bytes.range(of: Data(uuid.utf8)))
        XCTAssertNil(bytes.range(of: Data("relay.example.net".utf8)))
        // …and a new model over the same files reads it back.
        let again = makeModel()
        await again.load()
        XCTAssertEqual(again.profiles.map(\.name), ["Home"])
        XCTAssertEqual(again.selectedId, id)
    }

    func testConnectingPointsTheMacAtTheTunnelOnlyAfterItIsProven() async {
        _ = await addProfile()
        await model.connect()
        guard case .connected = model.snapshot.state else { return XCTFail("\(model.snapshot.state)") }
        XCTAssertEqual(proxy.applied, [.direct(httpPort: 18080, socksPort: 18081)])
        XCTAssertNotNil(model.profiles.first?.lastUsedAt)
        await model.disconnect()
        XCTAssertEqual(model.snapshot.state, .idle)
        XCTAssertTrue(engine.tunnel.stopped)
        XCTAssertGreaterThanOrEqual(proxy.restored, 1)
    }

    func testAnUnusableTunnelNeverTouchesTheSystemSettings() async {
        _ = await addProfile()
        engine.readinessFailure = .tls
        await model.connect()
        XCTAssertEqual(model.snapshot.state, .failed(.tunnelUnusable))
        XCTAssertTrue(proxy.applied.isEmpty)
        XCTAssertTrue(engine.tunnel.stopped)
    }

    func testRefusedSystemSettingsStopTheEngineAndSayWhy() async {
        _ = await addProfile()
        proxy.failApply = true
        await model.connect()
        XCTAssertEqual(model.snapshot.state, .failed(.systemProxy))
        XCTAssertTrue(engine.tunnel.stopped)
    }

    func testAnotherVpnsProxyOutranksOursAndConnectedIsNotClaimed() async {
        _ = await addProfile()
        proxy.inEffect = false
        await model.connect()
        XCTAssertEqual(model.snapshot.state, .failed(.proxyTaken))
        XCTAssertTrue(engine.tunnel.stopped)
        XCTAssertGreaterThanOrEqual(proxy.restored, 1)
    }

    func testSystemTrafficThatNeverReachesTheTunnelIsNotCalledConnected() async {
        _ = await addProfile()
        engine.carries = false
        await model.connect()
        XCTAssertEqual(model.snapshot.state, .failed(.proxyTaken))
        XCTAssertTrue(engine.tunnel.stopped)
        XCTAssertGreaterThanOrEqual(proxy.restored, 1)
    }

    func testAProxyTakenOverWhileConnectedIsNoticed() async throws {
        _ = await addProfile()
        await model.connect()
        guard case .connected = model.snapshot.state else { return XCTFail("\(model.snapshot.state)") }
        proxy.inEffect = false
        try await Task.sleep(nanoseconds: 7_500_000_000)
        XCTAssertEqual(model.snapshot.state, .failed(.proxyTaken))
    }

    func testWholeMacModeOpensNoSystemProxyAndStillProvesTheTraffic() async {
        _ = await addProfile()
        model.setPrefs { $0.mode = .wholeMac; $0.autoReconnect = false }
        await model.connect()
        guard case .connected = model.snapshot.state else { return XCTFail("\(model.snapshot.state)") }
        XCTAssertTrue(proxy.applied.isEmpty, "the interface takes the traffic; the proxy settings stay as they are")
        engine.tunnel.handler?()
        try? await Task.sleep(nanoseconds: 200_000_000)
        // The engine is gone and nothing brings it back: said as lost, not as an error of starting.
        guard case let .lost(_, blocked) = model.snapshot.state else { return XCTFail("\(model.snapshot.state)") }
        XCTAssertFalse(blocked)
    }

    func testWholeMacModeRefusesToStartBesideAnotherConnectedVpn() async {
        _ = await addProfile()
        model.setPrefs { $0.mode = .wholeMac }
        others = ["Shadowrocket"]
        await model.connect()
        XCTAssertEqual(model.snapshot.state, .failed(.proxyTaken))
        XCTAssertEqual(model.conflictingVPNs, ["Shadowrocket"])
        XCTAssertEqual(engine.started, 0)
    }

    func testAPasswordThatWasNotGivenIsSaidSo() async {
        _ = await addProfile()
        model.setPrefs { $0.mode = .wholeMac }
        engine.startError = .cancelled
        await model.connect()
        XCTAssertEqual(model.snapshot.state, .failed(.adminDenied))
        engine.startError = .privilege
        await model.connect()
        XCTAssertEqual(model.snapshot.state, .failed(.tun))
    }

    func testEngineStartFailuresAreNamed() async {
        _ = await addProfile()
        for (error, failure) in [(TunnelStartError.bind, Failure.tunnel), (.timeout, .handshake), (.config, .profile), (.engine, .engine)] {
            engine.startError = error
            await model.connect()
            XCTAssertEqual(model.snapshot.state, .failed(failure), "\(error)")
        }
        XCTAssertTrue(proxy.applied.isEmpty)
    }

    func testAConfigurationTheEngineCannotRunNeverStartsAnything() async {
        let id = await model.createFromLink("vless://\(uuid)@h.example:443?security=tls&allowInsecure=1", name: "Insecure")!
        XCTAssertEqual(model.profiles.first?.runnable, false)
        await model.connect(id)
        XCTAssertEqual(model.snapshot.state, .failed(.unsupported))
        XCTAssertEqual(engine.started, 0)
    }

    func testTheEngineDyingUnderALiveTunnelGivesTheSettingsBack() async throws {
        _ = await addProfile()
        model.setPrefs { $0.autoReconnect = false }
        await model.connect()
        let before = proxy.restored
        engine.tunnel.handler?()
        try await Task.sleep(nanoseconds: 200_000_000)
        guard case .lost = model.snapshot.state else { return XCTFail("\(model.snapshot.state)") }
        XCTAssertGreaterThan(proxy.restored, before, "the Mac does not keep pointing at a proxy nothing answers")
    }

    func testTheNetworkGoingAndComingBackIsReportedAndTheTunnelIsKept() async {
        _ = await addProfile()
        await model.connect()
        model.networkChanged(online: false)
        XCTAssertEqual(model.snapshot.state, .noInternet)
        model.networkChanged(online: true)
        // Connected again only after the tunnel has been looked at.
        try? await Task.sleep(nanoseconds: 300_000_000)
        guard case .connected = model.snapshot.state else { return XCTFail("\(model.snapshot.state)") }
        XCTAssertGreaterThan(health.checks, 0)
    }

    func testASplitCanChangeWhileConnectedWithoutANewConnection() async {
        _ = await addProfile()
        await model.connect()
        await model.setSplit(SplitTunnel(mode: .exclude, sites: ["example.org"]))
        XCTAssertEqual(proxy.applied.last, .script(url: script.url))
        XCTAssertTrue(script.last.contains("example.org"))
        XCTAssertEqual(engine.started, 1)
        await model.setSplit(.off)
        XCTAssertEqual(proxy.applied.last, .direct(httpPort: 18080, socksPort: 18081))
    }

    func testAPreviousRunThatLeftTheSettingsChangedIsPutRightOnLaunch() async {
        proxy.recovery = true
        let fresh = makeModel()
        await fresh.load()
        XCTAssertEqual(proxy.restored, 1)
    }

    func testRenameDuplicateRemoveAndLastUsed() async {
        let id = await addProfile()
        await model.rename(id, to: "  Work  ")
        XCTAssertEqual(model.profiles.first?.name, "Work")
        let copy = await model.duplicate(id, name: "Work (copy)")
        XCTAssertNotNil(copy)
        XCTAssertEqual(model.profiles.count, 2)
        XCTAssertNil(model.profiles.first { $0.id == copy }?.lastUsedAt)
        await model.remove(id)
        XCTAssertEqual(model.profiles.map(\.id), [copy!])
        XCTAssertNil(model.selectedId)
    }

    func testTheExportedLinkIsTheOneImported() async {
        let original = "vless://\(uuid)@relay.example.net:443?security=reality&pbk=KEY&sid=ab&sni=www.example.com&fp=chrome&flow=xtls-rprx-vision&foo=bar#Home"
        let id = await model.createFromLink(original, name: "Home")!
        let link = await model.exportLink(id)!
        XCTAssertEqual(Parsers.parse(link), Parsers.parse(original))
        let revealed = await model.revealCredential(id)
        XCTAssertEqual(revealed, uuid)
    }

    func testAnUnreadableVaultIsSetAsideNotOverwritten() async throws {
        let url = directory.appendingPathComponent("profiles.vault")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("not a vault".utf8).write(to: url)
        let fresh = makeModel()
        await fresh.load()
        XCTAssertEqual(fresh.profiles.count, 0)
        _ = await fresh.createFromLink("vless://\(uuid)@h.example:443", name: "New")
        let names = try FileManager.default.contentsOfDirectory(atPath: directory.path)
        XCTAssertTrue(names.contains { $0.hasPrefix("profiles.vault.unreadable-") }, "\(names)")
    }

    func testLatenciesAreMeasuredForEveryConfiguration() async {
        let a = await addProfile("A", host: "a.example.net")
        let b = await addProfile("B", host: "b.example.net")
        await model.measureLatencies()
        XCTAssertEqual(model.latencies[a], .some(42))
        XCTAssertEqual(model.latencies[b], .some(42))
        engine.latencyMs = nil
        await model.measureLatencies()
        XCTAssertEqual(model.latencies[a], .some(nil))
    }
}
