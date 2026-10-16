package app.vazie.vpn.feature.home

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectionDiagnostics
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.DnsMode
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.UnsupportedRuntimeFeature
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The runtime's state, as words on Home. */
class HomePresentationTest {

    @Test
    fun `nothing selected and nothing connected is the empty state`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(),
            selection = null,
            summaries = emptyList(),
            runnable = true,
            now = NOW,
        )

        assertEquals(HomeUiState.Empty, state)
    }

    @Test
    fun `a selection with no matching profile asks for a choice when there is one to make`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(),
            selection = ProfileId("gone"),
            summaries = listOf(summary("other")),
            runnable = true,
            now = NOW,
        )

        assertEquals(HomeUiState.Unselected, state)
    }

    @Test
    fun `configurations and nothing chosen ask for a choice, not an import`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(),
            selection = null,
            summaries = listOf(summary("profile-1")),
            runnable = true,
            now = NOW,
        )

        assertEquals(HomeUiState.Unselected, state)
        assertEquals(R.string.home_unselected_title, homeStatusTitle(state))
        assertEquals(false, connectionPresentation(state).actionEnabled)
    }

    @Test
    fun `the tunnel's profile is shown even when another one is selected`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(ProfileId("b"), NOW),
                subject = ConnectionSubject(summary("b", "Travel")),
            ),
            selection = ProfileId("a"),
            summaries = listOf(summary("a", "Home relay"), summary("b", "Travel")),
            runnable = true,
            now = NOW,
        )

        assertIs<HomeUiState.Ready>(state)
        assertEquals("Travel", state.configuration.name)
    }

    @Test
    fun `a session's duration comes from when it started`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(ProfileId("a"), NOW.minusSeconds(252)),
                subject = ConnectionSubject(summary("a")),
                diagnostics = diagnostics(),
            ),
            selection = ProfileId("a"),
            summaries = listOf(summary("a")),
            runnable = true,
            now = NOW,
            traffic = TrafficStats(rxBytes = 214L * 1024 * 1024, txBytes = 38L * 1024 * 1024),
        )

        val connection = (state as HomeUiState.Ready).connection
        assertIs<ConnectionUiState.Connected>(connection)
        assertEquals(252, connection.session.seconds)
        assertEquals("04:12", formatSessionDuration(connection.session.seconds))
        // The raw counters, undivided. Whether they read as `214.0 MB` or as `—` is
        // `trafficReading`'s question, and `TrafficReadingTest` is where it is asked.
        assertEquals(214L * 1024 * 1024, connection.session.rxBytes)
        assertEquals(38L * 1024 * 1024, connection.session.txBytes)
    }

    @Test
    fun `a clock that ran backwards does not produce a negative session`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(ProfileId("a"), NOW.plusSeconds(30)),
                subject = ConnectionSubject(summary("a")),
                diagnostics = diagnostics(),
            ),
            selection = ProfileId("a"),
            summaries = listOf(summary("a")),
            runnable = true,
            now = NOW,
        )

        val connection = (state as HomeUiState.Ready).connection
        assertIs<ConnectionUiState.Connected>(connection)
        assertEquals(0, connection.session.seconds)
    }

    @Test
    fun `latency stays absent because nothing measures it`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(ProfileId("a"), NOW),
                subject = ConnectionSubject(summary("a")),
                diagnostics = diagnostics(),
            ),
            selection = ProfileId("a"),
            summaries = listOf(summary("a")),
            runnable = true,
            now = NOW,
        )

        val connection = (state as HomeUiState.Ready).connection as ConnectionUiState.Connected
        assertNull(connection.session.diagnostics.latencyMs)
    }

    @Test
    fun `every failure maps to a sentence and none of them can carry a value`() {
        val failures = listOf(
            ConnectionFailure.ConsentDenied to ConnectionFailureUi.PERMISSION,
            ConnectionFailure.NoInternet to ConnectionFailureUi.NO_INTERNET,
            ConnectionFailure.ProfileMissing to ConnectionFailureUi.PROFILE,
            ConnectionFailure.ProfileInvalid("publicKey") to ConnectionFailureUi.PROFILE,
            ConnectionFailure.UnsupportedConfiguration(UnsupportedRuntimeFeature.FLOW) to
                ConnectionFailureUi.UNSUPPORTED,
            ConnectionFailure.TunnelSetupFailed to ConnectionFailureUi.TUNNEL,
            ConnectionFailure.TunnelUnusable to ConnectionFailureUi.TUNNEL_UNUSABLE,
            ConnectionFailure.ServiceStopped to ConnectionFailureUi.TUNNEL,
            ConnectionFailure.Unknown to ConnectionFailureUi.UNKNOWN,
        )

        failures.forEach { (failure, expected) ->
            assertEquals(expected, failure.toUi(), "$failure mapped wrong")
        }
    }

    @Test
    fun `the mark is two letters of the protocol, not a lookup table`() {
        val state = homeState(
            snapshot = VpnConnectionSnapshot(),
            selection = ProfileId("a"),
            summaries = listOf(summary("a")),
            runnable = true,
            now = NOW,
        )

        assertEquals("VL", (state as HomeUiState.Ready).configuration.mark)
    }

    private fun summary(id: String, name: String = "Home relay") =
        app.vazie.vpn.api.ProfileSummary(
            id = ProfileId(id),
            name = name,
            engineId = app.vazie.vpn.api.VpnEngineId.XRAY,
            protocolLabel = "VLESS",
        )

    private fun diagnostics() = ConnectionDiagnostics(
        engine = "xray",
        protocol = "vless",
        security = "reality",
        transport = "raw",
        flow = "xtls-rprx-vision",
        endpointHost = "relay.example.net",
        endpointPort = 443,
        dnsMode = DnsMode.TUNNEL,
        ipv4 = true,
        ipv6 = false,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")
    }
}
