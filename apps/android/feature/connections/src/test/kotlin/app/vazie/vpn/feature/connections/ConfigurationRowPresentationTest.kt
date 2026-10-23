package app.vazie.vpn.feature.connections

import app.vazie.vpn.core.model.LastUsed
import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.feature.connections.components.labelRes
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnEngineId
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigurationRowPresentationTest {

    @Test
    fun `every row state has its own label`() {
        val labels = ConfigurationStatusUi.entries.map { it.labelRes() }
        assertEquals(labels.size, labels.toSet().size, "two row states share a label")
    }

    @Test
    fun `an unavailable configuration stays in the list`() {
        val unavailable = ConnectionsFixtures.populated.configurations
            .filter { it.status == ConfigurationStatusUi.UNAVAILABLE }

        assertTrue(unavailable.isNotEmpty(), "the fixture no longer covers an unavailable row")
        assertTrue(unavailable.all { !it.enabled }, "an unavailable row must not be interactive")
    }

    @Test
    fun `the empty state is empty and the populated one is not`() {
        assertTrue(ConnectionsFixtures.empty.isEmpty)
        assertTrue(!ConnectionsFixtures.populated.isEmpty)
    }

    /** The clock is fixed and in UTC — a bucket decided by the machine the test runs on is a test that fails
     * once a day somewhere. */
    @Test
    fun `the last use survives the mapping`() {
        val now = Instant.parse("2026-01-15T12:00:00Z")
        val rows = listOf(
            ProfileSummary(
                id = ProfileId("used"),
                name = "Home relay",
                engineId = VpnEngineId.XRAY,
                protocolLabel = "VLESS",
                lastUsedAt = now.minusSeconds(60),
            ),
            ProfileSummary(
                id = ProfileId("never"),
                name = "Office tunnel",
                engineId = VpnEngineId.XRAY,
                protocolLabel = "VLESS",
            ),
        ).toRows(
            selected = null,
            snapshot = VpnConnectionSnapshot(),
            now = now,
            zone = ZoneOffset.UTC,
        )

        assertEquals(LastUsed.JustNow, rows[0].lastUsed)
        assertEquals(LastUsed.Never, rows[1].lastUsed, "a profile never used claimed a session")
    }

    @Test
    fun `configuration ids are unique, because a row is opened by its id`() {
        val ids = ConnectionsFixtures.populated.configurations.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
