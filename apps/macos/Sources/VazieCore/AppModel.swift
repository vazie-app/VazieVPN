import Combine
import Foundation

/// Serves the proxy auto-config script of a split tunnel.
public protocol ScriptServing: AnyObject, Sendable {
    func start() async throws
    func update(_ script: String)
    var url: String { get }
    func stop()
}

extension PACServer: ScriptServing {}

/// The app's one object: it owns the vault, the preferences and the tunnel, answers the interface's calls, and tells
/// every view whenever anything changes. This is `VazieVpnConnectionController` for a Mac: the "TUN" is the engine's
/// loopback proxy plus the system proxy settings that send the Mac's apps to it.
@MainActor
public final class AppModel: ObservableObject {
    @Published public private(set) var snapshot: Snapshot = .idle
    @Published public private(set) var profiles: [ProfileSummary] = []
    @Published public private(set) var prefs = Prefs()
    @Published public private(set) var engine: EngineStatus = .unknown
    @Published public private(set) var traffic: Traffic?
    @Published public private(set) var links: AppLinks?
    @Published public private(set) var latencies: [String: Int?] = [:]
    @Published public private(set) var loaded = false
    @Published public private(set) var online = true
    /// The VPN apps connected when the proxy turned out to be somebody else's, for the message that names them.
    @Published public private(set) var conflictingVPNs: [String] = []
    @Published public private(set) var otherVPNs: [String] = []
    @Published public private(set) var language: Language
    /// What a previous run left on this Mac that the person has to be asked about (see `restoreNetwork`).
    @Published public private(set) var leftover: Leftover?

    public enum Leftover: Equatable, Sendable {
        /// DNS or IPv6 still as the tunnel set them, and the way back needs the administrator.
        case settings
        /// The helper that ran the tunnel went without finishing; with `blocking`, it was holding the Mac's traffic.
        case supervisor(blocking: Bool)
    }

    private let repository: Repository
    private let prefsStore: PrefsStore
    private let runner: TunnelEngine
    private let tunRunner: TunnelEngine
    private let proxy: SystemProxyControlling
    private let script: ScriptServing
    private let subscriptions: SubscriptionFetcher
    private let linksDocument: PublishedDocument<AppLinks>
    private let faqDocument: PublishedDocument<Backend.Faq>
    private let engineLocator: @Sendable () -> URL?
    private let connectedVPNs: @Sendable () async -> [String]
    private let health: TunnelHealthChecking
    private let events: SystemEventSource?
    private let recovery: NetworkRecovering?
    private let privileged: PrivilegedRunner
    private let tunPaths: TunPaths
    private let routeProvider: @Sendable () async -> PhysicalRoute.Route?
    private let options: RuntimeOptions?
    private let healthTick: UInt64
    private let healthEvery: Int
    private let wakeSettle: TimeInterval
    public var policy: ReconnectPolicy

    private var generation = 0
    /// The person wants the tunnel up: set by Connect, cleared by Disconnect. Nothing reconnects without it.
    private var intent = false
    private var reconnectTask: Task<Void, Never>?
    private var lastSignature: NetworkPathSignature?
    private var tunnel: Tunnel?
    private var connectedSince: Date?
    private var subject: ConnectionSubject?
    private var diagnostics: Diagnostics?
    private var watchers = 0
    private var trafficTask: Task<Void, Never>?
    private var latencyTask: Task<Void, Never>?
    private var installTask: Task<Void, Never>?
    private var healthTask: Task<Void, Never>?
    private var currentPlan: ProxyPlan?

    public init(
        repository: Repository, prefsStore: PrefsStore, runner: TunnelEngine = XrayEngine(), tunRunner: TunnelEngine? = nil,
        proxy: SystemProxyControlling = SystemProxy(),
        script: ScriptServing = PACServer(), subscriptions: SubscriptionFetcher = SubscriptionFetcher(),
        linksCache: URL = Paths.linksCache, faqCache: URL = Paths.faqCache, language: Language = .of(),
        engineLocator: @escaping @Sendable () -> URL? = { Engine.binary() }, observeNetwork: Bool = true,
        connectedVPNs: @escaping @Sendable () async -> [String] = { await OtherVPN.connected() },
        health: TunnelHealthChecking = LiveHealth(), events: SystemEventSource? = nil, recovery: NetworkRecovering? = nil,
        privileged: PrivilegedRunner = AdministratorPrompt(), tunPaths: TunPaths = .system, options: RuntimeOptions? = nil,
        routeProvider: @escaping @Sendable () async -> PhysicalRoute.Route? = { await PhysicalRoute.current() },
        policy: ReconnectPolicy = .standard, healthTick: TimeInterval = 3, healthEvery: Int = 6, wakeSettle: TimeInterval = 2
    ) {
        self.repository = repository
        self.prefsStore = prefsStore
        self.runner = runner
        self.tunRunner = tunRunner ?? runner
        self.proxy = proxy
        self.script = script
        self.subscriptions = subscriptions
        self.engineLocator = engineLocator
        self.connectedVPNs = connectedVPNs
        self.health = health
        self.recovery = recovery
        self.privileged = privileged
        self.tunPaths = tunPaths
        self.options = options
        self.routeProvider = routeProvider
        self.policy = policy
        self.healthTick = UInt64(max(0.01, healthTick) * 1_000_000_000)
        self.healthEvery = max(1, healthEvery)
        self.wakeSettle = wakeSettle
        self.language = language
        linksDocument = PublishedDocument(cache: linksCache, path: "/apps/vpn/links", askWhileEmpty: false) { Backend.readLinks($0) }
        faqDocument = PublishedDocument(cache: faqCache, path: "/faq/vpn", askWhileEmpty: true) { Backend.readFaq($0) }
        let source = events ?? (observeNetwork ? MacSystemEvents() : nil)
        self.events = source
        source?.start { [weak self] event in Task { @MainActor in await self?.systemEvent(event) } }
    }

    /// The production wiring, with the files where the app keeps them.
    public static func standard() -> AppModel {
        let vault = Vault(url: Paths.vault, keys: KeychainKeyStore())
        var model: AppModel!
        let repository = Repository(vault: vault, onChange: { Task { @MainActor in await model?.reloadProfiles() } })
        // `VAZIE_PROXY_SERVICES="iPhone USB"` narrows which network services are touched: a testing aid, so that a
        // development build can be tried on a service that carries no traffic.
        let only = ProcessInfo.processInfo.environment["VAZIE_PROXY_SERVICES"].map { Set($0.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }) }
        let language = Language.of()
        let options = RuntimeOptions()
        let recovery = NetworkRecovery(store: NetworkSnapshotStore(url: Paths.networkSnapshot, keys: KeychainKeyStore()))
        let tun = TunEngine(prompt: { Translator(language: language).t("mac_admin_prompt") }, killSwitch: { options.killSwitch }, recovery: recovery)
        model = AppModel(
            repository: repository, prefsStore: PrefsStore(url: Paths.prefs), tunRunner: tun, proxy: SystemProxy(only: only),
            recovery: recovery, options: options)
        return model
    }

    public var t: Translator { Translator(language: language) }

    // -- loading -----------------------------------------------------------------------------------------

    public func load() async {
        if loaded { return }
        // A previous run that died with the system proxy changed leaves a record; it is put back before anything else.
        if proxy.needsRecovery() { await proxy.restore() }
        OrphanedEngine.clean()
        prefs = prefsStore.load()
        options?.killSwitch = prefs.killSwitch
        await checkLeftovers()
        await reloadProfiles()
        links = linksDocument.current()
        loaded = true
        await refreshEngine()
    }

    func reloadProfiles() async {
        profiles = await repository.summaries()
        if let id = prefs.selectedId, !profiles.contains(where: { $0.id == id }) { prefs.selectedId = nil }
    }

    /// The selection, unless the profile it names is gone.
    public var selectedId: String? {
        prefs.selectedId.flatMap { id in profiles.contains(where: { $0.id == id }) ? id : nil }
    }

    public func refreshLinks() async {
        await linksDocument.refresh { [weak self] in Task { @MainActor in self?.links = self?.linksDocument.value } }
        links = linksDocument.value
    }

    public func faq(force: Bool) async -> (faq: Backend.Faq?, loading: Bool) {
        await faqDocument.refresh(force: force)
        return (faqDocument.value, faqDocument.loading)
    }

    // -- the engine --------------------------------------------------------------------------------------

    public func refreshEngine() async {
        if case .installing = engine { return }
        guard let binary = engineLocator() else {
            engine = .missing
            return
        }
        engine = .ready(version: await Engine.runningVersion(of: binary) ?? Engine.version.trimmingCharacters(in: CharacterSet(charactersIn: "v")))
    }

    /// Downloads and verifies the pinned engine. Returns whether it is there afterwards.
    @discardableResult
    public func installEngine() async -> Bool {
        if case .installing = engine { return false }
        engine = .installing(done: 0, total: 0)
        do {
            try await Engine.install { [weak self] done, total in
                Task { @MainActor in
                    if case .installing = self?.engine { self?.engine = .installing(done: done, total: total) }
                }
            }
            engine = .unknown
            await refreshEngine()
            return true
        } catch {
            engine = .missing
            return false
        }
    }

    // -- preferences -------------------------------------------------------------------------------------

    public func setPrefs(_ change: (inout Prefs) -> Void) {
        var next = prefs
        change(&next)
        // Through the same checks as a stored file: nothing but a known value is kept.
        guard let data = try? JSONEncoder().encode(next) else { return }
        next = Prefs.sanitize(data)
        guard next != prefs else { return }
        prefs = next
        options?.killSwitch = next.killSwitch
        prefsStore.save(prefs)
    }

    public func select(_ id: String) { setPrefs { $0.selectedId = id } }

    public func setSplit(_ split: SplitTunnel) async {
        setPrefs { $0.split = split }
        await reapplySplit()
    }

    /// The sites list changed while a tunnel is up: the Mac is told at once, with no new connection.
    private func reapplySplit() async {
        // Whole-Mac mode reads the split from the engine's own configuration: it applies at the next connect.
        guard let tunnel, snapshot.state.isConnected, currentPlan != nil else { return }
        let plan = await plan(for: tunnel)
        try? await proxy.apply(plan)
        currentPlan = plan
    }

    private func plan(for tunnel: Tunnel) async -> ProxyPlan {
        let split = prefs.split
        guard split.isActive else { return .direct(httpPort: tunnel.httpPort, socksPort: tunnel.socksPort) }
        script.update(split.pacScript(httpPort: tunnel.httpPort))
        do {
            try await script.start()
        } catch {
            // Already running: its address is the one in use.
        }
        return .script(url: script.url)
    }

    // -- connect / disconnect ----------------------------------------------------------------------------

    private func publish(_ state: ConnectionState, _ subject: ConnectionSubject?, _ diagnostics: Diagnostics? = nil) {
        snapshot = Snapshot(state: state, subject: subject, diagnostics: diagnostics)
    }

    private func fail(_ failure: Failure) {
        stopTraffic()
        traffic = nil
        publish(.failed(failure), nil)
    }

    private enum Attempt { case connected, failed(Failure), offline, superseded, needsPassword }

    private var currentProfileId: String? { snapshot.state.profileId ?? snapshot.subject?.id }

    /// Whether the supervisor of a lost whole-Mac tunnel still stands, so that the engine can be started again in it without
    /// asking for the password.
    private var canRestartInPlace: Bool {
        prefs.mode == .wholeMac && (tunnel as? RestartableTunnel)?.supervisorIsAlive == true
    }

    public func connect(_ profileId: String? = nil) async {
        guard let id = profileId ?? selectedId else { return }
        if profileId != nil { select(id) }
        // A tunnel that was lost and whose helper still stands is brought back where it is, with no new password.
        if case let .lost(lostId, _) = snapshot.state, lostId == id, canRestartInPlace {
            resumeReconnect()
            return
        }
        await runConnect(id)
    }

    private func runConnect(_ id: String) async {
        generation += 1
        let mine = generation
        intent = true
        cancelReconnect()
        await teardown()
        let result = await establish(id, generation: mine, attempt: nil)
        guard mine == generation else { return }
        if case let .failed(failure) = result { fail(failure) }
    }

    /// Builds a tunnel for a configuration and proves it. `attempt` is set when this is a reconnection, whose state is
    /// already on screen. The result says what happened; the caller decides what to show.
    private func establish(_ id: String, generation mine: Int, attempt: Int?) async -> Attempt {
        func alive() -> Bool { mine == generation }

        guard let profile = await repository.profile(id) else { return .failed(.profile) }
        let subject = ConnectionSubject(id: profile.id, name: profile.name)

        // Reported before anything is started, so a configuration Vazie cannot run never touches the Mac's settings.
        if Support.check(profile.outbound) != nil { return .failed(.unsupported) }
        if !online {
            publish(.noInternet, subject)
            return .offline
        }

        if attempt == nil { publish(.preparing, subject) }
        guard engineLocator() != nil else { return .failed(.engine) }

        let mode = prefs.mode
        let active = mode == .wholeMac ? tunRunner : runner
        // Two tunnels cannot both be the Mac's way out: another VPN that is connected is named before anything is opened.
        if mode == .wholeMac {
            let others = await connectedVPNs()
            if !others.isEmpty {
                conflictingVPNs = others
                return .failed(.proxyTaken)
            }
        }

        if attempt == nil { publish(.connecting(profileId: id), subject) }
        let started: Tunnel
        do {
            started = try await active.start(profile.outbound, split: prefs.split)
        } catch let error as TunnelStartError {
            await settleRecovery()
            return alive() ? .failed(Failure(error)) : .superseded
        } catch {
            await settleRecovery()
            return alive() ? .failed(.unknown) : .superseded
        }
        if !alive() {
            started.stop()
            return .superseded
        }
        tunnel = started
        wire(started)

        // Proven before the Mac is pointed at it: an unusable tunnel must not take the Mac's traffic with it.
        if await active.readiness(of: started) != nil {
            guard alive() else { return .superseded }
            await teardown()
            return .failed(.tunnelUnusable)
        }
        if !alive() { return .superseded }

        // The Mac's programs follow the system proxy only in that mode; in the other the interface takes them.
        var proxyPlan: ProxyPlan?
        if mode == .systemProxy {
            let wanted = await plan(for: started)
            proxyPlan = wanted
            do {
                try await proxy.apply(wanted)
            } catch {
                guard alive() else { return .superseded }
                await teardown()
                return .failed(.systemProxy)
            }
            if !alive() {
                await proxy.restore()
                return .superseded
            }

            // What was written is not what counts: the settings the Mac's programs see must be these, or "connected"
            // would be a statement about a tunnel nothing uses.
            if !(await takesEffect(wanted)) {
                guard alive() else { return .superseded }
                conflictingVPNs = await connectedVPNs()
                await teardown()
                return .failed(.proxyTaken)
            }
            if !alive() {
                await proxy.restore()
                return .superseded
            }
        }

        if let failure = await confirm(started, active: active) {
            guard alive() else { return .superseded }
            await teardown()
            return .failed(failure)
        }
        if !alive() {
            await proxy.restore()
            return .superseded
        }

        await markConnected(profile, subject: subject, generation: mine, proxyPlan: proxyPlan)
        return .connected
    }

    /// The proofs that count once a tunnel is up: a request made the way a program makes one is seen by the engine (with a
    /// split the request may be meant to go direct, so there is nothing to ask of it), and, for the whole Mac, that the
    /// routes into the tunnel are the ones in effect. `nil` when all hold.
    private func confirm(_ started: Tunnel, active: TunnelEngine) async -> Failure? {
        if !prefs.split.isActive, !(await active.carriesSystemTraffic(started)) {
            conflictingVPNs = await connectedVPNs()
            return .proxyTaken
        }
        if prefs.mode == .wholeMac, case .dead = await health.check(started) { return .tunBypassed }
        return nil
    }

    /// "Connected" is said here and nowhere else: only after the proofs above.
    private func markConnected(_ profile: StoredProfile, subject: ConnectionSubject, generation mine: Int, proxyPlan: ProxyPlan?) async {
        let since = Date()
        connectedSince = since
        currentPlan = proxyPlan
        self.subject = subject
        diagnostics = Diagnostics(profile: profile)
        if let route = await routeProvider() { lastSignature = NetworkPathSignature(online: true, interface: route.interface, gateway: route.gateway) }
        publish(.connected(profileId: profile.id, since: since), subject, diagnostics)
        try? await repository.markUsed(profile.id)
        await reloadProfiles()
        syncTraffic()
        measureLatency(profile, generation: mine)
        startHealth(generation: mine)
    }

    /// What the tunnel tells the model about itself: the helper or the engine ended, or the engine alone was lost.
    private func wire(_ started: Tunnel) {
        started.onExit { [weak self, weak started] in
            Task { @MainActor in
                guard let self, let started else { return }
                await self.tunnelEnded(started)
            }
        }
        (started as? RestartableTunnel)?.onLost { [weak self, weak started] _ in
            Task { @MainActor in
                guard let self, let started else { return }
                await self.tunnelLost(started)
            }
        }
    }

    /// The plan is in effect, allowing the system a moment to publish what was written.
    private func takesEffect(_ plan: ProxyPlan) async -> Bool {
        for _ in 0..<10 {
            if proxy.isInEffect(plan) { return true }
            try? await Task.sleep(nanoseconds: 200_000_000)
        }
        return false
    }

    public func disconnect() async {
        intent = false
        generation += 1
        cancelReconnect()
        let subject = snapshot.subject
        if case .idle = snapshot.state, subject == nil { return }
        if connectedSince != nil || !isFailed { publish(.disconnecting, subject) }
        await teardown()
        publish(.idle, nil)
    }

    private var isFailed: Bool {
        if case .failed = snapshot.state { return true }
        return false
    }

    /// The system settings first, so the Mac never points at a tunnel that is going away; then the engine, until the
    /// helper says the network is back as it was.
    private func teardown() async {
        stopTraffic()
        latencyTask?.cancel()
        healthTask?.cancel()
        healthTask = nil
        currentPlan = nil
        traffic = nil
        connectedSince = nil
        subject = nil
        diagnostics = nil
        await proxy.restore()
        script.stop()
        let ending = tunnel
        tunnel = nil
        await ending?.shutdown()
        await settleRecovery()
    }

    /// Quitting: the Mac's settings go back as they were and the engine ends, before the process does.
    public func shutdown() async {
        intent = false
        generation += 1
        events?.stop()
        cancelReconnect()
        await teardown()
    }

    // -- losing and regaining the tunnel -------------------------------------------------------------------------------

    private func tunnelEnded(_ ended: Tunnel) async {
        guard tunnel === ended, intent, connectedSince != nil else { return }
        await handleLoss(tunnelGone: true)
    }

    private func tunnelLost(_ lost: Tunnel) async {
        guard tunnel === lost, intent, connectedSince != nil else { return }
        await handleLoss(tunnelGone: false)
    }

    /// Whether the Mac's traffic is being held back by a helper whose engine is gone (the kill switch), or is stuck in a
    /// tunnel interface whose engine cannot reach the server: in neither does it leave directly.
    private func trafficIsHeld() -> Bool {
        guard prefs.mode == .wholeMac, let restartable = tunnel as? RestartableTunnel, restartable.supervisorIsAlive else { return false }
        if restartable.trafficIsHeld { return true }
        return (restartable as? TunSession)?.currentStatus().kind == .running
    }

    /// The tunnel stopped carrying traffic. With Auto Reconnect it is brought back; without, the loss is shown and the
    /// Mac is left in the state it is honestly in.
    private func handleLoss(tunnelGone: Bool) async {
        guard intent, let id = currentProfileId, reconnectTask == nil else { return }
        if case .reconnecting = snapshot.state { return }
        generation += 1
        let mine = generation
        healthTask?.cancel()
        healthTask = nil
        stopTraffic()
        traffic = nil
        connectedSince = nil
        if prefs.autoReconnect {
            startReconnect(mine, id)
        } else {
            await settleLost(id, tunnelGone: tunnelGone)
        }
    }

    /// Shows `lost`. A whole-Mac helper that stands stays (so Reconnect needs no password, and Disconnect can lift a
    /// block); anything else is taken down, which puts the Mac's settings back.
    private func settleLost(_ id: String, tunnelGone: Bool) async {
        let subject = snapshot.subject
        if prefs.mode == .wholeMac, !tunnelGone, let restartable = tunnel as? RestartableTunnel, restartable.supervisorIsAlive {
            publish(.lost(profileId: id, blocked: trafficIsHeld()), subject)
        } else {
            await teardown()
            publish(.lost(profileId: id, blocked: false), subject)
        }
    }

    private func cancelReconnect() {
        reconnectTask?.cancel()
        reconnectTask = nil
    }

    /// Resets the count and tries again, for a person's Reconnect, a network that came back, or a Mac that woke.
    private func resumeReconnect() {
        guard intent, let id = currentProfileId else { return }
        generation += 1
        cancelReconnect()
        startReconnect(generation, id)
    }

    private func startReconnect(_ mine: Int, _ id: String) {
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in await self?.reconnectLoop(mine, id) }
    }

    private func sleep(_ seconds: TimeInterval) async {
        if seconds > 0 { try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000)) }
    }

    private func waitUntilOnline(_ mine: Int) async {
        while !Task.isCancelled, mine == generation, !online { await sleep(0.25) }
    }

    private func reconnectLoop(_ mine: Int, _ id: String) async {
        var attempt = 0
        while !Task.isCancelled, mine == generation, intent {
            if !online {
                // Waiting for a network is not an attempt, and the count starts again when one is there.
                publish(.noInternet, snapshot.subject)
                await waitUntilOnline(mine)
                attempt = 0
                continue
            }
            attempt += 1
            if attempt > policy.maxAttempts {
                guard mine == generation else { return }
                await settleLost(id, tunnelGone: false)
                reconnectTask = nil
                return
            }
            publish(.reconnecting(profileId: id, attempt: attempt), snapshot.subject, snapshot.diagnostics)
            await sleep(policy.delay(forAttempt: attempt, unit: Double.random(in: 0...1)))
            guard !Task.isCancelled, mine == generation, intent else { return }
            guard online else { continue }
            switch await reconnectOnce(id, generation: mine, attempt: attempt) {
            case .connected, .superseded:
                if mine == generation { reconnectTask = nil }
                return
            case .offline:
                continue
            case .needsPassword:
                // A new helper needs the password, which a retry in the background must not ask for.
                guard mine == generation else { return }
                publish(.lost(profileId: id, blocked: false), snapshot.subject)
                reconnectTask = nil
                return
            case let .failed(failure):
                guard mine == generation else { return }
                if failure.isRetriable { continue }
                await teardown()
                fail(failure)
                reconnectTask = nil
                return
            }
        }
    }

    /// One attempt. A whole-Mac tunnel whose helper stands has its engine started again in it, for the network as it is
    /// now; anything else is built anew.
    private func reconnectOnce(_ id: String, generation mine: Int, attempt: Int) async -> Attempt {
        if prefs.mode == .wholeMac, let current = tunnel {
            guard let restartable = current as? RestartableTunnel, restartable.supervisorIsAlive else {
                await teardown()
                return .needsPassword
            }
            guard let profile = await repository.profile(id) else { return .failed(.profile) }
            if Support.check(profile.outbound) != nil { return .failed(.unsupported) }
            do {
                if try await tunRunner.restart(current, outbound: profile.outbound, split: prefs.split) {
                    guard mine == generation else { return .superseded }
                    if await tunRunner.readiness(of: current) != nil { return .failed(.tunnelUnusable) }
                    if let failure = await confirm(current, active: tunRunner) { return .failed(failure) }
                    guard mine == generation else { return .superseded }
                    await markConnected(profile, subject: ConnectionSubject(id: profile.id, name: profile.name), generation: mine, proxyPlan: nil)
                    return .connected
                }
            } catch let error as TunnelStartError {
                return mine == generation ? .failed(Failure(error)) : .superseded
            } catch {
                return mine == generation ? .failed(.unknown) : .superseded
            }
        }
        await teardown()
        return await establish(id, generation: mine, attempt: attempt)
    }

    // -- watching a tunnel that is up ----------------------------------------------------------------------------------

    /// While connected: another app taking the proxy over is noticed and said, and the tunnel is looked at now and then
    /// (the helper and the engine, the routes, a small request, or the engine's own counters moving). A single failed
    /// request is not a loss; two in a row, or a tunnel that is plainly gone, is.
    private func startHealth(generation mine: Int) {
        healthTask?.cancel()
        let tick = healthTick
        let every = healthEvery
        healthTask = Task { [weak self] in
            var proxyMisses = 0
            var degraded = 0
            var ticks = 0
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: tick)
                guard let self, mine == self.generation, self.snapshot.state.isConnected, let tunnel = self.tunnel else { continue }
                if let plan = self.currentPlan {
                    proxyMisses = self.proxy.isInEffect(plan) ? 0 : proxyMisses + 1
                    if proxyMisses >= 2 {
                        self.conflictingVPNs = await self.connectedVPNs()
                        self.generation += 1
                        await self.teardown()
                        self.fail(.proxyTaken)
                        return
                    }
                }
                ticks += 1
                guard ticks % every == 0 else { continue }
                switch await self.health.check(tunnel) {
                case .ok:
                    degraded = 0
                case .degraded:
                    degraded += 1
                    if degraded >= 2 {
                        await self.handleLoss(tunnelGone: false)
                        return
                    }
                case .dead:
                    await self.handleLoss(tunnelGone: false)
                    return
                }
            }
        }
    }

    // -- the Mac's network and power -----------------------------------------------------------------------------------

    /// The Mac's network came or went; only a live tunnel has anything to say.
    func networkChanged(online now: Bool) {
        online = now
        switch snapshot.state {
        case .connected where !now:
            snapshot.state = .noInternet
            stopTraffic()
        case .noInternet where now:
            guard intent, tunnel != nil, connectedSince != nil else { break }
            // Back online: what is true is said only after looking.
            Task { await verifyAfterReturn() }
        default:
            break
        }
    }

    /// The network came back under a tunnel that was up: it is looked at before it is called connected again.
    private func verifyAfterReturn() async {
        guard intent, let tunnel, let since = connectedSince, case .noInternet = snapshot.state, let id = currentProfileId else { return }
        var verdict = await health.check(tunnel)
        if verdict != .ok {
            await sleep(1)
            verdict = await health.check(tunnel)
        }
        guard intent, case .noInternet = snapshot.state, self.tunnel === tunnel else { return }
        if verdict == .ok {
            publish(.connected(profileId: id, since: since), snapshot.subject, snapshot.diagnostics)
            syncTraffic()
        } else {
            await handleLoss(tunnelGone: false)
        }
    }

    /// Sleep, wake and a change of network, from the system.
    public func systemEvent(_ event: SystemEvent) async {
        switch event {
        case .willSleep:
            break
        case .didWake:
            guard intent else { return }
            switch snapshot.state {
            case .connected, .noInternet:
                // The network takes a moment to come back after a wake; then the tunnel is looked at.
                await sleep(wakeSettle)
                guard intent, let tunnel, snapshot.state.isConnected || { if case .noInternet = snapshot.state { return true } else { return false } }() else { return }
                var verdict = await health.check(tunnel)
                if verdict != .ok {
                    await sleep(1.5)
                    verdict = await health.check(tunnel)
                }
                if verdict != .ok { await handleLoss(tunnelGone: false) }
            case .lost:
                if prefs.autoReconnect { resumeReconnect() }
            default:
                break
            }
        case let .path(signature):
            if signature.online != online { networkChanged(online: signature.online) }
            guard signature.online else { return }
            let previous = lastSignature
            lastSignature = signature
            guard intent, let previous, previous.online, let before = previous.interface, let after = signature.interface,
                before != after || previous.gateway != signature.gateway
            else { return }
            // The way out moved (Wi-Fi to Ethernet, another router): what was built on the old one is not trusted.
            switch snapshot.state {
            case .connected:
                await handleLoss(tunnelGone: false)
            case .noInternet where connectedSince != nil:
                await handleLoss(tunnelGone: false)
            case .lost:
                if prefs.autoReconnect { resumeReconnect() }
            default:
                break
            }
        }
    }

    // -- what a previous run left ---------------------------------------------------------------------------------------

    /// Confirms the Mac's DNS and IPv6 are as recorded once a tunnel is over (or failed to begin), and forgets the record.
    private func settleRecovery() async {
        guard let recovery else { return }
        let alive = TunLeftovers.inspect(paths: tunPaths).supervisorAlive
        _ = await recovery.reconcile(supervisorAlive: alive)
    }

    /// At launch: a helper that went without finishing, or settings the tunnel left, are put right if that can be done
    /// quietly and asked about if it cannot.
    private func checkLeftovers() async {
        let report = TunLeftovers.inspect(paths: tunPaths)
        if report.supervisorAlive { return }
        var found: Leftover?
        if let recovery {
            switch await recovery.reconcile(supervisorAlive: false) {
            case .needsPrivileges, .discarded: found = .settings
            default: break
            }
        }
        if report.unfinished { found = .supervisor(blocking: report.wasBlocking) }
        leftover = found
    }

    /// The person asked for the Mac's network to be put back: one administrator prompt.
    @discardableResult
    public func restoreNetwork() async -> Bool {
        var done = true
        if case .supervisor = leftover {
            done = await TunLeftovers.restore(runner: privileged, prompt: t.t("mac_recovery_prompt"))
        }
        if done, let command = await recovery?.privilegedCommand() {
            switch await privileged.launch(script: "#!/bin/sh\n\(command)\n", arguments: [], wait: true, prompt: t.t("mac_recovery_prompt")) {
            case .finished(0), .started: break
            default: done = false
            }
        }
        await checkLeftovers()
        return done && leftover == nil
    }

    private func measureLatency(_ profile: StoredProfile, generation mine: Int) {
        latencyTask?.cancel()
        latencyTask = Task { [runner] in
            let ms = await runner.latency(host: profile.outbound.endpoint.host, port: profile.outbound.endpoint.port)
            await MainActor.run {
                guard mine == self.generation, var d = self.diagnostics else { return }
                d.latencyMs = ms
                self.diagnostics = d
                self.snapshot.diagnostics = d
            }
        }
    }

    // -- diagnostics ---------------------------------------------------------------------------------------

    /// Runs the connection check against the live tunnel; empty when there is none.
    public func runDiagnostics() async -> [Diagnose.Row] {
        guard let tunnel, snapshot.state.isConnected else { return [] }
        return await Diagnose.run(httpPort: tunnel.httpPort)
    }

    /// The VPN apps connected right now, for the warning on Home.
    public func refreshOtherVPNs() async { otherVPNs = await connectedVPNs() }

    // -- traffic -----------------------------------------------------------------------------------------

    /// How many views are showing the counters. They are read only while somebody is looking at them.
    public func beginWatching() {
        watchers += 1
        syncTraffic()
    }

    public func endWatching() {
        watchers = max(0, watchers - 1)
        syncTraffic()
    }

    private func syncTraffic() {
        let wanted = snapshot.state.isConnected && watchers > 0
        if wanted && trafficTask == nil {
            trafficTask = Task { [weak self] in
                while !Task.isCancelled {
                    await self?.readTraffic()
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                }
            }
        } else if !wanted {
            stopTraffic()
        }
    }

    private func stopTraffic() {
        trafficTask?.cancel()
        trafficTask = nil
    }

    private func readTraffic() async {
        guard snapshot.state.isConnected, let tunnel else { return }
        let reading = await tunnel.traffic()
        if snapshot.state.isConnected { traffic = reading }
    }

    // -- configurations ----------------------------------------------------------------------------------

    public func createFromLink(_ link: String, name: String) async -> String? {
        // Read again here: what the screen sends is a candidate, not a configuration.
        guard case let .recognized(draft) = Parsers.parse(link), !name.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        guard let id = try? await repository.create(draft, name: name) else { return nil }
        // A newly imported configuration becomes the selected one.
        await reloadProfiles()
        select(id)
        return id
    }

    public func fetchSubscription(_ link: String) async -> SubscriptionFetchResult { await subscriptions.fetch(link) }

    public func importSubscription(_ token: String) async -> [String] {
        guard let drafts = await subscriptions.take(token), let ids = try? await repository.createMany(drafts) else { return [] }
        await reloadProfiles()
        // The list's first entry is the one its provider put first; that is the one to arm.
        if let first = ids.first { select(first) }
        return ids
    }

    public func discardSubscription(_ token: String) async { await subscriptions.discard(token) }

    public func details(_ id: String) async -> ProfileDetails? { await repository.details(id) }

    public func revealCredential(_ id: String) async -> String? { await repository.profile(id)?.outbound.userId }

    /// The share link, for exactly one hand-off.
    public func exportLink(_ id: String) async -> String? {
        guard let profile = await repository.profile(id) else { return nil }
        return VlessSerializer.serialize(profile.outbound, name: profile.name)
    }

    public func rename(_ id: String, to name: String) async {
        try? await repository.rename(id, to: name)
        await reloadProfiles()
    }

    public func duplicate(_ id: String, name: String) async -> String? {
        let copy = try? await repository.duplicate(id, name: name)
        await reloadProfiles()
        return copy ?? nil
    }

    public func remove(_ id: String) async {
        if snapshot.state.profileId == id { await disconnect() }
        try? await repository.remove(id)
        await reloadProfiles()
    }

    /// Round-trip time per configuration, `nil` for no answer.
    public func measureLatencies() async {
        let all = await withTaskGroup(of: (String, Int?)?.self, returning: [String: Int?].self) { group in
            var result: [String: Int?] = [:]
            var pending = profiles.makeIterator()
            func next() async -> Bool {
                guard let summary = pending.next(), let stored = await repository.profile(summary.id) else { return false }
                let runner = self.runner
                group.addTask {
                    (stored.id, await runner.latency(host: stored.outbound.endpoint.host, port: stored.outbound.endpoint.port))
                }
                return true
            }
            var running = 0
            while running < 6, await next() { running += 1 }
            while let finished = await group.next() {
                if let (id, ms) = finished { result[id] = .some(ms) }
                _ = await next()
            }
            return result
        }
        latencies = all
    }

    // -- helpers for the views ---------------------------------------------------------------------------

    public func setLanguage(_ language: Language) { self.language = language }
}
