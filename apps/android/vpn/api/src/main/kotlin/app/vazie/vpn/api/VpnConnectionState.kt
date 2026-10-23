package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import java.time.Instant

/** What the tunnel is actually doing, owned by `:vpn:runtime` and read by everyone else. */
sealed interface VpnConnectionState {

    /** No tunnel, and nobody asked for one. */
    data object Idle : VpnConnectionState

    /** Consent has been requested and the system dialog is up, or the service is being started. */
    data object Preparing : VpnConnectionState

    /** TUN is up or coming up and the engine is starting. */
    data class Connecting(val profileId: ProfileId) : VpnConnectionState

    /** TUN established, engine started, packet path live. Nothing sets this because a coroutine was launched
     * — see `ConnectionReadiness`. */
    data class Connected(val profileId: ProfileId, val since: Instant) : VpnConnectionState

    data object Disconnecting : VpnConnectionState

    data class Failed(val failure: ConnectionFailure) : VpnConnectionState

    /** No validated network underneath; connecting cannot succeed and is not attempted. */
    data object NoInternet : VpnConnectionState
}

/** The profile a state refers to, when it refers to one. Lets a caller ask the question without a `when` over
 * seven cases it otherwise does not care about. */
val VpnConnectionState.profileId: ProfileId?
    get() = when (this) {
        is VpnConnectionState.Connecting -> profileId
        is VpnConnectionState.Connected -> profileId
        else -> null
    }

/** True while the runtime holds or is building a tunnel. */
val VpnConnectionState.isActive: Boolean
    get() = when (this) {
        VpnConnectionState.Preparing,
        is VpnConnectionState.Connecting,
        is VpnConnectionState.Connected,
        VpnConnectionState.Disconnecting,
        -> true

        VpnConnectionState.Idle,
        is VpnConnectionState.Failed,
        VpnConnectionState.NoInternet,
        -> false
    }
