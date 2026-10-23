package app.vazie.vpn.runtime

import app.vazie.vpn.api.TrafficStats
import kotlinx.coroutines.flow.Flow

/** The three questions the controller asks Android, each behind an interface it can fake. */
internal interface NetworkStatus {
    /** Whether a validated non-VPN network exists right now. */
    fun hasUsableUnderlyingNetwork(): Boolean

    /** [hasUsableUnderlyingNetwork], re-answered from network callbacks whenever it changes. What a live
     * tunnel watches instead of asking every second. */
    fun observeUsableUnderlyingNetwork(): Flow<Boolean>
}

internal interface ReadinessProbe {
    /** Whether the tunnel can actually carry a user's traffic, and where it stops if it cannot. */
    suspend fun probe(): TunnelReadiness
}

/** The outcome of the readiness check. */
internal sealed interface TunnelReadiness {

    data object Ready : TunnelReadiness

    data class NotReady(val stage: ReadinessStage) : TunnelReadiness
}

/** In the order they are attempted; the first failure is the one reported. */
/** What has to be true before a tunnel is called `Connected`, in the order it is asked. */
internal enum class ReadinessStage { TLS, DNS }

internal interface TrafficSource {
    /** Remembers where the OS counters stood, so a session reports its own bytes. */
    fun start()

    /** Bytes moved since [start]. */
    fun read(): TrafficStats
}
