package app.vazie.vpn.runtime

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectResult
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ConnectionDiagnostics
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.DnsMode
import app.vazie.vpn.api.EngineHost
import app.vazie.vpn.api.EngineStartFailure
import app.vazie.vpn.api.EngineStartResult
import app.vazie.vpn.api.EngineSupport
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.SplitTunnelSource
import app.vazie.vpn.api.TunOptions
import app.vazie.vpn.api.TunnelAttachment
import app.vazie.vpn.api.UnsupportedRuntimeFeature
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnEngine
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurityKind
import app.vazie.vpn.api.XrayTransport
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The tunnel: one at a time, owned here, described truthfully. */
class VazieVpnConnectionController internal constructor(
    private val profiles: VpnProfileRepository,
    private val engines: EngineRegistry,
    private val launcher: TunnelLauncher,
    private val network: NetworkStatus,
    private val probe: ReadinessProbe,
    private val traffic: TrafficSource,
    private val scope: CoroutineScope,
    /** Injectable so tests keep every coroutine on one scheduler. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC(),
    /** Which apps use the tunnel, read afresh for every connection. */
    private val splitTunnel: SplitTunnelSource = SplitTunnelSource.Off,
) : VpnConnectionController {

    private val _snapshot = MutableStateFlow(VpnConnectionSnapshot())
    override val snapshot: StateFlow<VpnConnectionSnapshot> = _snapshot.asStateFlow()

    private val mutex = Mutex()

    /** The session the controller is currently responsible for. Guarded by [mutex]. */
    private var current: Session? = null

    override suspend fun connect(profileId: ProfileId): ConnectResult {
        VpnTrace.record(VpnTraceEvent.CONNECT_REQUESTED)
        return when (val prepared = mutex.withLock { prepareLocked(profileId) }) {
            is Preparation.Rejected -> ConnectResult.Rejected(prepared.failure)
            Preparation.ConsentMissing -> ConnectResult.PermissionRequired
            is Preparation.Ready -> {
                if (launcher.start()) {
                    ConnectResult.Started
                } else {
                    // Android refused to start the service; without this the session stays in `Preparing` for
                    // ever.
                    abandonPreparedSession()
                    ConnectResult.Rejected(ConnectionFailure.ServiceStopped)
                }
            }
        }
    }

    override suspend fun canRun(profileId: ProfileId): Boolean {
        val profile = profiles.profile(profileId) ?: return false
        val engine = engines.forProfile(profile) ?: return false
        return engine.supports(profile) is EngineSupport.Supported
    }

    override suspend fun disconnect() {
        VpnTrace.record(VpnTraceEvent.DISCONNECT_REQUESTED)
        val session = mutex.withLock {
            val session = current
            current = null
            if (session != null) publish(VpnConnectionState.Disconnecting, session.subject)
            session
        }
        if (session == null) {
            // No session and no service; disconnecting from `Failed` still clears the error to `Idle`.
            mutex.withLock { publish(VpnConnectionState.Idle, subject = null) }
            return
        }
        session.job?.cancel()
        withContext(NonCancellable) { session.teardown() }
        mutex.withLock { publish(VpnConnectionState.Idle, subject = null) }
    }

    override suspend fun traffic(): TrafficStats {
        if (_snapshot.value.state !is VpnConnectionState.Connected) return TrafficStats.NONE
        return withContext(ioDispatcher) { traffic.read() }
    }

    // -- the Android service's side of the seam ------------------------------------------------

    /** The service is up and has a host. Called once per `onStartCommand(ACTION_CONNECT)`. */
    internal fun onHostAvailable(host: TunnelHost) {
        scope.launch {
            val session = mutex.withLock { current?.also { it.host = host } }
            VpnTrace.record(VpnTraceEvent.SERVICE_HOST, VpnTraceDetail.Flag(session != null))
            if (session == null) {
                host.shutdown()
                return@launch
            }
            val job = scope.launch { runSession(session, host) }
            mutex.withLock {
                if (current === session) session.job = job else job.cancel()
            }
        }
    }

    /** The service is gone. Whether that is a failure depends entirely on who asked. */
    internal fun onHostGone(host: TunnelHost) {
        scope.launch {
            val session = mutex.withLock {
                val session = current ?: return@withLock null
                if (session.host !== host) return@withLock null
                current = null
                session
            }
            VpnTrace.record(VpnTraceEvent.HOST_GONE, VpnTraceDetail.Flag(session != null))
            if (session == null) return@launch
            session.job?.cancel()
            withContext(NonCancellable) { session.teardown() }
            mutex.withLock {
                publish(VpnConnectionState.Failed(ConnectionFailure.ServiceStopped), subject = null)
            }
        }
    }

    /** The notification's Disconnect action, and anything else the service decides ends a session. */
    internal fun onStopRequested() {
        scope.launch { disconnect() }
    }

    // -- session ---------------------------------------------------------------------------------

    private suspend fun runSession(session: Session, host: TunnelHost) {
        try {
            mutex.withLock {
                if (current !== session) return
                publish(VpnConnectionState.Connecting(session.profile.id), session.subject)
            }
            host.publish(_snapshot.value)

            val spec = session.engine.tunSpec(
                profile = session.profile,
                options = TunOptions(sessionName = SESSION_NAME),
            ).copy(splitTunnel = splitTunnel.current())
            val handle = withContext(ioDispatcher) { host.establish(spec) }
            VpnTrace.record(VpnTraceEvent.TUN, VpnTraceDetail.Flag(handle != null))
            if (handle == null) {
                finish(session, ConnectionFailure.TunnelSetupFailed)
                return
            }
            val kept = mutex.withLock {
                if (current === session) {
                    session.handle = handle
                    true
                } else {
                    false
                }
            }
            if (!kept) {
                handle.close()
                return
            }

            VpnTrace.record(
                VpnTraceEvent.ENGINE_START,
                VpnTraceDetail.Outcome(VpnTraceOutcome.REQUESTED),
            )
            // Every engine socket passes through here: records whether protection was asked for and granted.
            val hostCurrencyRecorded = AtomicBoolean(false)
            val engineHost = object : EngineHost {
                override fun resolveIpv4(hostname: String): String? = host.resolveIpv4(hostname)

                override fun protect(socketFd: Int): Boolean {
                    VpnTrace.record(
                        VpnTraceEvent.SOCKET_PROTECT,
                        VpnTraceDetail.Outcome(VpnTraceOutcome.REQUESTED),
                    )
                    // Once per session: whether the engine's host reference is still this session's.
                    if (hostCurrencyRecorded.compareAndSet(false, true)) {
                        VpnTrace.record(
                            VpnTraceEvent.PROTECT_HOST_CURRENT,
                            VpnTraceDetail.Flag(session.host === host),
                        )
                    }
                    val protectedFd = host.protect(socketFd)
                    VpnTrace.record(
                        VpnTraceEvent.SOCKET_PROTECT,
                        VpnTraceDetail.Outcome(
                            if (protectedFd) VpnTraceOutcome.PASSED else VpnTraceOutcome.FAILED
                        ),
                    )
                    return protectedFd
                }
            }
            val started = session.engine.start(
                profile = session.profile,
                attachment = TunnelAttachment.Tun(handle.descriptor),
                host = engineHost,
            )
            VpnTrace.record(
                VpnTraceEvent.ENGINE_START,
                VpnTraceDetail.Outcome(
                    if (started is EngineStartResult.Failed) {
                        VpnTraceOutcome.FAILED
                    } else {
                        VpnTraceOutcome.PASSED
                    }
                ),
            )
            if (started is EngineStartResult.Failed) {
                finish(session, started.reason.toFailure(session.engine))
                return
            }
            VpnTrace.record(
                VpnTraceEvent.ENGINE_STATE,
                VpnTraceDetail.Flag(session.engine.isRunning()),
            )

            VpnTrace.record(
                VpnTraceEvent.READINESS_PROBE,
                VpnTraceDetail.Outcome(VpnTraceOutcome.REQUESTED),
            )
            val readiness = probe.probe()
            VpnTrace.record(
                VpnTraceEvent.READINESS_PROBE,
                VpnTraceDetail.Outcome(
                    if (readiness is TunnelReadiness.Ready) {
                        VpnTraceOutcome.PASSED
                    } else {
                        VpnTraceOutcome.FAILED
                    }
                ),
            )
            if (readiness is TunnelReadiness.NotReady) {
                // An unusable tunnel is not the device being offline; the network is re-checked for the
                // trace.
                VpnTrace.record(
                    VpnTraceEvent.UNDERLYING_NETWORK,
                    VpnTraceDetail.Flag(network.hasUsableUnderlyingNetwork()),
                )
                finish(session, ConnectionFailure.TunnelUnusable)
                return
            }

            val since = clock.instant()
            traffic.start()
            mutex.withLock {
                if (current !== session) return
                session.connectedSince = since
                publish(
                    state = VpnConnectionState.Connected(session.profile.id, since),
                    subject = session.subject,
                    diagnostics = session.diagnostics,
                )
            }
            host.publish(_snapshot.value)

            hold(session, host)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The throwable is not logged and not carried: an engine's exception message is built
            // out of the configuration that produced it.
            finish(session, ConnectionFailure.Unknown)
        }
    }

    /** Runs for as long as the tunnel does and tells the truth about the network underneath. */
    private suspend fun hold(session: Session, host: TunnelHost) {
        var online = true
        network.observeUsableUnderlyingNetwork().collect { nowOnline ->
            if (nowOnline == online) return@collect
            online = nowOnline
            // A session that is no longer current is being cancelled by whoever replaced it; it
            // must not get one last word in before that lands.
            val published = mutex.withLock {
                if (current !== session) return@withLock false
                val since = session.connectedSince ?: return@withLock false
                val state = if (nowOnline) {
                    VpnConnectionState.Connected(session.profile.id, since)
                } else {
                    VpnConnectionState.NoInternet
                }
                publish(
                    state = state,
                    subject = session.subject,
                    diagnostics = session.diagnostics,
                )
                true
            }
            if (published) host.publish(_snapshot.value)
        }
    }

    /** Ends [session] with a reason. */
    private suspend fun finish(session: Session, failure: ConnectionFailure) {
        mutex.withLock {
            if (current !== session) return
            current = null
        }
        withContext(NonCancellable) { session.teardown() }
        VpnTrace.record(VpnTraceEvent.FAILURE, VpnTraceDetail.Failure(failure.traceName()))
        mutex.withLock { publish(VpnConnectionState.Failed(failure), subject = null) }
    }

    // -- preparation -----------------------------------------------------------------------------

    private suspend fun prepareLocked(profileId: ProfileId): Preparation {
        val profile = profiles.profile(profileId)
            ?: return reject(ConnectionFailure.ProfileMissing)
        val subject = ConnectionSubject(profile.toSummary())
        val engine = engines.forProfile(profile)
            ?: return reject(
                ConnectionFailure.UnsupportedConfiguration(UnsupportedRuntimeFeature.ENGINE),
            )
        when (val support = engine.supports(profile)) {
            EngineSupport.Supported -> Unit
            is EngineSupport.Unsupported ->
                return reject(ConnectionFailure.UnsupportedConfiguration(support.feature))
        }
        val underlying = network.hasUsableUnderlyingNetwork()
        VpnTrace.record(VpnTraceEvent.UNDERLYING_NETWORK, VpnTraceDetail.Flag(underlying))
        if (!underlying) {
            publish(VpnConnectionState.NoInternet, subject = subject)
            return Preparation.Rejected(ConnectionFailure.NoInternet)
        }
        val consentRequired = launcher.consentRequired()
        VpnTrace.record(VpnTraceEvent.VPN_PERMISSION, VpnTraceDetail.Flag(!consentRequired))
        if (consentRequired) {
            // Not `Preparing`: nothing is being prepared until the user answers, and a screen
            // showing a spinner behind the system dialog is a screen that lies if they say no.
            publish(VpnConnectionState.Idle, subject = subject)
            return Preparation.ConsentMissing
        }

        current?.let { previous ->
            current = null
            previous.job?.cancel()
            withContext(NonCancellable) { previous.teardown() }
        }

        val session = Session(profile = profile, engine = engine, subject = subject)
        current = session
        publish(VpnConnectionState.Preparing, subject = session.subject)
        return Preparation.Ready
    }

    private suspend fun abandonPreparedSession() {
        val session = mutex.withLock {
            val session = current
            current = null
            session
        }
        if (session != null) withContext(NonCancellable) { session.teardown() }
        VpnTrace.record(
            VpnTraceEvent.FAILURE,
            VpnTraceDetail.Failure(ConnectionFailure.ServiceStopped.traceName()),
        )
        mutex.withLock {
            publish(VpnConnectionState.Failed(ConnectionFailure.ServiceStopped), subject = null)
        }
    }

    private fun reject(failure: ConnectionFailure): Preparation {
        publish(VpnConnectionState.Failed(failure), subject = null)
        return Preparation.Rejected(failure)
    }

    /** Every state change goes through here, which makes it the one place a transition can be traced without
     * scattering trace points through the logic that produces them. */
    private fun publish(
        state: VpnConnectionState,
        subject: ConnectionSubject?,
        diagnostics: ConnectionDiagnostics? = null,
    ) {
        val previous = _snapshot.value.state
        if (previous.traceName() != state.traceName()) {
            VpnTrace.record(
                VpnTraceEvent.STATE,
                VpnTraceDetail.Transition(previous.traceName(), state.traceName()),
            )
        }
        _snapshot.value = VpnConnectionSnapshot(
            state = state,
            subject = subject,
            diagnostics = diagnostics,
        )
    }

    private sealed interface Preparation {
        data object Ready : Preparation
        data object ConsentMissing : Preparation
        data class Rejected(val failure: ConnectionFailure) : Preparation
    }

    private class Session(
        val profile: VpnProfile,
        val engine: VpnEngine,
        val subject: ConnectionSubject,
    ) {

        var host: TunnelHost? = null
        var handle: TunnelHandle? = null
        var job: Job? = null
        var connectedSince: Instant? = null

        private val closed = AtomicBoolean(false)

        val diagnostics: ConnectionDiagnostics = profile.toDiagnostics()

        /** Idempotent, and it has to be: a disconnect racing a dying service tears the same session down
         * twice, and closing a descriptor number twice closes whatever was handed that number in between. */
        suspend fun teardown() {
            if (!closed.compareAndSet(false, true)) return
            // Traced: "asked to stop" and "let go of its sockets" are different facts.
            val stopped = runCatching { engine.stop() }.isSuccess
            VpnTrace.record(
                VpnTraceEvent.ENGINE_STOP,
                VpnTraceDetail.Outcome(
                    if (stopped) VpnTraceOutcome.PASSED else VpnTraceOutcome.FAILED
                ),
            )
            runCatching { handle?.close() }
            handle = null
            runCatching { host?.shutdown() }
            host = null
        }
    }

    private companion object {

        /** What Android shows in Settings → Network → VPN. A product name, never a profile name: this string
         * is rendered by system UI outside Vazie, where the user's own labels have no business being. */
        const val SESSION_NAME = "Vazie VPN"
    }
}

private fun EngineStartFailure.toFailure(engine: VpnEngine): ConnectionFailure = when (this) {
    EngineStartFailure.CONFIGURATION -> ConnectionFailure.ProfileInvalid(field = null)
    EngineStartFailure.NATIVE_LIBRARY -> ConnectionFailure.EngineFailed(engine.id)
    EngineStartFailure.RUNTIME -> ConnectionFailure.EngineFailed(engine.id)
}

private fun VpnProfile.toSummary(): ProfileSummary = ProfileSummary(
    id = id,
    name = name,
    engineId = engineId,
    protocolLabel = protocolLabel(),
)

private fun VpnProfile.protocolLabel(): String = when (this) {
    is VpnProfile.Xray -> when (outbound) {
        is XrayOutbound.Vless -> "VLESS"
    }
}

/** The read-out Console Mode may show, built once per session from the profile. */
private fun VpnProfile.toDiagnostics(): ConnectionDiagnostics = when (this) {
    is VpnProfile.Xray -> when (val outbound = outbound) {
        is XrayOutbound.Vless -> ConnectionDiagnostics(
            engine = "xray",
            protocol = "vless",
            security = when (outbound.security.kind) {
                XraySecurityKind.NONE -> "none"
                XraySecurityKind.TLS -> "tls"
                XraySecurityKind.REALITY -> "reality"
            },
            transport = when (outbound.transport) {
                is XrayTransport.Tcp -> "raw"
                is XrayTransport.WebSocket -> "ws"
                is XrayTransport.Grpc -> "grpc"
            },
            flow = outbound.flow?.wireName,
            endpointHost = outbound.endpoint.host,
            endpointPort = outbound.endpoint.port,
            dnsMode = DnsMode.TUNNEL,
            ipv4 = true,
            // False, and true: IPv6 is routed into the tunnel and then refused, so nothing IPv6
            // leaves the device — which is what a reader of this panel needs to know.
            ipv6 = false,
        )
    }
}
