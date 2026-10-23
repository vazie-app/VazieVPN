package app.vazie.vpn.tour

import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import kotlin.test.Test
import kotlin.test.assertEquals

/** The first-run tour points at the three things on the "Маршрут" Home, in order, and at nothing that used to
 * live in the bottom bar. */
class VazieTourSequenceTest {

    @Test
    fun `the tour is the connect control, the connection card and the settings gear`() {
        assertEquals(
            listOf(VazieTourTargetId.CONNECT_CONTROL, VazieTourTargetId.CONNECTION_CARD, VazieTourTargetId.SETTINGS_GEAR),
            vazieTourSequence().map { it.target },
        )
    }

    @Test
    fun `no tour target is a bottom bar tab any more`() {
        assertEquals(
            setOf(
                "CONNECT_CONTROL", "CONNECTION_CARD", "SETTINGS_GEAR", "CONFIGURATION_ROW",
                "SETTINGS_APPEARANCE", "SETTINGS_SPLIT_TUNNEL", "SETTINGS_GUIDES", "SETTINGS_REPORT_BUG",
            ),
            VazieTourTargetId.entries.map { it.name }.toSet(),
        )
    }
}
