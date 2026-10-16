package app.vazie.vpn.core.designsystem.component

import androidx.compose.ui.graphics.Color
import app.vazie.vpn.core.designsystem.theme.NightIndigoColors
import app.vazie.vpn.core.designsystem.theme.VazieColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Properties every variant must keep in every appearance, rather than exact colours. */
class ButtonPaletteTest {

    private val appearances = listOf(
        "Night Indigo" to NightIndigoColors,
    )

    private fun forEachAppearance(block: (String, VazieColors) -> Unit) =
        appearances.forEach { (name, colors) -> block(name, colors) }

    @Test
    fun `filled variants are the only ones with a container`() {
        forEachAppearance { name, colors ->
            VazieButtonVariant.entries.forEach { variant ->
                val palette = buttonPalette(variant, colors, enabled = true)
                if (variant.isFilled) {
                    assertNotEquals(Color.Transparent, palette.container, "$name $variant")
                } else {
                    assertEquals(Color.Transparent, palette.container, "$name $variant")
                }
            }
        }
    }

    @Test
    fun `no variant is outlined — surfaces differ by tone, not by borders`() {
        forEachAppearance { name, colors ->
            VazieButtonVariant.entries.forEach { variant ->
                assertNull(buttonPalette(variant, colors, enabled = true).border, "$name $variant")
            }
        }
    }

    @Test
    fun `primary is the action colour, and only primary`() {
        forEachAppearance { name, colors ->
            assertEquals(colors.action, buttonPalette(VazieButtonVariant.Primary, colors, enabled = true).container, name)
            VazieButtonVariant.entries.filter { it != VazieButtonVariant.Primary }.forEach { variant ->
                assertNotEquals(colors.action, buttonPalette(variant, colors, enabled = true).container, "$name $variant")
            }
        }
    }

    @Test
    fun `disabled never reuses an enabled colour`() {
        forEachAppearance { name, colors ->
            VazieButtonVariant.entries.forEach { variant ->
                val enabled = buttonPalette(variant, colors, enabled = true)
                val disabled = buttonPalette(variant, colors, enabled = false)
                assertNotEquals(enabled.content, disabled.content, "$name $variant content")
                assertEquals(colors.textDisabled, disabled.content, "$name $variant content token")
                assertNull(disabled.border, "$name $variant border")
                if (variant.isFilled) {
                    assertEquals(colors.disabled, disabled.container, "$name $variant container")
                }
            }
        }
    }

    @Test
    fun `primary and destructive stay visually distinct`() {
        forEachAppearance { name, colors ->
            val primary = buttonPalette(VazieButtonVariant.Primary, colors, enabled = true)
            val destructive = buttonPalette(VazieButtonVariant.Destructive, colors, enabled = true)
            assertNotEquals(primary.container, destructive.container, name)
        }
    }

    @Test
    fun `every resolved colour is opaque`() {
        // A translucent container would sample whatever sits behind it, which is exactly how a
        // button stops meeting the contrast the palette was verified at.
        forEachAppearance { name, colors ->
            VazieButtonVariant.entries.forEach { variant ->
                listOf(true, false).forEach { enabled ->
                    val palette = buttonPalette(variant, colors, enabled)
                    assertEquals(1f, palette.content.alpha, "$name $variant enabled=$enabled")
                    val opaqueContainer =
                        palette.container == Color.Transparent || palette.container.alpha == 1f
                    assertTrue(opaqueContainer, "$name $variant enabled=$enabled container")
                }
            }
        }
    }
}
