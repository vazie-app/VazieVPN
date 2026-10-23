package app.vazie.vpn.feature.home

import app.vazie.vpn.core.designsystem.component.VazieRouteState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's state as the "Маршрут" design draws it. Pins the mapping, and that nothing is invented: no
 * "reconnecting", no timer that is not the runtime's own. */
class HomeRoutePresentationTest {

    private fun ready(connection: ConnectionUiState) = HomeUiState.Ready(HomeFixtures.configuration, connection)

    @Test
    fun `every connection state has its route`() {
        val expected = mapOf(
            ready(ConnectionUiState.Idle) to VazieRouteState.Disconnected,
            ready(ConnectionUiState.Preparing) to VazieRouteState.Connecting,
            ready(ConnectionUiState.Connecting) to VazieRouteState.Connecting,
            HomeFixtures.connected to VazieRouteState.Connected,
            ready(ConnectionUiState.Disconnecting) to VazieRouteState.Disconnected,
            ready(ConnectionUiState.Failed(ConnectionFailureUi.HANDSHAKE)) to VazieRouteState.Failed,
            ready(ConnectionUiState.NoInternet) to VazieRouteState.Failed,
        )
        expected.forEach { (state, route) -> assertEquals(route, homeRouteState(state), "$state") }
    }

    @Test
    fun `no configuration draws the empty state, not a route`() {
        assertNull(homeRouteState(HomeUiState.Empty))
        assertEquals(R.string.home_no_config_title, homeStatusTitle(HomeUiState.Empty))
    }

    @Test
    fun `nothing is drawn as reconnecting — the runtime has no such state`() {
        HomeFixtures.allStates.forEach { state ->
            assertTrue(homeRouteState(state) != VazieRouteState.Reconnecting, "$state was drawn as reconnecting")
        }
    }

    @Test
    fun `the status words follow the design`() {
        assertEquals(R.string.home_route_title_disconnected, homeStatusTitle(ready(ConnectionUiState.Idle)))
        assertEquals(R.string.home_route_title_connecting, homeStatusTitle(ready(ConnectionUiState.Connecting)))
        assertEquals(R.string.home_route_title_connected, homeStatusTitle(HomeFixtures.connected))
        assertEquals(R.string.home_route_title_disconnecting, homeStatusTitle(ready(ConnectionUiState.Disconnecting)))
        assertEquals(R.string.home_title_failed, homeStatusTitle(ready(ConnectionUiState.Failed(ConnectionFailureUi.TUNNEL))))
        assertEquals(R.string.home_status_no_internet, homeStatusTitle(ready(ConnectionUiState.NoInternet)))
    }

    @Test
    fun `only a connected state shows the session, and it is the runtime's own clock`() {
        val line = assertIs<HomeStatusLine.Session>(homeStatusLine(HomeFixtures.connected))
        val session = (HomeFixtures.connected.connection as ConnectionUiState.Connected).session
        assertEquals(session.seconds, line.seconds, "Home must not start a timer of its own")
        assertEquals(HomeFixtures.configuration.protocolLabel, line.protocol)
        HomeFixtures.allStates.filter { it != HomeFixtures.connected }.forEach { state ->
            val connected = (state as? HomeUiState.Ready)?.connection is ConnectionUiState.Connected
            if (!connected) assertIs<HomeStatusLine.Words>(homeStatusLine(state), "$state showed a timer")
        }
    }

    @Test
    fun `a failure explains itself with the failure's own reason`() {
        ConnectionFailureUi.entries.forEach { failure ->
            val line = assertIs<HomeStatusLine.Words>(homeStatusLine(ready(ConnectionUiState.Failed(failure))))
            assertEquals(failure.reasonRes(), line.res, "$failure")
        }
    }

    @Test
    fun `connecting names where the route goes, and an unrunnable configuration says so`() {
        val connecting = assertIs<HomeStatusLine.Words>(homeStatusLine(ready(ConnectionUiState.Connecting)))
        assertEquals(HomeFixtures.configuration.name, connecting.configurationName)
        val unsupported = assertIs<HomeStatusLine.Words>(homeStatusLine(HomeFixtures.unsupported))
        assertEquals(R.string.home_subtitle_unsupported, unsupported.res)
    }

    @Test
    fun `retry is offered only where the existing logic retries`() {
        HomeFixtures.allStates.forEach { state ->
            val action = connectionPresentation(state).action
            if (action == HomeAction.Retry) {
                assertIs<ConnectionUiState.Failed>((state as HomeUiState.Ready).connection, "Retry offered outside a failure")
            }
        }
    }
}
