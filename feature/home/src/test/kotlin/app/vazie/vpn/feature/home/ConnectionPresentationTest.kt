package app.vazie.vpn.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules this pins are product rules, not layout ones: every connection state has to say what it is in
 * words, and the button under it has to be the action that state offers. */
class ConnectionPresentationTest {

    @Test
    fun `every state names itself in words`() {
        HomeFixtures.allStates.forEach { state ->
            val presentation = connectionPresentation(state)
            assertNotEquals(0, presentation.statusRes, "no status text for $state")
            assertNotEquals(0, presentation.actionRes, "no action label for $state")
        }
    }

    @Test
    fun `connected defers its headline to the session duration`() {
        assertNull(connectionPresentation(HomeFixtures.connected).titleRes)

        HomeFixtures.allStates.filterNot { it == HomeFixtures.connected }.forEach { state ->
            assertNotEquals(null, connectionPresentation(state).titleRes, "no headline for $state")
        }
    }

    @Test
    fun `the primary action matches the state it is offered in`() {
        fun actionFor(connection: ConnectionUiState) =
            connectionPresentation(HomeUiState.Ready(HomeFixtures.configuration, connection)).action

        assertEquals(HomeAction.Connect, actionFor(ConnectionUiState.Idle))
        assertEquals(HomeAction.Cancel, actionFor(ConnectionUiState.Connecting))
        assertEquals(HomeAction.Disconnect, actionFor(ConnectionUiState.Connected(HomeFixtures.session)))
        assertEquals(HomeAction.Retry, actionFor(ConnectionUiState.Failed(ConnectionFailureUi.TUNNEL_UNUSABLE)))
        assertEquals(HomeAction.CheckConnection, actionFor(ConnectionUiState.NoInternet))
        assertEquals(HomeAction.AddConfiguration, connectionPresentation(HomeUiState.Empty).action)
    }

    @Test
    fun `disconnecting offers no action to press`() {
        val presentation = connectionPresentation(
            HomeUiState.Ready(HomeFixtures.configuration, ConnectionUiState.Disconnecting),
        )
        assertTrue(!presentation.actionEnabled)
    }

    @Test
    fun `every failure has its own message`() {
        val messages = ConnectionFailureUi.entries.map { it.reasonRes() }
        assertEquals(messages.size, messages.toSet().size, "two failures share a message")
    }

    @Test
    fun `session duration is minutes and seconds until there are hours`() {
        assertEquals("04:12", formatSessionDuration(252))
        assertEquals("00:00", formatSessionDuration(0))
        assertEquals("00:09", formatSessionDuration(9))
        assertEquals("59:59", formatSessionDuration(3599))
        assertEquals("1:00:00", formatSessionDuration(3600))
        assertEquals("12:00:05", formatSessionDuration(43205))
    }

    @Test
    fun `a negative duration reads as zero rather than as a minus sign`() {
        assertEquals("00:00", formatSessionDuration(-30))
    }
}
