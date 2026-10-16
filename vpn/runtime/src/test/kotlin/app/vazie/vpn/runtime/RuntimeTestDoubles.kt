package app.vazie.vpn.runtime

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.EngineHost
import app.vazie.vpn.api.EngineStartResult
import app.vazie.vpn.api.EngineSupport
import app.vazie.vpn.api.Endpoint
import app.vazie.vpn.api.CidrAddress
import app.vazie.vpn.api.ProfileDetails
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.TunOptions
import app.vazie.vpn.api.TunSpec
import app.vazie.vpn.api.TunnelAttachment
import app.vazie.vpn.api.TunnelStrategy
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnEngine
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Everything the controller talks to, in memory. */
internal const val PROFILE_ID = "profile-1"
internal const val OTHER_PROFILE_ID = "profile-2"

internal fun testProfile(id: String = PROFILE_ID, name: String = "Test relay"): VpnProfile.Xray =
    VpnProfile.Xray(
        id = ProfileId(id),
        name = name,
        origin = ProfileOrigin.IMPORTED_LINK,
        createdAt = Instant.EPOCH,
        outbound = XrayOutbound.Vless(
            userId = Secret.of("00000000-0000-4000-8000-000000000000"),
            endpoint = Endpoint("relay.example.net", 443),
            security = XraySecurity.None,
            transport = XrayTransport.Tcp(),
        ),
    )

internal class FakeProfileRepository(profiles: List<VpnProfile> = listOf(testProfile())) :
    VpnProfileRepository {

    var stored: List<VpnProfile> = profiles

    override fun observeSummaries(): Flow<List<ProfileSummary>> = MutableStateFlow(emptyList())

    override suspend fun create(draft: ProfileDraft, name: String, origin: ProfileOrigin): ProfileId =
        error("not used")

    override suspend fun details(id: ProfileId): ProfileDetails? = null

    override suspend fun revealCredential(id: ProfileId): Secret<String>? = null

    override suspend fun profile(id: ProfileId): VpnProfile? = stored.firstOrNull { it.id == id }

    override suspend fun delete(id: ProfileId) = Unit

    /** All four are loud, and that is the assertion. */
    override suspend fun rename(id: ProfileId, name: String) = error("the runtime does not write profiles")

    override suspend fun duplicate(id: ProfileId, name: String): ProfileId? =
        error("the runtime does not write profiles")


    override suspend fun markUsed(id: ProfileId) = error("the runtime does not write profiles")
}

internal class FakeEngine(
    private val support: EngineSupport = EngineSupport.Supported,
    private val startResult: EngineStartResult = EngineStartResult.Started,
) : VpnEngine {

    /** Set by a test that wants `start` to hang, so a disconnect can be raced against a connection that is
     * still in progress. */
    var startGate: CompletableDeferred<Unit>? = null

    var startCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set
    var lastDescriptor: Int? = null
        private set

    override val id: VpnEngineId get() = VpnEngineId.XRAY

    override fun supports(profile: VpnProfile): EngineSupport = support

    override fun tunnelStrategy(profile: VpnProfile): TunnelStrategy = TunnelStrategy.EngineOwnsTun

    override fun tunSpec(profile: VpnProfile, options: TunOptions): TunSpec = TunSpec(
        sessionName = options.sessionName,
        mtu = 1500,
        addresses = listOf(CidrAddress("10.10.10.1", 32)),
        routes = listOf(CidrAddress("0.0.0.0", 0), CidrAddress("::", 0)),
        dnsServers = listOf("1.1.1.1"),
    )

    override suspend fun start(
        profile: VpnProfile,
        attachment: TunnelAttachment,
        host: EngineHost,
    ): EngineStartResult {
        startCalls++
        lastDescriptor = (attachment as TunnelAttachment.Tun).descriptor
        startGate?.await()
        return startResult
    }

    override suspend fun stop() {
        stopCalls++
    }

    override fun isRunning(): Boolean = startCalls > stopCalls
}

internal class FakeTunnelLauncher(
    var consentMissing: Boolean = false,
) : TunnelLauncher {

    var startCalls: Int = 0
        private set

    /** Set by a test that wants the service to arrive by itself. */
    var onStart: (() -> Unit)? = null

    override fun consentRequired(): Boolean = consentMissing

    /** Set by a test that wants Android to refuse the foreground start. */
    var startSucceeds: Boolean = true

    override fun start(): Boolean {
        startCalls++
        if (!startSucceeds) return false
        onStart?.invoke()
        return true
    }
}

/** A host that behaves like the real service in the one way that matters: **shutting it down calls back.** */
internal class FakeTunnelHost(private val establishes: Boolean = true) : TunnelHost {

    val openDescriptors = AtomicInteger(0)
    var shutdownCalls: Int = 0
        private set

    /** Set by the harness to `controller::onHostGone`, the way Android sets `onDestroy`. */
    var onShutdown: ((TunnelHost) -> Unit)? = null
    var published: MutableList<VpnConnectionSnapshot> = mutableListOf()
        private set

    /** The last interface the runtime asked for. */
    var lastSpec: TunSpec? = null
        private set

    override fun establish(spec: TunSpec): TunnelHandle? {
        lastSpec = spec
        if (!establishes) return null
        openDescriptors.incrementAndGet()
        return object : TunnelHandle {
            override val descriptor: Int = 7
            private var closed = false
            override fun close() {
                if (closed) return
                closed = true
                openDescriptors.decrementAndGet()
            }
        }
    }

    override fun protect(socketFd: Int): Boolean = true

    override fun resolveIpv4(hostname: String): String? = "203.0.113.7"

    override fun publish(snapshot: VpnConnectionSnapshot) {
        published += snapshot
    }

    override fun shutdown() {
        shutdownCalls++
        onShutdown?.invoke(this)
    }
}

/** The underlying network, switchable by a test. */
internal class FakeNetworkStatus(underlyingAvailable: Boolean = true) : NetworkStatus {
    private val available = MutableStateFlow(underlyingAvailable)

    /** Flipping it is also what a live tunnel's network callback hears. */
    var underlyingAvailable: Boolean
        get() = available.value
        set(value) {
            available.value = value
        }

    override fun hasUsableUnderlyingNetwork(): Boolean = available.value

    override fun observeUsableUnderlyingNetwork(): Flow<Boolean> = available
}

/** A readiness check a test can break at a chosen stage. */
internal class FakeReadinessProbe(
    var failAt: ReadinessStage? = null,
) : ReadinessProbe {

    constructor(passes: Boolean) : this(if (passes) null else ReadinessStage.TLS)

    /** Set by a test that wants the readiness check to be still running, so a disconnect can be raced against
     * it. */
    var hangUntil: CompletableDeferred<Unit>? = null

    var calls: Int = 0
        private set

    override suspend fun probe(): TunnelReadiness {
        calls++
        hangUntil?.await()
        return failAt?.let { TunnelReadiness.NotReady(it) } ?: TunnelReadiness.Ready
    }
}

internal class FakeTrafficSource : TrafficSource {
    var startCalls: Int = 0
        private set

    override fun start() {
        startCalls++
    }

    var readCalls: Int = 0
        private set

    override fun read(): TrafficStats {
        readCalls++
        return TrafficStats(rxBytes = 1024, txBytes = 512)
    }
}
