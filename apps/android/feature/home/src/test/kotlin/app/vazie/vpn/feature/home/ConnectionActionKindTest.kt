package app.vazie.vpn.feature.home

import app.vazie.vpn.core.designsystem.component.VazieConnectionActionKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The "Маршрут" action only changes how the primary action looks. These pin that every connection state
 * still offers the action it offered before, and which look that action takes. */
class ConnectionActionKindTest {

    private fun ready(connection: ConnectionUiState) = HomeUiState.Ready(HomeFixtures.configuration, connection)

    private fun kindFor(state: HomeUiState): VazieConnectionActionKind? =
        connectionActionKind(state, connectionPresentation(state).action)

    @Test
    fun `each connection state keeps its action and gets the matching look`() {
        val expected = mapOf(
            ready(ConnectionUiState.Idle) to (HomeAction.Connect to VazieConnectionActionKind.Connect),
            ready(ConnectionUiState.Preparing) to (HomeAction.Cancel to VazieConnectionActionKind.Connecting),
            ready(ConnectionUiState.Connecting) to (HomeAction.Cancel to VazieConnectionActionKind.Connecting),
            HomeFixtures.connected to (HomeAction.Disconnect to VazieConnectionActionKind.Disconnect),
            ready(ConnectionUiState.Disconnecting) to (HomeAction.Cancel to VazieConnectionActionKind.Disconnect),
            ready(ConnectionUiState.Failed(ConnectionFailureUi.HANDSHAKE)) to (HomeAction.Retry to VazieConnectionActionKind.Retry),
        )
        expected.forEach { (state, pair) ->
            val (action, kind) = pair
            assertEquals(action, connectionPresentation(state).action, "the action changed for $state")
            assertEquals(kind, kindFor(state), "look for $state")
        }
    }

    @Test
    fun `actions that are not about the tunnel keep the shared button`() {
        assertNull(kindFor(HomeUiState.Empty), "adding a configuration is not a connection action")
        assertNull(kindFor(ready(ConnectionUiState.NoInternet)), "checking the network is not a connection action")
    }

    @Test
    fun `an unrunnable configuration still offers Connect, disabled`() {
        val presentation = connectionPresentation(HomeFixtures.unsupported)
        assertEquals(HomeAction.Connect, presentation.action)
        assertEquals(false, presentation.actionEnabled)
        assertEquals(VazieConnectionActionKind.Connect, kindFor(HomeFixtures.unsupported))
    }
}
