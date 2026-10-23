package app.vazie.vpn.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.vazie.vpn.core.designsystem.component.VazieConnectionActionKind
import app.vazie.vpn.core.designsystem.component.VazieRouteAnimation
import app.vazie.vpn.core.designsystem.component.VazieRouteState

/** How Home's state is drawn by the "Маршрут" design — pure, so the whole mapping is one JVM test away. */
internal fun homeRouteState(state: HomeUiState): VazieRouteState? = when (state) {
    HomeUiState.Empty, HomeUiState.Unselected -> null
    is HomeUiState.Ready -> when (state.connection) {
        ConnectionUiState.Idle -> VazieRouteState.Disconnected
        ConnectionUiState.Preparing, ConnectionUiState.Connecting -> VazieRouteState.Connecting
        is ConnectionUiState.Connected -> VazieRouteState.Connected
        ConnectionUiState.Disconnecting -> VazieRouteState.Disconnected
        is ConnectionUiState.Failed, ConnectionUiState.NoInternet -> VazieRouteState.Failed
    }
}

/** The status word. */
@StringRes
internal fun homeStatusTitle(state: HomeUiState): Int = when (state) {
    HomeUiState.Empty -> R.string.home_no_config_title
    HomeUiState.Unselected -> R.string.home_unselected_title
    is HomeUiState.Ready -> when (state.connection) {
        ConnectionUiState.Idle -> R.string.home_route_title_disconnected
        ConnectionUiState.Preparing -> R.string.home_title_preparing
        ConnectionUiState.Connecting -> R.string.home_route_title_connecting
        is ConnectionUiState.Connected -> R.string.home_route_title_connected
        ConnectionUiState.Disconnecting -> R.string.home_route_title_disconnecting
        is ConnectionUiState.Failed -> R.string.home_title_failed
        ConnectionUiState.NoInternet -> R.string.home_status_no_internet
    }
}

/** The line under the status word. */
@Immutable
internal sealed interface HomeStatusLine {
    /** Plain words, optionally with the configuration name. */
    data class Words(@param:StringRes val res: Int, val configurationName: String? = null) : HomeStatusLine

    /** The connected line: session time and protocol, in Martian Mono. [seconds] is the runtime's own session
     * clock (`SessionUi.seconds`, ticked by `connectionTicks`) — Home starts no timer of its own. */
    data class Session(val seconds: Long, val protocol: String) : HomeStatusLine
}

internal fun homeStatusLine(state: HomeUiState): HomeStatusLine = when (state) {
    HomeUiState.Empty -> HomeStatusLine.Words(R.string.home_no_config_body)
    HomeUiState.Unselected -> HomeStatusLine.Words(R.string.home_unselected_body)
    is HomeUiState.Ready -> when (val connection = state.connection) {
        ConnectionUiState.Idle -> HomeStatusLine.Words(
            if (state.runnable) R.string.home_route_subtitle_direct else R.string.home_subtitle_unsupported,
        )
        ConnectionUiState.Preparing -> HomeStatusLine.Words(R.string.home_subtitle_preparing)
        ConnectionUiState.Connecting -> HomeStatusLine.Words(
            R.string.home_route_subtitle_connecting,
            configurationName = state.configuration.name,
        )
        is ConnectionUiState.Connected -> HomeStatusLine.Session(
            seconds = connection.session.seconds,
            protocol = state.configuration.protocolLabel,
        )
        ConnectionUiState.Disconnecting -> HomeStatusLine.Words(R.string.home_title_disconnecting)
        is ConnectionUiState.Failed -> HomeStatusLine.Words(connection.failure.reasonRes())
        ConnectionUiState.NoInternet -> HomeStatusLine.Words(R.string.home_subtitle_no_internet)
    }
}

/** The look of the primary action, or `null` for actions not about the tunnel (the shared button). */
internal fun connectionActionKind(state: HomeUiState, action: HomeAction): VazieConnectionActionKind? = when (action) {
    HomeAction.Connect -> VazieConnectionActionKind.Connect
    HomeAction.Disconnect -> VazieConnectionActionKind.Disconnect
    HomeAction.Retry -> VazieConnectionActionKind.Retry
    HomeAction.Cancel -> {
        val disconnecting = state is HomeUiState.Ready && state.connection == ConnectionUiState.Disconnecting
        if (disconnecting) VazieConnectionActionKind.Disconnect else VazieConnectionActionKind.Connecting
    }
    else -> null
}

/** The real state, except a connection is drawn as "connecting" until the route animation arrives. */
internal fun homeShownState(state: HomeUiState, route: VazieRouteAnimation): HomeUiState = when {
    state is HomeUiState.Ready &&
        state.connection is ConnectionUiState.Connected &&
        route.isCatchingUpTo(VazieRouteState.Connected) -> state.copy(connection = ConnectionUiState.Connecting)
    else -> state
}
