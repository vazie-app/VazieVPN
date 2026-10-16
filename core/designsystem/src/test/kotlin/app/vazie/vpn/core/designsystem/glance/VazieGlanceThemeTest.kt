package app.vazie.vpn.core.designsystem.glance

import androidx.glance.text.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The widget scale: readings keep one shape, and the state word outranks the configuration. */
class VazieGlanceThemeTest {

    private val typography = VazieGlanceTypography()

    @Test
    fun `readings are monospace`() {
        // A value someone compares between two screens must not change shape.
        assertEquals(FontFamily.Monospace, typography.technical.fontFamily)
    }

    /** The widget hierarchy: what Vazie is doing, then with which configuration. */
    @Test
    fun `the state word outranks the configuration`() {
        val state = typography.state.fontSize!!.value
        val compact = typography.stateCompact.fontSize!!.value
        val server = typography.server.fontSize!!.value
        val technical = typography.technical.fontSize!!.value

        assertTrue(state > server, "the state is set no larger than the configuration")
        assertTrue(server > technical, "the configuration is set no larger than the reading")
        assertEquals(technical, typography.reading.fontSize!!.value, "prose and measured readings differ")
        assertTrue(compact >= server, "the compact state shrinks below the configuration")
    }

    @Test
    fun `the widget keeps the touch target`() {
        val dimens = VazieGlanceDimens()
        assertTrue(dimens.minTouchTarget.value >= 48f, "minTouchTarget is ${dimens.minTouchTarget}")
    }
}
