package app.vazie.vpn.widget

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnEngineId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import app.vazie.vpn.core.model.LastUsed
import java.time.ZoneOffset
import kotlin.test.assertTrue

/** What the launcher gets to know. */
class VazieWidgetPublisherTest {

    @Test
    fun `no profiles is no configuration, not an idle one`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(),
            selected = null,
            summaries = emptyList(),
            now = NOW,
        )

        assertEquals(VazieWidgetState.NoConfiguration, state)
    }

    @Test
    fun `the selection becomes the widget's configuration`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(),
            selected = summary("a", "Home relay"),
            summaries = listOf(summary("a", "Home relay"), summary("b", "Travel")),
            now = NOW,
        )

        assertIs<VazieWidgetState.Configured>(state)
        assertEquals("Home relay", state.selected.name)
        assertEquals("VL", state.selected.mark)
        assertEquals(listOf("Travel"), state.shortcuts.map { it.name })
        assertEquals(WidgetConnectionUi.Idle, state.connection)
    }

    @Test
    fun `the tunnel's profile outranks the selection`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(ProfileId("b"), NOW.minusSeconds(125)),
                subject = ConnectionSubject(summary("b", "Travel")),
            ),
            selected = summary("a", "Home relay"),
            summaries = listOf(summary("a", "Home relay"), summary("b", "Travel")),
            now = NOW,
        )

        assertIs<VazieWidgetState.Configured>(state)
        assertEquals("Travel", state.selected.name)
        assertEquals(listOf("Home relay"), state.shortcuts.map { it.name })
    }

    /** Connected is a state and no longer a measurement. */
    @Test
    fun `a connected widget carries no measurement`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(ProfileId("a"), NOW.minusSeconds(3725)),
                subject = ConnectionSubject(summary("a")),
            ),
            selected = summary("a"),
            summaries = listOf(summary("a")),
            now = NOW,
        )

        val connection = (state as VazieWidgetState.Configured).connection
        assertEquals(WidgetConnectionUi.Connected, connection)
    }

    @Test
    fun `every runtime state has a widget state`() {
        val cases = listOf(
            VpnConnectionState.Idle to WidgetConnectionUi.Idle,
            VpnConnectionState.Preparing to WidgetConnectionUi.Preparing,
            VpnConnectionState.Connecting(ProfileId("a")) to WidgetConnectionUi.Connecting,
            VpnConnectionState.Disconnecting to WidgetConnectionUi.Disconnecting,
            VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable) to WidgetConnectionUi.Failed,
            VpnConnectionState.NoInternet to WidgetConnectionUi.NoInternet,
        )

        cases.forEach { (runtime, expected) ->
            val state = widgetState(
                snapshot = VpnConnectionSnapshot(state = runtime, subject = ConnectionSubject(summary("a"))),
                selected = summary("a"),
                summaries = listOf(summary("a")),
                now = NOW,
            )
            assertEquals(expected, (state as VazieWidgetState.Configured).connection)
        }
    }

    @Test
    fun `nothing a widget holds could be a credential`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(ProfileId("a"), NOW),
                subject = ConnectionSubject(summary("a")),
            ),
            selected = summary("a"),
            summaries = listOf(summary("a")),
            now = NOW,
        )

        val rendered = state.toString()
        listOf("relay.example.net", "00000000-0000-4000-8000-000000000000").forEach { value ->
            assertFalse(rendered.contains(value), "$value reached the launcher's process")
        }
    }

    @Test
    fun `the chips are the most recently used configurations`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(),
            selected = summary("a", "Home relay"),
            summaries = listOf(
                summary("a", "Home relay"),
                summary("b", "Never used"),
                summary("c", "Used today", lastUsedAt = NOW.minusSeconds(3_600)),
                summary("d", "Used last week", lastUsedAt = NOW.minusSeconds(600_000)),
            ),
            now = NOW,
            zone = ZoneOffset.UTC,
        )

        assertIs<VazieWidgetState.Configured>(state)
        assertEquals(
            listOf("Used today", "Used last week", "Never used"),
            state.shortcuts.map { it.name },
            "recency is the only signal about this person rather than about the list",
        )
    }

    @Test
    fun `the chip row never grows past what the design draws`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(),
            selected = summary("a"),
            summaries = listOf(summary("a")) + (1..8).map { summary("f$it", "Relay $it") },
            now = NOW,
            zone = ZoneOffset.UTC,
        )

        assertEquals(3, (state as VazieWidgetState.Configured).shortcuts.size)
    }

    @Test
    fun `the last-used line describes the configuration on the card, not the newest one`() {
        // The bucket comes from the configuration the widget shows, not the most recently used one.
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(),
            selected = summary("a", "Home relay", lastUsedAt = NOW.minusSeconds(86_400)),
            summaries = listOf(
                summary("a", "Home relay", lastUsedAt = NOW.minusSeconds(86_400)),
                summary("b", "Travel", lastUsedAt = NOW),
            ),
            now = NOW,
            zone = ZoneOffset.UTC,
        )

        assertEquals(LastUsed.Yesterday, (state as VazieWidgetState.Configured).lastUsed)
    }

    @Test
    fun `a configuration that has never carried traffic says so`() {
        val state = widgetState(
            snapshot = VpnConnectionSnapshot(),
            selected = summary("a"),
            summaries = listOf(summary("a")),
            now = NOW,
            zone = ZoneOffset.UTC,
        )

        assertEquals(LastUsed.Never, (state as VazieWidgetState.Configured).lastUsed)
    }

    private fun summary(
        id: String,
        name: String = "Home relay",
        lastUsedAt: Instant? = null,
    ) = ProfileSummary(
        id = ProfileId(id),
        name = name,
        engineId = VpnEngineId.XRAY,
        protocolLabel = "VLESS",
        lastUsedAt = lastUsedAt,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")
    }
}
