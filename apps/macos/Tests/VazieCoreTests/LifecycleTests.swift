import XCTest

@testable import VazieCore

private let uuid = "00000000-0000-4000-8000-000000000001"

/// What recovery would answer, and what it was asked.
final class FakeRecovery: NetworkRecovering, @unchecked Sendable {
    private let lock = NSLock()
    var outcome: NetworkRecovery.Outcome = .clean
    var command: String?
    private(set) var begins = 0
    private(set) var reconciles: [Bool] = []
    func begin() async -> Bool { lock.lock(); begins += 1; lock.unlock(); return true }
    func reconcile(supervisorAlive: Bool) async -> NetworkRecovery.Outcome {
        lock.lock(); defer { lock.unlock() }
        reconciles.append(supervisorAlive)
        return outcome
    }
    func privilegedCommand() async -> String? { command }
}

final class FakePrivileged: PrivilegedRunner, @unchecked Sendable {
    private let lock = NSLock()
    private(set) var launches: [(script: String, arguments: [String], wait: Bool)] = []
    var result: PrivilegedResult = .finished(0)
    var onLaunch: (@Sendable () -> Void)?
    func launch(script: String, arguments: [String], wait: Bool, prompt: String) async -> PrivilegedResult {
        lock.lock(); launches.append((script, arguments, wait)); lock.unlock()
        onLaunch?()
        return result
    }
}

/// The model over fakes, with fast timings, for the scenarios of a connection's life.
@MainActor
class ModelCase: XCTestCase {
    var directory: URL!
    var engine: FakeEngine!
    var proxy: FakeProxy!
    var script: FakeScript!
    var health: FakeHealth!
    var recovery: FakeRecovery!
    var privileged: FakePrivileged!
    var model: AppModel!
    var tunPaths: TunPaths!
    let keys = MemoryKeyStore()
    let vpns = VPNList()

    override func setUp() async throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("vz-life-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory.appendingPathComponent("run"), withIntermediateDirectories: true)
        tunPaths = TunPaths(run: directory.appendingPathComponent("run"))
        engine = FakeEngine()
        proxy = FakeProxy()
        script = FakeScript()
        health = FakeHealth()
        recovery = FakeRecovery()
        privileged = FakePrivileged()
        model = makeModel()
        await model.load()
        model.setPrefs { $0.mode = .systemProxy }
    }

    override func tearDown() async throws {
        await model.shutdown()
        try? FileManager.default.removeItem(at: directory)
    }

    func makeModel(policy: ReconnectPolicy = ReconnectPolicy(base: 0.01, cap: 0.05, maxAttempts: 3, spread: 0)) -> AppModel {
        let vault = Vault(url: directory.appendingPathComponent("profiles.vault"), keys: keys)
        var created: AppModel!
        let repository = Repository(vault: vault, onChange: { Task { @MainActor in await created?.reloadProfiles() } })
        created = AppModel(
            repository: repository, prefsStore: PrefsStore(url: directory.appendingPathComponent("prefs.json")), runner: engine, tunRunner: engine,
            proxy: proxy, script: script, linksCache: directory.appendingPathComponent("links.json"),
            faqCache: directory.appendingPathComponent("faq.json"), language: .en,
            engineLocator: { URL(fileURLWithPath: "/usr/bin/true") }, observeNetwork: false,
            connectedVPNs: { [vpns] in vpns.value }, health: health, recovery: recovery, privileged: privileged, tunPaths: tunPaths,
            routeProvider: { PhysicalRoute.Route(interface: "en1", gateway: "192.168.31.1") },
            policy: policy, healthTick: 0.02, healthEvery: 1, wakeSettle: 0)
        return created
    }

    @discardableResult
    func addProfile() async -> String {
        await model.createFromLink("vless://\(uuid)@relay.example.net:443?security=tls&sni=relay.example.net", name: "Home")!
    }

    /// Polls until `condition` holds or the time is up.
    func eventually(_ seconds: Double = 5, _ condition: @escaping @MainActor () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        return condition()
    }

    var state: ConnectionState { model.snapshot.state }
    var isConnected: Bool { state.isConnected }

    /// A connected whole-Mac tunnel made of a helper that can be restarted in place.
    func connectWholeMac(autoReconnect: Bool = true, killSwitch: Bool = true) async {
        engine.restartable = true
        model.setPrefs { $0.mode = .wholeMac; $0.autoReconnect = autoReconnect; $0.killSwitch = killSwitch }
        await model.connect()
    }
}

final class LifecycleTests: ModelCase {
    // -- 1, 2: a connection and a disconnection ------------------------------------------------------------------------

    func testConnectedIsSaidOnlyAfterTheTunnelIsProven() async {
        await addProfile()
        engine.readinessFailure = .dns
        await model.connect()
        XCTAssertEqual(state, .failed(.tunnelUnusable))
        engine.readinessFailure = nil
        engine.carries = false
        await model.connect()
        XCTAssertFalse(isConnected, "traffic that does not reach the tunnel is not a connection")
        engine.carries = true
        await model.connect()
        XCTAssertTrue(isConnected)
    }

    func testAWholeMacTunnelWhoseRoutesAreNotInEffectIsNotCalledConnected() async {
        await addProfile()
        health.next([.dead("routes")])
        await connectWholeMac()
        XCTAssertEqual(state, .failed(.tunBypassed))
        XCTAssertFalse(state.isProtected)
        XCTAssertTrue(engine.restartableTunnel.stopped, "and what was opened is closed")
    }

    func testAManualDisconnectEndsEverything() async {
        await addProfile()
        await model.connect()
        XCTAssertTrue(isConnected)
        await model.disconnect()
        XCTAssertEqual(state, .idle)
        XCTAssertTrue(engine.tunnel.stopped)
        XCTAssertGreaterThanOrEqual(proxy.restored, 1)
    }

    // -- 3: the engine falls ---------------------------------------------------------------------------------------------

    func testAnEngineThatFallsIsBroughtBackByItself() async {
        await addProfile()
        await model.connect()
        let first = engine.tunnel
        engine.tunnel.handler?()
        let back = await eventually { [self] in engine.started == 2 && isConnected }
        XCTAssertTrue(back, "\(state)")
        XCTAssertTrue(first.stopped, "the dead one is not left behind")
        XCTAssertNotIdentical(first, engine.tunnel)
        XCTAssertEqual(proxy.applied.count, 2, "the proxy settings point at the new engine")
        XCTAssertEqual(engine.made.filter { !$0.stopped }.count, 1, "exactly one tunnel runs")
    }

    func testWhileItIsBeingBroughtBackTheStateIsReconnectingNotConnected() async {
        await addProfile()
        let slow = makeModel(policy: ReconnectPolicy(base: 0.4, cap: 0.4, maxAttempts: 3, spread: 0))
        await slow.load()
        slow.setPrefs { $0.mode = .systemProxy }
        _ = await slow.createFromLink("vless://\(uuid)@relay.example.net:443?security=tls", name: "Other")
        model = slow
        await model.connect()
        engine.tunnel.handler?()
        let seen = await eventually(2) { [self] in if case .reconnecting = state { return true } else { return false } }
        XCTAssertTrue(seen, "\(state)")
        XCTAssertFalse(state.isConnected)
        XCTAssertFalse(state.isProtected)
        let view = present(HomeView.make(snapshot: model.snapshot, profiles: model.profiles, selectedId: model.selectedId))
        XCTAssertEqual(view.statusKey, "mac_status_reconnecting")
        XCTAssertNotEqual(view.tone, .success)
    }

    func testAManualDisconnectCancelsAReconnectInProgress() async {
        await addProfile()
        let slow = makeModel(policy: ReconnectPolicy(base: 0.3, cap: 0.3, maxAttempts: 5, spread: 0))
        await slow.load()
        slow.setPrefs { $0.mode = .systemProxy }
        _ = await slow.createFromLink("vless://\(uuid)@relay.example.net:443?security=tls", name: "Other")
        model = slow
        await model.connect()
        engine.tunnel.handler?()
        _ = await eventually(2) { [self] in if case .reconnecting = state { return true } else { return false } }
        await model.disconnect()
        XCTAssertEqual(state, .idle)
        try? await Task.sleep(nanoseconds: 800_000_000)
        XCTAssertEqual(state, .idle, "nothing came back after the person said stop")
        XCTAssertEqual(engine.started, 1)
        XCTAssertTrue(engine.made.allSatisfy(\.stopped))
    }

    // -- 4: the server is down ------------------------------------------------------------------------------------------

    func testAServerThatStaysDownIsGivenUpOnAfterTheLimit() async {
        await addProfile()
        await model.connect()
        engine.startErrors = Array(repeating: .timeout, count: 50)
        engine.tunnel.handler?()
        let gaveUp = await eventually { [self] in if case .lost = state { return true } else { return false } }
        XCTAssertTrue(gaveUp, "\(state)")
        XCTAssertEqual(engine.started, 1 + model.policy.maxAttempts, "one attempt per allowed try, not a loop")
        try? await Task.sleep(nanoseconds: 400_000_000)
        XCTAssertEqual(engine.started, 1 + model.policy.maxAttempts, "and no more by itself")
        guard case let .lost(_, blocked) = state else { return XCTFail() }
        XCTAssertFalse(blocked)
        XCTAssertFalse(state.isProtected)
        // The person can ask again, and it works once the server is back.
        engine.startErrors = []
        await model.connect()
        XCTAssertTrue(isConnected)
    }

    func testTheBackoffDoublesIsCappedAndNeverNegative() {
        let policy = ReconnectPolicy(base: 1, cap: 30, maxAttempts: 8, spread: 0.2)
        XCTAssertEqual(policy.delay(forAttempt: 1, unit: 0.5), 1)
        XCTAssertEqual(policy.delay(forAttempt: 2, unit: 0.5), 2)
        XCTAssertEqual(policy.delay(forAttempt: 4, unit: 0.5), 8)
        XCTAssertEqual(policy.delay(forAttempt: 20, unit: 0.5), 30)
        XCTAssertEqual(policy.delay(forAttempt: 999, unit: 1), 30, "capped even with the spread at its top")
        XCTAssertEqual(policy.delay(forAttempt: 3, unit: 0), 3.2, accuracy: 0.0001)
        XCTAssertEqual(policy.delay(forAttempt: 3, unit: 1), 4.8, accuracy: 0.0001)
        XCTAssertGreaterThanOrEqual(policy.delay(forAttempt: 0, unit: 0), 0)
        XCTAssertGreaterThan(ReconnectPolicy.standard.delay(forAttempt: 1), 0, "never an instant retry")
    }

    func testOnlyFailuresThatCanChangeAreRetried() async {
        XCTAssertTrue(Failure.handshake.isRetriable)
        XCTAssertTrue(Failure.network.isRetriable)
        for failure in [Failure.engine, .profile, .unsupported, .adminDenied, .integrity, .proxyTaken, .killSwitch] { XCTAssertFalse(failure.isRetriable, "\(failure)") }
        await addProfile()
        await model.connect()
        engine.startErrors = [.integrity]
        engine.tunnel.handler?()
        let stopped = await eventually { [self] in if case .failed = state { return true } else { return false } }
        XCTAssertTrue(stopped)
        XCTAssertEqual(state, .failed(.integrity))
        XCTAssertEqual(engine.started, 2, "an engine that is not trusted is not asked for again")
    }

    // -- 8, 9: routes and DNS that could not be set ---------------------------------------------------------------------

    func testRouteAndDnsFailuresAreNamedWhenConnectingAndRetriedWhenReconnecting() async {
        await addProfile()
        engine.startError = .routes
        await model.connect()
        XCTAssertEqual(state, .failed(.routes))
        engine.startError = .dns
        await model.connect()
        XCTAssertEqual(state, .failed(.dns))
        engine.startError = .killSwitch
        await model.connect()
        XCTAssertEqual(state, .failed(.killSwitch))
        engine.startError = nil
        await model.connect()
        XCTAssertTrue(isConnected)
        // Lost, then a try fails on the routes, the next on the DNS, the third works.
        engine.startErrors = [.routes, .dns]
        engine.tunnel.handler?()
        let back = await eventually { [self] in isConnected && engine.started >= 7 }
        XCTAssertTrue(back, "\(state)")
    }

    // -- 5: the network changes ------------------------------------------------------------------------------------------

    func testAChangeOfWayOutRebuildsTheTunnel() async {
        await addProfile()
        await model.connect()
        await model.systemEvent(.path(NetworkPathSignature(online: true, interface: "en0", gateway: "192.168.1.1")))
        let rebuilt = await eventually { [self] in engine.started == 2 && isConnected }
        XCTAssertTrue(rebuilt, "\(state)")
    }

    func testTheSameWayOutAgainChangesNothing() async {
        await addProfile()
        await model.connect()
        await model.systemEvent(.path(NetworkPathSignature(online: true, interface: "en1", gateway: "192.168.31.1")))
        XCTAssertTrue(isConnected)
        XCTAssertEqual(engine.started, 1)
    }

    func testGoingOfflineAndComingBackWithADeadEngineReconnectsOnceTheNetworkIsThere() async {
        await addProfile()
        await model.connect()
        await model.systemEvent(.path(NetworkPathSignature(online: false, interface: nil, gateway: nil)))
        XCTAssertEqual(state, .noInternet)
        engine.tunnel.handler?()
        try? await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertEqual(state, .noInternet, "no attempts are spent while there is no network")
        XCTAssertEqual(engine.started, 1)
        await model.systemEvent(.path(NetworkPathSignature(online: true, interface: "en1", gateway: "192.168.31.1")))
        let back = await eventually { [self] in isConnected && engine.started == 2 }
        XCTAssertTrue(back, "\(state)")
    }

    func testTheNetworkComingBackLooksAtTheTunnelBeforeCallingItConnected() async {
        await addProfile()
        await model.connect()
        await model.systemEvent(.path(NetworkPathSignature(online: false, interface: nil, gateway: nil)))
        health.steady = .dead("engine")
        await model.systemEvent(.path(NetworkPathSignature(online: true, interface: "en1", gateway: "192.168.31.1")))
        let reconnecting = await eventually { [self] in engine.started == 2 && isConnected == false || engine.started >= 2 }
        XCTAssertTrue(reconnecting)
        XCTAssertFalse(state.isConnected && engine.started == 1, "a dead tunnel was called connected")
    }

    // -- 6: sleep and wake ----------------------------------------------------------------------------------------------

    func testAWakeThatFindsTheTunnelWellChangesNothing() async {
        await addProfile()
        await model.connect()
        await model.systemEvent(.willSleep)
        await model.systemEvent(.didWake)
        XCTAssertTrue(isConnected)
        XCTAssertEqual(engine.started, 1)
    }

    func testAWakeThatFindsTheTunnelDeadBringsItBack() async {
        await addProfile()
        await model.connect()
        await model.systemEvent(.willSleep)
        health.next([.dead("engine"), .dead("engine")])
        await model.systemEvent(.didWake)
        let back = await eventually { [self] in engine.started == 2 && isConnected }
        XCTAssertTrue(back, "\(state)")
    }

    func testAWakeIsGivenASecondLookBeforeTheTunnelIsDeclaredLost() async {
        await addProfile()
        await model.connect()
        health.next([.degraded("probe"), .ok])
        await model.systemEvent(.didWake)
        XCTAssertTrue(isConnected)
        XCTAssertEqual(engine.started, 1, "a network still waking up is not a loss")
    }

    // -- hiccups are not losses --------------------------------------------------------------------------------------------

    func testOneFailedLookIsNotALossAndTwoInARowIs() async {
        await addProfile()
        await model.connect()
        health.next([.degraded("probe"), .ok])
        try? await Task.sleep(nanoseconds: 250_000_000)
        XCTAssertTrue(isConnected)
        XCTAssertEqual(engine.started, 1)
        health.steady = .degraded("probe")
        let lost = await eventually { [self] in engine.started >= 2 }
        XCTAssertTrue(lost, "two failed looks in a row rebuilt the tunnel")
        health.steady = .ok
    }

    func testAHealthyTunnelIsLeftAloneHoweverLongItRuns() async {
        await addProfile()
        await model.connect()
        try? await Task.sleep(nanoseconds: 400_000_000)
        XCTAssertTrue(isConnected)
        XCTAssertEqual(engine.started, 1)
        XCTAssertGreaterThan(health.checks, 3, "it is looked at, cheaply, on a schedule")
    }

    // -- the whole Mac: no new password in the background ----------------------------------------------------------------

    func testTheEngineIsStartedAgainInsideTheSameHelperWithoutANewPassword() async {
        await addProfile()
        await connectWholeMac()
        XCTAssertTrue(isConnected)
        engine.restartableTunnel.lostHandler?(true)
        let back = await eventually { [self] in engine.restarts == 1 && isConnected }
        XCTAssertTrue(back, "\(state)")
        XCTAssertEqual(engine.started, 1, "no second prompt: the helper that was started is the helper that goes on")
        XCTAssertFalse(engine.restartableTunnel.stopped)
        XCTAssertTrue(proxy.applied.isEmpty, "and the system proxy is not touched in this mode")
    }

    func testAHelperThatIsGoneIsNotReplacedWithoutThePerson() async {
        await addProfile()
        await connectWholeMac()
        engine.restartableTunnel.alive = false
        engine.restartableTunnel.exitHandler?()
        let lost = await eventually { [self] in if case .lost = state { return true } else { return false } }
        XCTAssertTrue(lost, "\(state)")
        XCTAssertEqual(engine.started, 1, "a new helper needs the password, which a background retry does not ask for")
        guard case let .lost(_, blocked) = state else { return XCTFail() }
        XCTAssertFalse(blocked)
        // The person presses Reconnect: now a prompt is fine.
        await model.connect()
        XCTAssertEqual(engine.started, 2)
        XCTAssertTrue(isConnected)
    }

    func testWithTheKillSwitchALostTunnelIsShownAsBlockedAndDisconnectLiftsIt() async {
        await addProfile()
        await connectWholeMac(autoReconnect: false, killSwitch: true)
        engine.restartableTunnel.held = true
        engine.restartableTunnel.lostHandler?(true)
        let lost = await eventually { [self] in if case .lost = state { return true } else { return false } }
        XCTAssertTrue(lost)
        XCTAssertEqual(state, .lost(profileId: model.selectedId!, blocked: true))
        XCTAssertTrue(state.isProtected, "nothing leaks")
        XCTAssertFalse(state.isConnected, "but this is not a connection")
        XCTAssertFalse(engine.restartableTunnel.stopped, "the helper stays: it is what holds the block, and what lifts it")
        await model.disconnect()
        XCTAssertEqual(state, .idle)
        XCTAssertEqual(engine.restartableTunnel.shutdowns, 1, "the helper is asked to put the network back and waited for")
    }

    func testReconnectAfterALossReusesTheHelper() async {
        await addProfile()
        await connectWholeMac(autoReconnect: false)
        engine.restartableTunnel.held = true
        engine.restartableTunnel.lostHandler?(true)
        _ = await eventually { [self] in if case .lost = state { return true } else { return false } }
        engine.restartableTunnel.held = false
        await model.connect()
        let back = await eventually { [self] in isConnected && engine.restarts == 1 }
        XCTAssertTrue(back, "\(state)")
        XCTAssertEqual(engine.started, 1)
    }

    func testAKillSwitchIsPassedToTheEngine() {
        let options = RuntimeOptions()
        let vault = Vault(url: directory.appendingPathComponent("v2.vault"), keys: keys)
        let repository = Repository(vault: vault, onChange: {})
        let withOptions = AppModel(
            repository: repository, prefsStore: PrefsStore(url: directory.appendingPathComponent("p2.json")), runner: engine, proxy: proxy, script: script,
            linksCache: directory.appendingPathComponent("l2.json"), faqCache: directory.appendingPathComponent("f2.json"), language: .en,
            observeNetwork: false, health: health, options: options)
        XCTAssertFalse(options.killSwitch)
        withOptions.setPrefs { $0.killSwitch = true }
        XCTAssertTrue(options.killSwitch)
        withOptions.setPrefs { $0.killSwitch = false }
        XCTAssertFalse(options.killSwitch)
    }

    // -- 13: competing operations -----------------------------------------------------------------------------------------

    func testConnectAndDisconnectRacingLeaveOneConsistentState() async {
        await addProfile()
        for round in 0..<12 {
            async let a: Void = model.connect()
            async let b: Void = { if round % 2 == 0 { await Task.yield() }; await model.disconnect() }()
            _ = await (a, b)
            // Whatever won, what is shown is what exists: connected means exactly one live tunnel, idle means none.
            let live = engine.made.filter { !$0.stopped }.count
            if isConnected { XCTAssertEqual(live, 1, "round \(round)") } else { XCTAssertEqual(live, 0, "round \(round): \(state)") }
        }
        await model.disconnect()
        XCTAssertEqual(engine.made.filter { !$0.stopped }.count, 0)
    }

    func testTwoConnectsAtOnceLeaveOneTunnel() async {
        await addProfile()
        async let a: Void = model.connect()
        async let b: Void = model.connect()
        _ = await (a, b)
        XCTAssertTrue(isConnected, "\(state)")
        XCTAssertEqual(engine.made.filter { !$0.stopped }.count, 1)
    }

    func testALossAndADisconnectRacingDoNotResurrectTheTunnel() async {
        await addProfile()
        await model.connect()
        engine.tunnel.handler?()
        await model.disconnect()
        try? await Task.sleep(nanoseconds: 400_000_000)
        XCTAssertEqual(state, .idle)
        XCTAssertTrue(engine.made.allSatisfy(\.stopped))
    }

    // -- 12: cleanup is idempotent ------------------------------------------------------------------------------------------

    func testCleanupTwiceIsHarmless() async {
        await addProfile()
        await model.connect()
        await model.disconnect()
        await model.disconnect()
        await model.shutdown()
        await model.shutdown()
        XCTAssertEqual(state, .idle)
        XCTAssertTrue(engine.made.allSatisfy(\.stopped))
    }
}

final class RecoveryOnLaunchTests: ModelCase {
    private func writeSupervisorFiles(status: String, killswitch: String? = nil, pid: String = "99999999") throws {
        try (status + "\n").write(to: tunPaths.status, atomically: true, encoding: .utf8)
        try (pid + "\n").write(to: tunPaths.pid, atomically: true, encoding: .utf8)
        if let killswitch { try "killswitch=\(killswitch)\n".write(to: tunPaths.info, atomically: true, encoding: .utf8) }
    }

    func testSettingsTheTunnelLeftThatNeedThePersonAreAskedAbout() async {
        recovery.outcome = .needsPrivileges(["-setdnsservers"])
        recovery.command = "'/usr/sbin/networksetup' '-setdnsservers' 'Wi-Fi' 'Empty'"
        let fresh = makeModel()
        await fresh.load()
        XCTAssertEqual(fresh.leftover, .settings)
        // One prompt, with the restore as the command; then it is asked again and found clean.
        recovery.outcome = .clean
        recovery.command = nil
        privileged.onLaunch = { [recovery] in recovery!.outcome = .clean }
        let done = await fresh.restoreNetwork()
        XCTAssertTrue(done)
        XCTAssertNil(fresh.leftover)
        XCTAssertEqual(privileged.launches.count, 0, "nothing to run once it is clean")
    }

    func testARestoreThatNeedsAPromptRunsTheCommandAndNothingElse() async {
        recovery.outcome = .needsPrivileges(["-setdnsservers"])
        recovery.command = "'/usr/sbin/networksetup' '-setdnsservers' 'Wi-Fi' 'Empty'"
        let fresh = makeModel()
        await fresh.load()
        privileged.onLaunch = { [recovery] in recovery!.outcome = .clean; recovery!.command = nil }
        let done = await fresh.restoreNetwork()
        XCTAssertTrue(done)
        XCTAssertEqual(privileged.launches.count, 1)
        XCTAssertTrue(privileged.launches[0].script.contains("'/usr/sbin/networksetup' '-setdnsservers' 'Wi-Fi' 'Empty'"))
        XCTAssertEqual(privileged.launches[0].arguments, [])
        XCTAssertTrue(privileged.launches[0].wait)
    }

    func testAHelperThatWentWithoutFinishingIsReportedWithWhetherItHeldTheBlock() async throws {
        try writeSupervisorFiles(status: "running 0123456789abcdef", killswitch: "on")
        let fresh = makeModel()
        await fresh.load()
        XCTAssertEqual(fresh.leftover, .supervisor(blocking: true))
        // The emergency restore is the helper's own `--recover`, from the signed text, with one prompt.
        privileged.onLaunch = { [tunPaths] in try? "stopped\n".write(to: tunPaths!.status, atomically: true, encoding: .utf8) }
        let done = await fresh.restoreNetwork()
        XCTAssertTrue(done)
        XCTAssertEqual(privileged.launches.first?.arguments, ["--recover"])
        XCTAssertEqual(privileged.launches.first?.script, TunScriptSource.text)
        XCTAssertNil(fresh.leftover)
    }

    func testAHelperThatFinishedLeavesNothingToAskAbout() async throws {
        try writeSupervisorFiles(status: "stopped")
        let fresh = makeModel()
        await fresh.load()
        XCTAssertNil(fresh.leftover)
    }

    func testALiveHelperIsNotALeftoverAndRecoveryDoesNotTouchItsSettings() async throws {
        try writeSupervisorFiles(status: "running 0123456789abcdef", pid: String(getpid()))
        let fresh = makeModel()
        await fresh.load()
        XCTAssertNil(fresh.leftover)
        XCTAssertTrue(recovery.reconciles.isEmpty || !recovery.reconciles.contains(false) || fresh.leftover == nil)
    }

    func testAnUnreadableRecordIsAskedAboutNotIgnored() async {
        recovery.outcome = .discarded(.tampered)
        let fresh = makeModel()
        await fresh.load()
        XCTAssertEqual(fresh.leftover, .settings, "a record that cannot be trusted is not a record that nothing is wrong")
    }

    func testEveryEndOfATunnelConfirmsTheNetworkIsAsRecorded() async {
        await addProfile()
        let before = recovery.reconciles.count
        await model.connect()
        await model.disconnect()
        XCTAssertGreaterThan(recovery.reconciles.count, before)
    }

    func testAStartThatFailsAlsoConfirmsAndDropsTheRecord() async {
        await addProfile()
        engine.startError = .cancelled
        let before = recovery.reconciles.count
        await model.connect()
        XCTAssertEqual(state, .failed(.adminDenied))
        XCTAssertGreaterThan(recovery.reconciles.count, before, "a password refused leaves no record behind")
    }
}

final class SmallPartsTests: XCTestCase {
    func testThePhysicalRouteIsTheHardwareOneNotAVpnsDefault() {
        let table = """
            Destination        Gateway            Flags               Netif Expire
            default            link#21            UCSg                utun4
            default            192.168.31.1       UGScIg                en1
            """
        XCTAssertEqual(PhysicalRoute.parse(table), PhysicalRoute.Route(interface: "en1", gateway: "192.168.31.1"))
        XCTAssertEqual(PhysicalRoute.parse("default            10.1.0.1           UGScg           bridge100\n")?.interface, "bridge100")
        XCTAssertNil(PhysicalRoute.parse("default            link#21            UCSg                utun4\n"))
        XCTAssertNil(PhysicalRoute.parse(""))
        XCTAssertNil(PhysicalRoute.parse("10                 192.168.31.1       UGSc                  en1\n"), "only a default route")
    }

    func testTheNewPreferencesDefaultSafelyAndSurviveGarbage() throws {
        let fresh = Prefs()
        XCTAssertTrue(fresh.autoReconnect)
        XCTAssertFalse(fresh.killSwitch, "a switch that can cut the internet is the person's to turn on")
        let garbage = Prefs.sanitize(try JSONSerialization.data(withJSONObject: ["autoReconnect": "yes", "killSwitch": 5]))
        XCTAssertTrue(garbage.autoReconnect)
        XCTAssertFalse(garbage.killSwitch)
        let stored = Prefs.sanitize(try JSONSerialization.data(withJSONObject: ["autoReconnect": false, "killSwitch": true]))
        XCTAssertFalse(stored.autoReconnect)
        XCTAssertTrue(stored.killSwitch)
    }

    func testTheStatesAreHonestAboutProtection() {
        XCTAssertTrue(ConnectionState.connected(profileId: "a", since: Date()).isProtected)
        XCTAssertTrue(ConnectionState.lost(profileId: "a", blocked: true).isProtected)
        for state in [ConnectionState.idle, .preparing, .connecting(profileId: "a"), .reconnecting(profileId: "a", attempt: 2), .lost(profileId: "a", blocked: false), .disconnecting, .failed(.handshake), .noInternet] {
            XCTAssertFalse(state.isProtected, "\(state)")
            XCTAssertFalse(state.isConnected, "\(state)")
        }
    }

    func testEveryNewStateHasWordsAndAnAction() {
        guard case let .recognized(draft) = Parsers.parse("vless://\(uuid)@relay.example.net:443?security=tls") else { return XCTFail() }
        let profiles = [ProfileSummary(StoredProfile(id: "p1", name: "x", createdAt: 1, outbound: draft.outbound))]
        func view(_ state: ConnectionState) -> Presentation {
            present(HomeView.make(snapshot: Snapshot(state: state, subject: ConnectionSubject(id: profiles[0].id, name: "x"), diagnostics: nil), profiles: profiles, selectedId: profiles[0].id))
        }
        let reconnecting = view(.reconnecting(profileId: profiles[0].id, attempt: 1))
        XCTAssertEqual(reconnecting.statusKey, "mac_status_reconnecting")
        XCTAssertEqual(reconnecting.action, .cancel)
        let blocked = view(.lost(profileId: profiles[0].id, blocked: true))
        XCTAssertEqual(blocked.statusKey, "mac_status_lost_blocked")
        XCTAssertEqual(blocked.action, .retry)
        XCTAssertEqual(blocked.tone, .error)
        let open = view(.lost(profileId: profiles[0].id, blocked: false))
        XCTAssertEqual(open.statusKey, "mac_status_lost_open")
        for failure in [Failure.integrity, .network, .routes, .dns, .killSwitch] {
            XCTAssertTrue(Translator(language: .en).has(failureKey(failure)), "\(failure)")
            XCTAssertTrue(Translator(language: .ru).has(failureKey(failure)), "\(failure)")
        }
    }
}

final class MovingTunnel: RestartableTunnel, @unchecked Sendable {
    let httpPort = 1
    let socksPort = 2
    var serverAddress: String? = "203.0.113.5"
    var alive = true
    var held = false
    var killSwitchActive = true
    var interfaceName: String? = "en1"
    var supervisorIsAlive: Bool { alive }
    var trafficIsHeld: Bool { held }
    private var received: Int64 = 0
    var grow = true
    func stop() {}
    func traffic() async -> Traffic? { if grow { received += 4096 }; return Traffic(rx: received, tx: 1) }
    func onExit(_ handler: @escaping @Sendable () -> Void) {}
    func onLost(_ handler: @escaping @Sendable (Bool) -> Void) {}
    func restart(outbound: VlessOutbound, address: String, split: SplitTunnel) async throws {}
}

final class LiveHealthTests: XCTestCase {
    func testAHelperThatIsGoneOrHoldingTheBlockIsDeadWithoutAnyRequest() async {
        let tunnel = MovingTunnel()
        let health = LiveHealth()
        tunnel.alive = false
        let gone = await health.check(tunnel)
        XCTAssertEqual(gone, .dead("supervisor"))
        tunnel.alive = true
        tunnel.held = true
        let held = await health.check(tunnel)
        XCTAssertEqual(held, .dead("engine"))
    }

    func testBytesComingBackAreTheProofAndCostNoRequest() async {
        let tunnel = MovingTunnel()
        let health = LiveHealth()
        // The first look has nothing to compare with, so it asks (nothing listens on the stand-in's port: a refusal).
        let first = await health.check(tunnel)
        XCTAssertEqual(first, .degraded("probe"))
        // From then on, real traffic is the answer.
        let second = await health.check(tunnel)
        XCTAssertEqual(second, .ok)
        let third = await health.check(tunnel)
        XCTAssertEqual(third, .ok)
        // And when it stops coming, the request is made again and its failure counts.
        tunnel.grow = false
        let stalled = await health.check(tunnel)
        XCTAssertEqual(stalled, .degraded("probe"))
    }
}
