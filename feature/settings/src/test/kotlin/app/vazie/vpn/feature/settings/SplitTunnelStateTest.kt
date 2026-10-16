package app.vazie.vpn.feature.settings

import app.vazie.vpn.core.model.SplitTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

class SplitTunnelStateTest {

    @Test
    fun `toggling adds and removes an app, and changing the mode keeps the apps`() {
        val start = SplitTunnel(SplitTunnel.Mode.EXCLUDE)
        val one = start.after(SplitTunnelAction.ToggleApp("com.example.bank"))
        assertEquals(setOf("com.example.bank"), one.packages)
        assertEquals(emptySet(), one.after(SplitTunnelAction.ToggleApp("com.example.bank")).packages)
        assertEquals(SplitTunnel(SplitTunnel.Mode.INCLUDE, setOf("com.example.bank")), one.after(SplitTunnelAction.SelectMode(SplitTunnel.Mode.INCLUDE)))
    }

    @Test
    fun `search matches names and packages, ignoring case`() {
        val state = SplitTunnelUiState(
            split = SplitTunnel(),
            apps = persistentListOf(LaunchableApp("com.example.bank", "Bank"), LaunchableApp("org.example.maps", "Maps")),
        )
        assertEquals(listOf("Bank"), state.copy(query = "ban").visibleApps.map { it.label })
        assertEquals(listOf("Maps"), state.copy(query = "ORG.EXAMPLE").visibleApps.map { it.label })
        assertEquals(2, state.copy(query = "  ").visibleApps.size)
    }
}
