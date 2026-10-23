package app.vazie.vpn.core.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VazieTokensTest {

    private val spacing = VazieSpacing()

    @Test
    fun `the spacing scale is strictly increasing`() {
        val scale = listOf(
            "xxs" to spacing.xxs, "xs" to spacing.xs, "sm" to spacing.sm, "md" to spacing.md,
            "lg" to spacing.lg, "xl" to spacing.xl, "xxl" to spacing.xxl, "xxxl" to spacing.xxxl,
        )
        scale.zipWithNext { (lowerName, lower), (upperName, upper) ->
            assertTrue(upper > lower, "$upperName must exceed $lowerName")
        }
    }

    @Test
    fun `the minimum touch target meets the platform accessibility floor`() {
        assertTrue(spacing.minTouchTarget.value >= 48f, "minTouchTarget is ${spacing.minTouchTarget}")
    }

    @Test
    fun `border widths are ordered and distinguishable`() {
        val borders = VazieBorders()
        assertTrue(borders.hairline < borders.regular)
        assertTrue(borders.regular < borders.strong)
        // Focus and error are signalled by width as well as colour; steps below half a point are
        // not visible at any density, which would make that signal colour-only again.
        assertTrue((borders.regular - borders.hairline).value >= 0.5f)
        assertTrue((borders.strong - borders.regular).value >= 0.5f)
    }

    @Test
    fun `corner radii are ordered`() {
        val shapes = VazieShapes()
        assertTrue(shapes.smRadius < shapes.mdRadius)
        assertTrue(shapes.mdRadius < shapes.lgRadius)
        assertTrue(shapes.lgRadius < shapes.xlRadius)
    }
}
