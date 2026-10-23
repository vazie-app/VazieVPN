package app.vazie.vpn.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitTunnelTest {

    @Test
    fun `nothing is split until a mode has apps`() {
        assertFalse(SplitTunnel().isActive)
        assertFalse(SplitTunnel(SplitTunnel.Mode.INCLUDE).isActive)
        assertFalse(SplitTunnel(SplitTunnel.Mode.OFF, setOf("com.example.app")).isActive)
        assertTrue(SplitTunnel(SplitTunnel.Mode.EXCLUDE, setOf("com.example.app")).isActive)
    }

    @Test
    fun `stored mode ids read back, and anything else is off`() {
        SplitTunnel.Mode.entries.forEach { assertEquals(it, SplitTunnel.Mode.fromId(it.id)) }
        assertEquals(SplitTunnel.Mode.OFF, SplitTunnel.Mode.fromId("sideways"))
        assertEquals(SplitTunnel.Mode.OFF, SplitTunnel.Mode.fromId(null))
    }

    @Test
    fun `only real package names are accepted`() {
        listOf("com.example.app", "org.telegram.messenger", "ru.sberbankmobile").forEach {
            assertTrue(SplitTunnel.isPackageName(it), it)
        }
        listOf("", "app", "com..example", "1com.example", "com.example.", "com example.app").forEach {
            assertFalse(SplitTunnel.isPackageName(it), it)
        }
    }
}
