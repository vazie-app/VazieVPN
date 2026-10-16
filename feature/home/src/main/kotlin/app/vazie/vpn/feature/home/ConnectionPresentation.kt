package app.vazie.vpn.feature.home

import androidx.annotation.StringRes
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieStatusTone

/** How a connection state is presented: the words, the tone and the one action that changes it. */
internal data class ConnectionPresentation(
    @param:StringRes val statusRes: Int,
    /** Null when the screen supplies the headline itself — `Connected` shows the session duration. */
    @param:StringRes val titleRes: Int?,
    val tone: VazieStatusTone,
    @param:StringRes val actionRes: Int,
    val actionVariant: VazieButtonVariant,
    val action: HomeAction,
    val actionEnabled: Boolean = true,
)

internal fun connectionPresentation(state: HomeUiState): ConnectionPresentation = when (state) {
    HomeUiState.Empty -> ConnectionPresentation(
        statusRes = R.string.home_status_no_configuration,
        titleRes = R.string.home_no_config_title,
        tone = VazieStatusTone.Neutral,
        actionRes = R.string.home_add_config,
        actionVariant = VazieButtonVariant.Primary,
        action = HomeAction.AddConfiguration,
    )

    HomeUiState.Unselected -> ConnectionPresentation(
        statusRes = R.string.home_status_unselected,
        titleRes = R.string.home_unselected_title,
        tone = VazieStatusTone.Neutral,
        actionRes = R.string.home_action_connect,
        actionVariant = VazieButtonVariant.Primary,
        action = HomeAction.Connect,
        actionEnabled = false,
    )

    is HomeUiState.Ready -> when (state.connection) {
        ConnectionUiState.Idle -> ConnectionPresentation(
            statusRes = R.string.home_status_disconnected,
            titleRes = R.string.home_title_not_protected,
            tone = VazieStatusTone.Neutral,
            actionRes = R.string.home_action_connect,
            actionVariant = VazieButtonVariant.Primary,
            action = HomeAction.Connect,
            // A configuration this build cannot run is still the user's configuration and stays visible and
            // named; what it does not get is a button that would fail.
            actionEnabled = state.runnable,
        )

        ConnectionUiState.Preparing -> ConnectionPresentation(
            statusRes = R.string.home_status_preparing,
            titleRes = R.string.home_title_preparing,
            tone = VazieStatusTone.Info,
            actionRes = R.string.home_action_cancel,
            actionVariant = VazieButtonVariant.Secondary,
            action = HomeAction.Cancel,
        )

        ConnectionUiState.Connecting -> ConnectionPresentation(
            statusRes = R.string.home_status_connecting,
            titleRes = R.string.home_title_connecting,
            tone = VazieStatusTone.Warning,
            actionRes = R.string.home_action_cancel,
            actionVariant = VazieButtonVariant.Secondary,
            action = HomeAction.Cancel,
        )

        is ConnectionUiState.Connected -> ConnectionPresentation(
            statusRes = R.string.home_status_protected,
            titleRes = null,
            tone = VazieStatusTone.Success,
            actionRes = R.string.home_action_disconnect,
            actionVariant = VazieButtonVariant.Destructive,
            action = HomeAction.Disconnect,
        )

        ConnectionUiState.Disconnecting -> ConnectionPresentation(
            statusRes = R.string.home_status_disconnecting,
            titleRes = R.string.home_title_disconnecting,
            tone = VazieStatusTone.Warning,
            actionRes = R.string.home_action_disconnecting,
            actionVariant = VazieButtonVariant.Secondary,
            action = HomeAction.Cancel,
            actionEnabled = false,
        )

        is ConnectionUiState.Failed -> ConnectionPresentation(
            statusRes = R.string.home_status_failed,
            titleRes = R.string.home_title_failed,
            tone = VazieStatusTone.Error,
            actionRes = R.string.home_action_retry,
            actionVariant = VazieButtonVariant.Primary,
            action = HomeAction.Retry,
            actionEnabled = state.runnable,
        )

        ConnectionUiState.NoInternet -> ConnectionPresentation(
            statusRes = R.string.home_status_no_internet,
            titleRes = R.string.home_title_no_internet,
            tone = VazieStatusTone.Neutral,
            actionRes = R.string.home_action_check_connection,
            actionVariant = VazieButtonVariant.Secondary,
            action = HomeAction.CheckConnection,
        )
    }
}

@StringRes
internal fun ConnectionFailureUi.reasonRes(): Int = when (this) {
    ConnectionFailureUi.PERMISSION -> R.string.home_failure_permission
    ConnectionFailureUi.NO_INTERNET -> R.string.home_failure_no_internet
    ConnectionFailureUi.PROFILE -> R.string.home_failure_profile
    ConnectionFailureUi.UNSUPPORTED -> R.string.home_failure_unsupported
    ConnectionFailureUi.TUNNEL -> R.string.home_failure_tunnel
    ConnectionFailureUi.HANDSHAKE -> R.string.home_failure_handshake
    ConnectionFailureUi.TUNNEL_UNUSABLE -> R.string.home_failure_tunnel_unusable
    ConnectionFailureUi.UNKNOWN -> R.string.home_failure_unknown
}

/** `252` → `04:12`; hours appear only once there are any. */
internal fun formatSessionDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainder = safeSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%02d:%02d".format(minutes, remainder)
    }
}
