package app.vazie.vpn.feature.home

import androidx.compose.runtime.Immutable

/** What Home shows. `Empty` is not a connection state: it is the absence of anything to connect to. */
@Immutable
sealed interface HomeUiState {

    data object Empty : HomeUiState

    /** There is a configuration to connect through, and nothing is chosen yet.
     * The list below is the answer, so the screen asks for a choice, not an import. */
    data object Unselected : HomeUiState

    data class Ready(
        val configuration: HomeConfigurationUi,
        val connection: ConnectionUiState,
        /** Whether this build can run [configuration]; `false` disables Connect instead of letting it
         * fail. */
        val runnable: Boolean = true,
    ) : HomeUiState
}

/** `VpnConnectionState` as the UI sees it, one case per case. */
@Immutable
sealed interface ConnectionUiState {

    data object Idle : ConnectionUiState

    /** Awaiting the system VPN consent dialog. */
    data object Preparing : ConnectionUiState

    data object Connecting : ConnectionUiState

    data class Connected(val session: SessionUi) : ConnectionUiState

    data object Disconnecting : ConnectionUiState

    data class Failed(val failure: ConnectionFailureUi) : ConnectionUiState

    data object NoInternet : ConnectionUiState
}

/** A closed set, narrower than `ConnectionFailure` on purpose. */
enum class ConnectionFailureUi {
    PERMISSION,
    NO_INTERNET,
    PROFILE,
    UNSUPPORTED,
    TUNNEL,
    HANDSHAKE,
    TUNNEL_UNUSABLE,
    UNKNOWN,
}

@Immutable
data class HomeConfigurationUi(
    val name: String,
    /** Two or three characters for the leading mark: `VL`, `WG`. */
    val mark: String,
    /** `VLESS` — what the row says out loud. The only format the parser registry accepts. */
    val protocolLabel: String,
    /** The engine that would run this configuration — `xray` — read from `ProfileSummary.engineId`. */
    val engineLabel: String,
)

/** One live tunnel, as the screen sees it. */
@Immutable
data class SessionUi(
    val seconds: Long,
    /** Bytes in since the tunnel came up, or `TrafficStats.UNKNOWN`. */
    val rxBytes: Long,
    /** Bytes out since the tunnel came up, or `TrafficStats.UNKNOWN`. */
    val txBytes: Long,
    val diagnostics: DiagnosticsUi,
)

@Immutable
data class DiagnosticsUi(
    val engine: String,
    val protocol: String,
    val security: String?,
    val transport: String?,
    val flow: String?,
    val endpointHost: String,
    val endpointPort: Int,
    /** Null until Vazie measures round-trip time through the tunnel, which it does not yet. The panel renders
     * a dash rather than `0 ms`, because `0 ms` is a claim. */
    val latencyMs: Long?,
    val dnsMode: String,
    val ipv4: Boolean,
    val ipv6: Boolean,
)

sealed interface HomeAction {
    data object Connect : HomeAction

    /** Connect to one named configuration, selecting it on the way. */
    data class ConnectTo(val profileId: String) : HomeAction
    data object Cancel : HomeAction
    data object Disconnect : HomeAction
    data object Retry : HomeAction
    data object CheckConnection : HomeAction
    data object AddConfiguration : HomeAction
    data object ShowConnectionDetails : HomeAction
    data object OpenSettings : HomeAction
}

/** Whether pressing this is a person deciding to bring a tunnel up. */
internal val HomeAction.startsAConnection: Boolean
    get() = this == HomeAction.Connect ||
        this == HomeAction.Retry ||
        this == HomeAction.CheckConnection

/** One-shot effects; the state holder decides whether a button navigates or connects. */
sealed interface HomeEffect {
    data object OpenAddConfiguration : HomeEffect

    data object OpenConnectionDetails : HomeEffect
    data object OpenSettings : HomeEffect

    /** VPN permission is needed; the dialog needs an `Activity`, answered via `onVpnPermissionGranted`. */
    data object RequestVpnPermission : HomeEffect
}
