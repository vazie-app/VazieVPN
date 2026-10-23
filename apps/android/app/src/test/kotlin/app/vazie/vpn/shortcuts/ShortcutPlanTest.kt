package app.vazie.vpn.shortcuts

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnEngineId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a launcher shows when somebody holds down the Vazie icon. */
class ShortcutPlanTest {

    @Test
    fun `a fresh install offers the one thing there is to do`() {
        val plan = shortcutPlan(profiles = emptyList(), maxShortcuts = 4)

        assertEquals(listOf(VazieShortcut.AddConfiguration), plan)
    }

    @Test
    fun `the most recently used configurations come first`() {
        val plan = shortcutPlan(
            profiles = listOf(
                summary("a", "Home"),
                summary("b", "Travel", lastUsedAt = NOW),
                summary("c", "Backup", lastUsedAt = NOW.minusSeconds(60)),
            ),
            maxShortcuts = 4,
        )

        assertEquals(
            listOf("Travel", "Backup", "Home"),
            plan.filterIsInstance<VazieShortcut.Connect>().map { it.label },
        )
    }

    @Test
    fun `the wizard keeps a slot, and it is the last one`() {
        val plan = shortcutPlan(
            profiles = listOf(summary("a", "Home"), summary("b", "Travel")),
            maxShortcuts = 4,
        )

        assertEquals(VazieShortcut.AddConfiguration, plan.last())
        assertEquals(2, plan.filterIsInstance<VazieShortcut.Connect>().size)
    }

    @Test
    fun `more configurations than slots keeps the most useful ones`() {
        val many = (1..10).map { summary("p$it", "Config $it", lastUsedAt = if (it == 7) NOW else null) }

        val plan = shortcutPlan(profiles = many, maxShortcuts = 4)

        assertTrue(plan.size <= 4, "published more shortcuts than the launcher accepts")
        assertEquals(
            "Config 7",
            plan.filterIsInstance<VazieShortcut.Connect>().first().label,
            "the last used configuration lost its place",
        )
    }

    @Test
    fun `a launcher with one slot spends it on connecting`() {
        val plan = shortcutPlan(
            profiles = listOf(summary("a", "Home")),
            maxShortcuts = 1,
        )

        assertEquals(1, plan.size)
        assertTrue(plan.single() is VazieShortcut.Connect)
    }

    @Test
    fun `a launcher with no slots gets nothing rather than a crash`() {
        assertEquals(emptyList(), shortcutPlan(listOf(summary("a", "Home")), maxShortcuts = 0))
    }

    @Test
    fun `a shortcut id is stable and derived from the profile`() {
        val plan = shortcutPlan(listOf(summary("profile-1", "Home server")), 4)

        assertEquals(
            "vazie.shortcut.profile.profile-1",
            plan.filterIsInstance<VazieShortcut.Connect>().single().id,
        )
        assertEquals("vazie.shortcut.add_config", VazieShortcut.AddConfiguration.id)
    }

    @Test
    fun `a name that looks like a server address never becomes a label`() {
        val dangerous = listOf(
            "vless" + "://" + "00000000-0000-4000-8000-000000000000@relay.example.net:443",
            "relay.example.net",
            "user@relay.example.net",
            "203.0.113.7",
            "00000000-0000-4000-8000-000000000000",
        )

        dangerous.forEach { name ->
            assertNull(safeShortcutLabel(name), "a launcher would have stored: $name")
        }
    }

    @Test
    fun `a configuration with an unsafe name is skipped, not renamed`() {
        val plan = shortcutPlan(
            profiles = listOf(summary("a", "relay.example.net"), summary("b", "Home")),
            maxShortcuts = 4,
        )

        assertEquals(
            listOf("Home"),
            plan.filterIsInstance<VazieShortcut.Connect>().map { it.label },
        )
    }

    @Test
    fun `an ordinary name is kept, tidied and clipped`() {
        assertEquals("Home relay", safeShortcutLabel("  Home   relay "))
        assertEquals("Домашний сервер", safeShortcutLabel("Домашний сервер"))
        assertNull(safeShortcutLabel("   "))

        val long = safeShortcutLabel("A configuration with a very long name indeed")
        assertTrue(long!!.length <= 20, "a label a launcher would clip mid-word: $long")
        assertTrue(long.endsWith("…"))
    }

    private fun summary(id: String, name: String, lastUsedAt: Instant? = null) = ProfileSummary(
        id = ProfileId(id),
        name = name,
        engineId = VpnEngineId.XRAY,
        protocolLabel = "VLESS",
        origin = ProfileOrigin.IMPORTED_LINK,
        lastUsedAt = lastUsedAt,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-28T12:00:00Z")
    }
}
