package app.vazie.vpn.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The two palettes of the "Маршрут" design, and how a stored choice is read back. */
class AppearanceTest {

    @Test
    fun `there are exactly two appearances`() {
        assertEquals(listOf(Appearance.NIGHT_INDIGO, Appearance.MILK), Appearance.entries)
        assertEquals(Appearance.NIGHT_INDIGO, Appearance.Default)
    }

    @Test
    fun `each appearance paints its own palette`() {
        assertSame(NightIndigoColors, colorsFor(Appearance.NIGHT_INDIGO))
        assertSame(MilkColors, colorsFor(Appearance.MILK))
    }

    /** Legacy palettes, `SYSTEM`, unknown names and nothing at all open as Night Indigo; only `MILK` reads as
     * Milk. The reader must never throw: an old installation has to open. */
    @Test
    fun `a stored name reads back safely`() {
        assertEquals(Appearance.MILK, Appearance.fromStoredName("MILK"))
        assertEquals(Appearance.NIGHT_INDIGO, Appearance.fromStoredName("NIGHT_INDIGO"))
        listOf(
            "LAVENDER", "MIDNIGHT", "SAKURA", "SAGE", "ABYSS", "PEACH", "HOLOGRAM", "OBSIDIAN", "MINT",
            "AUBERGINE", "EMBER", "CLOUD", "SYSTEM", "milk", "", "SOMETHING_FROM_A_NEWER_BUILD", null,
        ).forEach { name ->
            assertEquals(Appearance.NIGHT_INDIGO, Appearance.fromStoredName(name), "stored=$name")
        }
    }

    @Test
    fun `Night Indigo is the approved dark palette`() {
        val c = NightIndigoColors
        assertTrue(c.isDark)
        assertEquals(Color(0xFF0D1020), c.background, "Night")
        assertEquals(Color(0xFF151932), c.surface, "Surface")
        assertEquals(Color(0xFF262C54), c.surfaceMuted, "Raised")
        assertEquals(Color(0xFF9DB0FF), c.routeStart, "Route start")
        assertEquals(Color(0xFFC9B6F2), c.routeEnd, "Route end")
        assertEquals(Color(0xFF2B3163), c.routeSoft, "Route soft")
        assertEquals(Color(0xFFF1E3C8), c.action, "Act")
        assertEquals(Color(0xFFF0A77F), c.error, "Clay")
    }

    @Test
    fun `Milk is the approved light palette`() {
        val c = MilkColors
        assertFalse(c.isDark)
        assertEquals(Color(0xFFFAF8F4), c.background, "Milk")
        assertEquals(Color(0xFFFFFEFC), c.surface, "Card")
        assertEquals(Color(0xFF171A2E), c.textPrimary, "Ink")
        assertEquals(Color(0xFF4E5470), c.textSecondary, "Ink secondary")
        assertEquals(Color(0xFF4A5BC4), c.routeStart, "Route start")
        assertEquals(Color(0xFF5D43A8), c.routeEnd, "Route end")
        assertEquals(Color(0xFFE4E7F8), c.routeSoft, "Route soft")
        assertEquals(Color(0xFF1D1F3A), c.action, "Act")
        assertEquals(Color(0xFFF6EEDD), c.onAction, "On act")
    }

    @Test
    fun `indigo is state and the action colour is its own, in both palettes`() {
        Appearance.entries.map(::colorsFor).forEach { c ->
            assertEquals(c.routeStart, c.primary, "state and selection are the route colour")
            assertTrue(c.action != c.primary, "the action colour must not be the state colour")
        }
    }
}
