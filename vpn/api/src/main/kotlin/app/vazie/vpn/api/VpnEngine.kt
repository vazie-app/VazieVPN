package app.vazie.vpn.api

import app.vazie.vpn.core.model.SplitTunnel

/** What an engine can be asked to do, and nothing about how Android does it. */
interface VpnEngine {

    val id: VpnEngineId

    /** Whether this engine can run [profile], and which part of it it cannot if it cannot. */
    fun supports(profile: VpnProfile): EngineSupport

    /** How this engine wants to be attached to the tunnel. Asked before the TUN is opened. */
    fun tunnelStrategy(profile: VpnProfile): TunnelStrategy

    /** What the TUN interface must look like for [profile]. Asked before the TUN is opened. */
    fun tunSpec(profile: VpnProfile, options: TunOptions): TunSpec

    /** Starts the engine against [attachment] and returns once it is running or has failed. */
    suspend fun start(
        profile: VpnProfile,
        attachment: TunnelAttachment,
        host: EngineHost,
    ): EngineStartResult

    /** Stops the engine and releases everything it holds. Safe to call when it never started. */
    suspend fun stop()

    /** Whether the engine believes itself to be running. */
    fun isRunning(): Boolean
}

/** What `:vpn:runtime` offers an engine in return. */
fun interface EngineHost {
    /** Keeps the socket behind [socketFd] outside the tunnel. `false` when Android refused. */
    fun protect(socketFd: Int): Boolean

    /** Resolves the VPN server on a validated non-VPN network and returns a literal IPv4 address. The runtime
     * implementation uses DNS-over-HTTPS; `null` is a fail-closed answer. */
    fun resolveIpv4(hostname: String): String? = null
}

/** Whether an engine can run a profile. */
sealed interface EngineSupport {

    data object Supported : EngineSupport

    /** @param feature the part of the configuration this engine cannot execute. */
    data class Unsupported(val feature: UnsupportedRuntimeFeature) : EngineSupport
}

/** Whether an engine came up. */
sealed interface EngineStartResult {

    data object Started : EngineStartResult

    /** [reason] is a fixed word, never an engine message built from the configuration. */
    data class Failed(val reason: EngineStartFailure) : EngineStartResult
}

enum class EngineStartFailure {
    /** The profile could not be turned into a runtime configuration. */
    CONFIGURATION,

    /** The native binding is missing or refused to load. */
    NATIVE_LIBRARY,

    /** The engine loaded, took the configuration and did not come up. */
    RUNTIME,
}

/** How an engine wants to be attached to the tunnel. */
sealed interface TunnelStrategy {

    /** The engine takes the TUN descriptor and runs its own IP stack on it. */
    data object EngineOwnsTun : TunnelStrategy
}

/** What the runtime handed the engine. */
sealed interface TunnelAttachment {

    /** [descriptor] is the open TUN fd; the runtime owns and closes it, never the engine. */
    data class Tun(val descriptor: Int) : TunnelAttachment
}

/** What the runtime tells the engine about the tunnel it is about to build. */
data class TunOptions(
    /** The label in Android's VPN settings; product copy from the runtime, never profile material. */
    val sessionName: String,
)

/** The interface the runtime must build for this engine to work. */
data class TunSpec(
    /** Shown by Android in the VPN settings entry. Never contains profile material. */
    val sessionName: String,
    val mtu: Int,
    val addresses: List<CidrAddress>,
    /** Destinations pulled into the tunnel. */
    val routes: List<CidrAddress>,
    /** Resolvers handed to the apps on the device. */
    val dnsServers: List<String>,
    /** Which apps use the tunnel; set by the runtime from the person's choice, never by an engine. */
    val splitTunnel: SplitTunnel = SplitTunnel(),
) {
    init {
        require(mtu > 0) { "TunSpec mtu must be positive" }
        require(addresses.isNotEmpty()) { "a TUN interface needs at least one address" }
        require(routes.isNotEmpty()) { "a TUN interface with no route carries nothing" }
    }
}

/** An address with a prefix length: `10.0.0.2/32`, `::/0`. */
data class CidrAddress(val address: String, val prefixLength: Int) {
    init {
        require(address.isNotBlank()) { "CidrAddress address must not be blank" }
        require(prefixLength >= 0) { "CidrAddress prefix length must not be negative" }
    }

    override fun toString(): String = "$address/$prefixLength"
}
