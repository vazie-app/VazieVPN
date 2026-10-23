package app.vazie.vpn.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When the phone is allowed to buzz, and — mostly — when it is not. */
class ConnectionFeedbackTest {

    @Test
    fun `a connection that worked is worth feeling`() {
        assertEquals(
            ConnectionFeedback.Success,
            connectionFeedback(ConnectionUiState.Connecting, connected()),
        )
        assertEquals(
            ConnectionFeedback.Success,
            connectionFeedback(ConnectionUiState.Preparing, connected()),
        )
    }

    @Test
    fun `a connection that failed is worth feeling`() {
        assertEquals(
            ConnectionFeedback.Failure,
            connectionFeedback(ConnectionUiState.Connecting, failed()),
        )
    }

    @Test
    fun `a tunnel that died under the user is a failure too`() {
        assertEquals(ConnectionFeedback.Failure, connectionFeedback(connected(), failed()))
    }

    @Test
    fun `no network in answer to a tap is a failure`() {
        assertEquals(
            ConnectionFeedback.Failure,
            connectionFeedback(ConnectionUiState.Idle, ConnectionUiState.NoInternet),
        )
        assertEquals(
            ConnectionFeedback.Failure,
            connectionFeedback(ConnectionUiState.Preparing, ConnectionUiState.NoInternet),
        )
    }

    @Test
    fun `no network under a live tunnel is not this attempt's answer`() {
        assertNull(
            connectionFeedback(connected(), ConnectionUiState.NoInternet),
            "a pocket buzzed because the wifi dropped",
        )
    }

    @Test
    fun `a cold open restoring an existing state says nothing`() {
        listOf(
            connected(),
            failed(),
            ConnectionUiState.NoInternet,
            ConnectionUiState.Idle,
            ConnectionUiState.Connecting,
        ).forEach { state ->
            assertNull(connectionFeedback(previous = null, current = state), "$state on a cold open")
        }
    }

    @Test
    fun `a repeated state says nothing`() {
        listOf(connected(), failed(), ConnectionUiState.NoInternet).forEach { state ->
            assertNull(connectionFeedback(previous = state, current = state), "$state repeated")
        }
    }

    @Test
    fun `a tunnel that came up somewhere else is not congratulated`() {
        assertNull(
            connectionFeedback(ConnectionUiState.Idle, connected()),
            "Connected arrived without an attempt on this screen",
        )
        assertNull(connectionFeedback(ConnectionUiState.Disconnecting, connected()))
    }

    @Test
    fun `stages say nothing`() {
        val stages = listOf(
            ConnectionUiState.Preparing,
            ConnectionUiState.Connecting,
            ConnectionUiState.Disconnecting,
            ConnectionUiState.Idle,
        )
        stages.forEach { stage ->
            assertNull(connectionFeedback(ConnectionUiState.Idle, stage), "$stage buzzed")
            assertNull(connectionFeedback(connected(), stage), "$stage buzzed after Connected")
        }
    }

    @Test
    fun `one connection produces exactly one buzz`() {
        // The sequence a real connection walks through, fed to the policy in order. Anything other
        // than a single Success here is a phone that buzzes two or three times per connect.
        val walk = listOf(
            ConnectionUiState.Idle,
            ConnectionUiState.Preparing,
            ConnectionUiState.Connecting,
            connected(),
            connected(),
        )
        val felt = walk.zipWithNext().mapNotNull { (previous, current) ->
            connectionFeedback(previous, current)
        }

        assertEquals(listOf(ConnectionFeedback.Success), felt)
    }

    private fun connected() = ConnectionUiState.Connected(
        SessionUi(
            seconds = 0,
            rxBytes = 0,
            txBytes = 0,
            diagnostics = DiagnosticsUi(
                engine = "xray",
                protocol = "vless",
                security = null,
                transport = null,
                flow = null,
                endpointHost = "relay.example.net",
                endpointPort = 443,
                latencyMs = null,
                dnsMode = "tunnel",
                ipv4 = true,
                ipv6 = false,
            ),
        ),
    )

    private fun failed() = ConnectionUiState.Failed(ConnectionFailureUi.TUNNEL)
}
