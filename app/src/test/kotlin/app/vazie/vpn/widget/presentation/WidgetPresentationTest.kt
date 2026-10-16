package app.vazie.vpn.widget.presentation

import app.vazie.vpn.R
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.WidgetConnectionUi
import app.vazie.vpn.widget.WidgetFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The state mapping, and the property the design is built on: that no two states a person needs to tell
 * apart are told apart by colour alone. */
class WidgetPresentationTest {

    @Test
    fun `connected and failed never look alike with the colour taken away`() {
        val connected = presentationOf(WidgetConnectionUi.Connected)
        val failed = presentationOf(WidgetConnectionUi.Failed)

        assertNotEquals(connected.phase, failed.phase, "the rail draws them the same")
        assertNotEquals(connected.statusRes, failed.statusRes)
        assertNotEquals(connected.actionStyle, failed.actionStyle, "the control looks the same")
    }

    @Test
    fun `disconnected and failed never look alike with the colour taken away`() {
        val idle = presentationOf(WidgetConnectionUi.Idle)
        val failed = presentationOf(WidgetConnectionUi.Failed)

        assertNotEquals(idle.phase, failed.phase)
        assertNotEquals(idle.statusRes, failed.statusRes)
    }

    @Test
    fun `disconnected and connected never look alike with the colour taken away`() {
        val idle = presentationOf(WidgetConnectionUi.Idle)
        val connected = presentationOf(WidgetConnectionUi.Connected)

        assertNotEquals(idle.phase, connected.phase)
        assertNotEquals(idle.statusRes, connected.statusRes)
        assertNotEquals(idle.actionStyle, connected.actionStyle)
    }

    @Test
    fun `disconnected is not dressed as a failure`() {
        val idle = presentationOf(WidgetConnectionUi.Idle)
        assertEquals(WidgetTone.NEUTRAL, idle.tone, "not protected is a state, not an error")
        assertEquals(WidgetPhase.IDLE, idle.phase)
        assertEquals(R.string.widget_action_connect, idle.actionRes)
        assertEquals(WidgetActionStyle.FILLED, idle.actionStyle, "connect is the obvious next step")
    }

    @Test
    fun `connecting is the product's own accent rather than a warning`() {
        assertEquals(WidgetTone.INFO, presentationOf(WidgetConnectionUi.Preparing).tone)
        assertEquals(WidgetTone.INFO, presentationOf(WidgetConnectionUi.Connecting).tone)
    }

    @Test
    fun `a transitional state is visibly between the two it sits between`() {
        val idle = presentationOf(WidgetConnectionUi.Idle).phase
        val preparing = presentationOf(WidgetConnectionUi.Preparing).phase
        val connecting = presentationOf(WidgetConnectionUi.Connecting).phase
        val connected = presentationOf(WidgetConnectionUi.Connected).phase

        val lit = { phase: WidgetPhase -> phase.segments.count { it } }
        assertTrue(
            lit(idle) < lit(preparing) && lit(preparing) < lit(connecting) &&
                lit(connecting) < lit(connected),
            "the rail does not count towards connected",
        )
    }

    @Test
    fun `failure offers a way out rather than only an apology`() {
        val failed = presentationOf(WidgetConnectionUi.Failed)
        assertEquals(R.string.widget_action_retry, failed.actionRes)
        assertEquals(WidgetAction.Connect, failed.action)
        assertEquals(WidgetActionStyle.FILLED, failed.actionStyle)
    }

    @Test
    fun `a widget with nothing configured sends the person where configurations are`() {
        val presentation = widgetPresentation(VazieWidgetState.NoConfiguration)
        assertEquals(WidgetAction.OpenConnections, presentation.action)
        assertEquals(R.string.connection_status_no_configuration, presentation.statusRes)
    }

    @Test
    fun `every state says something and offers something`() {
        ALL.forEach { state ->
            val presentation = widgetPresentation(state)
            assertTrue(presentation.statusRes != 0, "$state has no word")
            assertTrue(presentation.actionRes != 0, "$state has no control")
            assertEquals(
                RAIL_SEGMENTS,
                presentation.phase.segments.size,
                "$state draws a rail of a different length",
            )
        }
    }

    @Test
    fun `only a live tunnel offers to take itself down`() {
        val takesDown = ALL.filter { widgetPresentation(it).action == WidgetAction.Disconnect }
        assertEquals(
            setOf(
                WidgetConnectionUi.Preparing,
                WidgetConnectionUi.Connecting,
                WidgetConnectionUi.Connected,
            ),
            takesDown.filterIsInstance<VazieWidgetState.Configured>().map { it.connection }.toSet(),
        )
    }

    private fun presentationOf(connection: WidgetConnectionUi) =
        widgetPresentation(WidgetFixtures.configured(connection))

    private companion object {
        const val RAIL_SEGMENTS = 3
        val ALL = WidgetFixtures.allStates
    }
}
