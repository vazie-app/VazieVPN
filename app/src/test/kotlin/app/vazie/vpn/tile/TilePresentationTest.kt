package app.vazie.vpn.tile

import android.service.quicksettings.Tile
import app.vazie.vpn.R
import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Domain state to tile state, every case, with nothing left to a default. */
class TilePresentationTest {

    @Test
    fun `nothing to connect to is tappable and says so`() {
        val presentation = tilePresentation(VpnConnectionSnapshot(), hasSelection = false)

        assertEquals(Tile.STATE_INACTIVE, presentation.tileState)
        assertEquals(R.string.connection_status_no_configuration, presentation.subtitleRes)
        assertEquals(
            VazieTileAction.OpenApp(TileDestination.ADD_CONFIGURATION),
            presentation.action,
            "a tile with no configuration must lead somewhere useful",
        )
    }

    /** The regression this file exists to hold shut. */
    @Test
    fun `a live tunnel is never described as having no configuration`() {
        val running = listOf(
            VpnConnectionState.Preparing,
            VpnConnectionState.Connecting(PROFILE),
            VpnConnectionState.Connected(PROFILE, CONNECTED_SINCE),
            VpnConnectionState.Disconnecting,
        )
        running.forEach { state ->
            val presentation = tilePresentation(VpnConnectionSnapshot(state = state), hasSelection = false)

            assertEquals(Tile.STATE_ACTIVE, presentation.tileState, "$state")
            assertEquals(VazieTileAction.Disconnect, presentation.action, "$state")
            assertTrue(
                presentation.subtitleRes != R.string.connection_status_no_configuration,
                "$state was described as having no configuration",
            )
        }
    }

    /** Connected, the tile is active and its tap disconnects — the whole of what a tile has to do while a
     * tunnel is up, and what it could not do before. */
    @Test
    fun `connected, the tile is active and disconnects`() {
        val presentation = tilePresentation(
            VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(PROFILE, CONNECTED_SINCE),
                subject = ConnectionSubject(
                    ProfileSummary(
                        id = PROFILE,
                        name = "Home relay",
                        engineId = VpnEngineId.XRAY,
                        protocolLabel = "VLESS",
                    ),
                ),
            ),
            hasSelection = true,
        )

        assertEquals(Tile.STATE_ACTIVE, presentation.tileState)
        assertEquals(R.string.connection_status_protected, presentation.subtitleRes)
        assertEquals(VazieTileAction.Disconnect, presentation.action)
    }

    /** With something selected - either kind - a disconnected tile connects rather than sending anyone to add
     * a configuration they already have. */
    @Test
    fun `a selection of either kind makes the tile connect`() {
        val presentation = tilePresentation(VpnConnectionSnapshot(), hasSelection = true)

        assertEquals(VazieTileAction.Connect, presentation.action)
        assertEquals(R.string.connection_status_disconnected, presentation.subtitleRes)
    }

    /** Only a Connect is redirected when there is nothing to connect to. A failed attempt would offer to
     * retry, and with nothing selected there is nothing to retry. */
    @Test
    fun `a failed attempt with nothing selected leads somewhere useful`() {
        val presentation = tilePresentation(
            VpnConnectionSnapshot(state = VpnConnectionState.Failed(ConnectionFailure.TunnelSetupFailed)),
            hasSelection = false,
        )

        assertEquals(
            VazieTileAction.OpenApp(TileDestination.ADD_CONFIGURATION),
            presentation.action,
        )
    }

    @Test
    fun `disconnected is inactive and connects`() {
        val presentation = presentation(VpnConnectionState.Idle)

        assertEquals(Tile.STATE_INACTIVE, presentation.tileState)
        assertEquals(R.string.connection_status_disconnected, presentation.subtitleRes)
        assertEquals(VazieTileAction.Connect, presentation.action)
    }

    @Test
    fun `the three transitional states read as active and stop the attempt`() {
        val transitional = listOf(
            VpnConnectionState.Preparing to R.string.connection_status_preparing,
            VpnConnectionState.Connecting(PROFILE) to R.string.connection_status_connecting,
            VpnConnectionState.Disconnecting to R.string.connection_status_disconnecting,
        )
        transitional.forEach { (state, subtitle) ->
            val presentation = presentation(state)
            assertEquals(Tile.STATE_ACTIVE, presentation.tileState, "$state")
            assertEquals(subtitle, presentation.subtitleRes, "$state")
            assertEquals(VazieTileAction.Disconnect, presentation.action, "$state")
        }
    }

    @Test
    fun `connected is active and disconnects`() {
        val presentation = presentation(VpnConnectionState.Connected(PROFILE, Instant.EPOCH))

        assertEquals(Tile.STATE_ACTIVE, presentation.tileState)
        assertEquals(R.string.connection_status_protected, presentation.subtitleRes)
        assertEquals(VazieTileAction.Disconnect, presentation.action)
    }

    @Test
    fun `a failure is inactive and offers another attempt`() {
        val presentation = presentation(VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable))

        assertEquals(Tile.STATE_INACTIVE, presentation.tileState)
        assertEquals(R.string.connection_status_failed, presentation.subtitleRes)
        assertEquals(VazieTileAction.Connect, presentation.action)
    }

    @Test
    fun `no internet opens the app rather than retrying into the same wall`() {
        val presentation = presentation(VpnConnectionState.NoInternet)

        assertEquals(Tile.STATE_INACTIVE, presentation.tileState)
        assertEquals(R.string.connection_status_no_internet, presentation.subtitleRes)
        assertEquals(VazieTileAction.OpenApp(TileDestination.CONNECT), presentation.action)
    }

    @Test
    fun `the tile is never unavailable, and never says what it connects to`() {
        val states = listOf(
            VpnConnectionState.Idle,
            VpnConnectionState.Preparing,
            VpnConnectionState.Connecting(PROFILE),
            VpnConnectionState.Connected(PROFILE, Instant.EPOCH),
            VpnConnectionState.Disconnecting,
            VpnConnectionState.Failed(ConnectionFailure.Unknown),
            VpnConnectionState.NoInternet,
        )
        states.forEach { state ->
            val presentation = presentation(state)
            assertTrue(presentation.tileState != Tile.STATE_UNAVAILABLE, "$state went unavailable")
            assertEquals(
                R.string.tile_label,
                presentation.labelRes,
                "$state put something other than the app's name on the tile",
            )
        }
        assertEquals(
            R.string.tile_label,
            tilePresentation(VpnConnectionSnapshot(), hasSelection = false).labelRes,
        )
    }

    private fun presentation(state: VpnConnectionState) =
        tilePresentation(VpnConnectionSnapshot(state = state), hasSelection = true)

    private companion object {
        val CONNECTED_SINCE: Instant = Instant.parse("2026-09-01T12:00:00Z")
        val PROFILE = ProfileId("profile-1")
    }
}
