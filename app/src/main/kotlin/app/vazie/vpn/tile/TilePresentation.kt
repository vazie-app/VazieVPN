package app.vazie.vpn.tile

import android.service.quicksettings.Tile
import androidx.annotation.StringRes
import app.vazie.vpn.R
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState

/** What the Quick Settings tile shows, and what tapping it should do. */
internal data class VazieTilePresentation(
    val tileState: Int,
    @param:StringRes val labelRes: Int,
    @param:StringRes val subtitleRes: Int,
    val action: VazieTileAction,
)

/** What a tap means in this state. */
internal sealed interface VazieTileAction {
    data object Connect : VazieTileAction
    data object Disconnect : VazieTileAction
    data class OpenApp(val destination: TileDestination) : VazieTileAction
}

/** Where opening the app should land. Two screens, because there are exactly two reasons a tile hands the job
 * over: something needs consent, or there is nothing to connect to yet. */
internal enum class TileDestination { CONNECT, ADD_CONFIGURATION }

/** The tile, from the tunnel's state and whether anything is selected. */
internal fun tilePresentation(
    snapshot: VpnConnectionSnapshot,
    hasSelection: Boolean,
): VazieTilePresentation {
    val state = when (snapshot.state) {
        VpnConnectionState.Idle -> presentation(
            Tile.STATE_INACTIVE,
            R.string.connection_status_disconnected,
            VazieTileAction.Connect,
        )
        VpnConnectionState.Preparing -> presentation(
            Tile.STATE_ACTIVE,
            R.string.connection_status_preparing,
            VazieTileAction.Disconnect,
        )
        is VpnConnectionState.Connecting -> presentation(
            Tile.STATE_ACTIVE,
            R.string.connection_status_connecting,
            VazieTileAction.Disconnect,
        )
        is VpnConnectionState.Connected -> presentation(
            Tile.STATE_ACTIVE,
            R.string.connection_status_protected,
            VazieTileAction.Disconnect,
        )
        VpnConnectionState.Disconnecting -> presentation(
            Tile.STATE_ACTIVE,
            R.string.connection_status_disconnecting,
            VazieTileAction.Disconnect,
        )
        is VpnConnectionState.Failed -> presentation(
            Tile.STATE_INACTIVE,
            R.string.connection_status_failed,
            VazieTileAction.Connect,
        )
        VpnConnectionState.NoInternet -> presentation(
            Tile.STATE_INACTIVE,
            R.string.connection_status_no_internet,
            VazieTileAction.OpenApp(TileDestination.CONNECT),
        )
    }
    if (hasSelection || state.action != VazieTileAction.Connect) return state
    return state.copy(
        subtitleRes = R.string.connection_status_no_configuration,
        action = VazieTileAction.OpenApp(TileDestination.ADD_CONFIGURATION),
    )
}

private fun presentation(
    tileState: Int,
    @StringRes subtitleRes: Int,
    action: VazieTileAction,
) = VazieTilePresentation(
    tileState = tileState,
    labelRes = R.string.tile_label,
    subtitleRes = subtitleRes,
    action = action,
)
