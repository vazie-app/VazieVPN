package app.vazie.vpn.runtime

import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.VpnConnectionState

/** Everything a lifecycle trace is allowed to say. */
internal enum class VpnTraceEvent {
    CONNECT_REQUESTED,
    UNDERLYING_NETWORK,
    VPN_PERMISSION,
    SERVICE_HOST,
    TUN,
    ENGINE_START,
    ENGINE_STATE,
    ENGINE_STOP,
    READINESS_PROBE,
    TLS_PROBE,
    DNS_PROBE,
    STATE,
    FAILURE,
    DISCONNECT_REQUESTED,
    HOST_GONE,

    /** One outbound socket was offered to `VpnService.protect`, and what came back. */
    SOCKET_PROTECT,

    /** Whether the host that [SOCKET_PROTECT] reached is still the session's current one. */
    PROTECT_HOST_CURRENT,
}

/** `TIMED_OUT` is not a flavour of `FAILED`. "The stage answered no" and "the stage never answered" have
 * different causes and different fixes, and a trace that merged them is what made a hang read as silence. */
internal enum class VpnTraceOutcome { REQUESTED, PASSED, FAILED, TIMED_OUT }

/** [VpnConnectionState] reduced to a name. No case carries a value; see [traceName]. */
internal enum class VpnStateName {
    IDLE,
    PREPARING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED,
    NO_INTERNET,
}

/** [ConnectionFailure] reduced to a name. `ProfileInvalid`'s field name is dropped here too. */
internal enum class VpnFailureName {
    CONSENT_DENIED,
    NO_INTERNET,
    PROFILE_MISSING,
    PROFILE_INVALID,
    UNSUPPORTED_CONFIGURATION,
    TUNNEL_SETUP_FAILED,
    ENGINE_FAILED,
    TUNNEL_UNUSABLE,
    SERVICE_STOPPED,
    UNKNOWN,
}

internal sealed interface VpnTraceDetail {

    data object None : VpnTraceDetail

    /** `available=true`, `granted=false`, `established=true`. */
    data class Flag(val value: Boolean) : VpnTraceDetail

    data class Outcome(val value: VpnTraceOutcome) : VpnTraceDetail

    data class Transition(val from: VpnStateName, val to: VpnStateName) : VpnTraceDetail

    data class Failure(val value: VpnFailureName) : VpnTraceDetail
}

/** The state's name and nothing else. */
internal fun VpnConnectionState.traceName(): VpnStateName = when (this) {
    VpnConnectionState.Idle -> VpnStateName.IDLE
    VpnConnectionState.Preparing -> VpnStateName.PREPARING
    is VpnConnectionState.Connecting -> VpnStateName.CONNECTING
    is VpnConnectionState.Connected -> VpnStateName.CONNECTED
    VpnConnectionState.Disconnecting -> VpnStateName.DISCONNECTING
    is VpnConnectionState.Failed -> VpnStateName.FAILED
    VpnConnectionState.NoInternet -> VpnStateName.NO_INTERNET
}

internal fun ConnectionFailure.traceName(): VpnFailureName = when (this) {
    ConnectionFailure.ConsentDenied -> VpnFailureName.CONSENT_DENIED
    ConnectionFailure.NoInternet -> VpnFailureName.NO_INTERNET
    ConnectionFailure.ProfileMissing -> VpnFailureName.PROFILE_MISSING
    is ConnectionFailure.ProfileInvalid -> VpnFailureName.PROFILE_INVALID
    is ConnectionFailure.UnsupportedConfiguration -> VpnFailureName.UNSUPPORTED_CONFIGURATION
    ConnectionFailure.TunnelSetupFailed -> VpnFailureName.TUNNEL_SETUP_FAILED
    is ConnectionFailure.EngineFailed -> VpnFailureName.ENGINE_FAILED
    ConnectionFailure.TunnelUnusable -> VpnFailureName.TUNNEL_UNUSABLE
    ConnectionFailure.ServiceStopped -> VpnFailureName.SERVICE_STOPPED
    ConnectionFailure.Unknown -> VpnFailureName.UNKNOWN
}
