package app.vazie.vpn.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The "Маршрут" type scale: Geologica for interface text, sharpened Geologica for headings, Martian Mono for
 * data. These pin the split, the hierarchy and the numbers taken from the approved screens. */
class VazieTypographyTest {

    private val scale = VazieTypography()

    private fun VazieTypography.headings() = listOf(display, headline, title)
    private fun VazieTypography.interfaceText() =
        listOf(titleSmall, body, bodySecondary, label, labelSmall, caption, button, sectionLabel)
    private fun VazieTypography.technical() =
        listOf(monoDisplay, monoTitle, monoValue, mono, monoSmall, monoMicro)
    private fun VazieTypography.all() = headings() + interfaceText() + technical()

    @Test
    fun `headings use the sharpened Geologica`() {
        scale.headings().forEach { assertSame(VazieFontFamilies.geologicaHeading, it.fontFamily) }
    }

    @Test
    fun `interface text uses Geologica`() {
        scale.interfaceText().forEach { assertSame(VazieFontFamilies.geologica, it.fontFamily) }
    }

    @Test
    fun `technical values use Martian Mono`() {
        scale.technical().forEach { assertSame(VazieFontFamilies.martianMono, it.fontFamily) }
    }

    @Test
    fun `every role states its weight and a line that fits it`() {
        scale.all().forEach { style ->
            assertNotNull(style.fontWeight, "a role without an explicit weight renders at the file default")
            assertTrue(style.lineHeight.value >= style.fontSize.value, "a line height is below its size")
        }
    }

    @Test
    fun `the hierarchy descends`() {
        assertTrue(scale.display.fontSize.value > scale.headline.fontSize.value)
        assertTrue(scale.headline.fontSize.value > scale.title.fontSize.value)
        assertTrue(scale.title.fontSize.value > scale.titleSmall.fontSize.value)
        assertTrue(scale.body.fontSize.value > scale.bodySecondary.fontSize.value)
        assertTrue(scale.monoDisplay.fontSize.value > scale.monoTitle.fontSize.value)
        assertTrue(scale.monoTitle.fontSize.value > scale.monoValue.fontSize.value)
        assertTrue(scale.monoValue.fontSize.value > scale.mono.fontSize.value)
        assertTrue(scale.mono.fontSize.value > scale.monoSmall.fontSize.value)
        assertTrue(scale.monoSmall.fontSize.value > scale.monoMicro.fontSize.value)
    }

    @Test
    fun `the numbers are the approved design's`() {
        assertEquals(34.sp, scale.display.fontSize)
        assertEquals(26.sp, scale.headline.fontSize)
        assertEquals(19.sp, scale.title.fontSize)
        assertEquals(15.sp, scale.body.fontSize)
        assertEquals(13.5.sp, scale.bodySecondary.fontSize)
        assertEquals(16.sp, scale.button.fontSize)
        assertEquals(27.5.sp, scale.monoDisplay.fontSize)
        assertEquals(12.sp, scale.mono.fontSize)
    }

    @Test
    fun `the typefaces are Geologica and Martian Mono`() {
        assertSame(VazieFontFamilies.geologica, DefaultTypefaces.product)
        assertSame(VazieFontFamilies.martianMono, DefaultTypefaces.technical)
    }

    @Test
    fun `no role carries a colour`() {
        scale.all().forEach { style: TextStyle ->
            assertEquals(Color.Unspecified, style.color, "typography must not decide colour")
        }
    }
}
