package app.vazie.vpn.core.designsystem.component

import app.vazie.vpn.core.designsystem.theme.NightIndigoColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Status is the one place where a colour clash actually misleads: two tones resolving to the same indicator
 * would make "connected" and "reconnecting" look identical. */
class StatusPaletteTest {

    private val appearances = listOf(
        "Night Indigo" to NightIndigoColors,
    )

    @Test
    fun `every tone resolves to a distinct indicator`() {
        appearances.forEach { (name, colors) ->
            val indicators = VazieStatusTone.entries.map { statusPalette(it, colors).indicator }
            assertEquals(VazieStatusTone.entries.size, indicators.toSet().size, name)
        }
    }

    @Test
    fun `labels use the accessible text roles, not the indicator fills`() {
        // The fills are chosen to read as dots; the *Text roles are the ones the contrast test
        // covers. Pointing a label at a fill is how the palette quietly loses AA.
        appearances.forEach { (name, colors) ->
            listOf(
                VazieStatusTone.Success to colors.successText,
                VazieStatusTone.Warning to colors.warningText,
                VazieStatusTone.Error to colors.errorText,
                VazieStatusTone.Info to colors.primaryText,
                VazieStatusTone.Neutral to colors.textSecondary,
            ).forEach { (tone, expected) ->
                assertEquals(expected, statusPalette(tone, colors).label, "$name $tone")
            }
        }
    }

    @Test
    fun `every resolved colour is opaque`() {
        appearances.forEach { (name, colors) ->
            VazieStatusTone.entries.forEach { tone ->
                val palette = statusPalette(tone, colors)
                assertTrue(palette.indicator.alpha == 1f, "$name $tone indicator")
                assertTrue(palette.label.alpha == 1f, "$name $tone label")
            }
        }
    }
}
