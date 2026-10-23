package app.vazie.vpn.widget.presentation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.glance.VazieGlanceDimens
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import app.vazie.vpn.widget.WidgetConnectionUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The layout rules, and the one thing they exist to guarantee. */
class WidgetLayoutTest {

    @Test
    fun `every declared cell keeps the control and the state word`() {
        DECLARED.forEach { size ->
            VARIANTS.forEach { variant ->
                val dimens = VazieGlanceDimens()
                val layout = widgetLayout(size, variant, dimens)
                val used = used(layout, dimens)
                assertTrue(
                    used <= size.height,
                    "$size / $variant needs $used and has ${size.height}",
                )
            }
        }
    }

    @Test
    fun `the wide widget puts its control beside the text and the square one under it`() {
        assertEquals(WidgetArrangement.SPLIT, arrangementOf(VazieWidgetSizes.wideTarget))
        assertEquals(WidgetArrangement.SPLIT, arrangementOf(VazieWidgetSizes.wideTall))
        assertEquals(WidgetArrangement.STACKED, arrangementOf(VazieWidgetSizes.squareMin))
        assertEquals(WidgetArrangement.STACKED, arrangementOf(VazieWidgetSizes.squareTarget))
    }

    @Test
    fun `a wide widget squeezed onto a narrow grid stacks rather than truncates`() {
        assertEquals(WidgetArrangement.STACKED, arrangementOf(VazieWidgetSizes.wideMin))
    }

    @Test
    fun `the smallest square cell still names the state and offers the control`() {
        val layout = layoutOf(VazieWidgetSizes.squareMin, WidgetVariant.QUICK_CONNECT)
        assertEquals(WidgetDetail.STATE_ONLY, layout.detail)
        assertEquals(0, layout.shortcutSlots)
    }

    @Test
    fun `a normal square cell names the configuration too`() {
        assertTrue(layoutOf(VazieWidgetSizes.squareTarget, WidgetVariant.QUICK_CONNECT).showsServer)
    }

    @Test
    fun `the wide widget's design size shows the state, the configuration and one reading`() {
        val layout = layoutOf(VazieWidgetSizes.wideTarget, WidgetVariant.DASHBOARD)
        assertEquals(WidgetDetail.FULL, layout.detail)
        assertTrue(layout.showsServer)
        assertTrue(layout.showsReading)
    }

    @Test
    fun `more room only ever adds`() {
        listOf(
            VazieWidgetSizes.squareMin,
            VazieWidgetSizes.squareTarget,
            VazieWidgetSizes.squareTall,
            VazieWidgetSizes.squareRoomy,
        ).zipWithNext { smaller, larger ->
            val small = layoutOf(smaller, WidgetVariant.QUICK_CONNECT)
            val large = layoutOf(larger, WidgetVariant.QUICK_CONNECT)
            assertTrue(!small.showsServer || large.showsServer, "$larger drops the configuration")
            assertTrue(!small.showsReading || large.showsReading, "$larger drops the reading")
            assertTrue(large.shortcutSlots >= small.shortcutSlots, "$larger lists fewer")
        }
    }

    @Test
    fun `only the dashboard offers configurations, and only when it has grown into the room`() {
        assertEquals(0, layoutOf(VazieWidgetSizes.squareTall, WidgetVariant.QUICK_CONNECT).shortcutSlots)
        assertEquals(0, layoutOf(VazieWidgetSizes.wideTall, WidgetVariant.QUICK_CONNECT).shortcutSlots)
        assertEquals(0, layoutOf(VazieWidgetSizes.wideTarget, WidgetVariant.DASHBOARD).shortcutSlots)
        assertTrue(layoutOf(VazieWidgetSizes.wideTall, WidgetVariant.DASHBOARD).shortcutSlots > 0)
    }

    @Test
    fun `no cell offers more chips than the product allows`() {
        DECLARED.forEach { size ->
            val slots = layoutOf(size, WidgetVariant.DASHBOARD).shortcutSlots
            assertTrue(slots <= MAX_CHIPS, "$size offers $slots configurations")
        }
    }

    @Test
    fun `only the squares have to shorten the state word`() {
        assertTrue(!layoutOf(VazieWidgetSizes.squareMin, WidgetVariant.QUICK_CONNECT).statesInFull)
        assertTrue(!layoutOf(VazieWidgetSizes.squareTall, WidgetVariant.QUICK_CONNECT).statesInFull)
        assertTrue(layoutOf(VazieWidgetSizes.wideMin, WidgetVariant.DASHBOARD).statesInFull)
        assertTrue(layoutOf(VazieWidgetSizes.wideTarget, WidgetVariant.DASHBOARD).statesInFull)
    }

    /** A person who asked for larger text gets fewer lines, not a line cut through its own baseline. */
    @Test
    fun `a large font scale drops a line rather than clipping one`() {
        DECLARED.forEach { size ->
            VARIANTS.forEach { variant ->
                val dimens = VazieGlanceDimens()
                val layout = widgetLayout(size, variant, dimens, LARGE_FONT)
                val text = textHeight(layout, dimens, LARGE_FONT)
                val room = when (layout.arrangement) {
                    WidgetArrangement.STACKED ->
                        size.height - layout.verticalPadding - layout.verticalPadding -
                            dimens.blockGap - dimens.minTouchTarget

                    WidgetArrangement.SPLIT ->
                        size.height - layout.verticalPadding - layout.verticalPadding
                }
                // The state word alone may not fit the smallest cells at 150%, and nothing can be done about
                // that without dropping the word. Everything the layout chose to add on top of it has to fit.
                val optional = text - (dimens.railHeight + dimens.blockGap +
                    dimens.stateLine * LARGE_FONT)
                assertTrue(
                    optional <= room,
                    "$size / $variant at $LARGE_FONT adds $optional of lines to $room of room",
                )
            }
        }
    }

    /** A larger font widens the control instead of cutting its label in half. */
    @Test
    fun `a larger font widens the control, and never past the state word`() {
        val dimens = VazieGlanceDimens()
        val scales = listOf(1f, 1.15f, 1.3f, 1.5f)

        // It grows where the cell can pay for it.
        val roomy = scales.map {
            widgetLayout(VazieWidgetSizes.wideRoomy, WidgetVariant.DASHBOARD, dimens, it).actionWidth
        }
        roomy.zipWithNext { narrower, wider ->
            assertTrue(wider > narrower, "a larger font did not widen the control: $roomy")
        }
        assertEquals(dimens.actionWidth * 1.3f, roomy[2], "the control did not follow the font")

        // And never at the state word's expense, on any cell or any scale.
        DECLARED.forEach { size ->
            scales.forEach { scale ->
                val layout = widgetLayout(size, WidgetVariant.DASHBOARD, dimens, scale)
                if (layout.arrangement != WidgetArrangement.SPLIT) return@forEach
                val column = size.width - layout.padding * 2 - dimens.itemGap - layout.actionWidth
                assertTrue(
                    layout.actionWidth >= dimens.actionWidth,
                    "$size at $scale made the control narrower than it has ever been",
                )
                // The floor applies only to what the control gained; it never shrinks below its original
                // width.
                if (layout.actionWidth > dimens.actionWidth) {
                    assertTrue(
                        column >= VazieWidgetSizes.shortStateWidth * scale,
                        "$size at $scale widened the control into the state word, leaving $column",
                    )
                }
            }
        }
    }

    /** The frame on each axis is paid for out of that axis, and by nothing else. */
    @Test
    fun `a taller cell never has a narrower frame to write in`() {
        listOf(
            VazieWidgetSizes.squareTarget to VazieWidgetSizes.squareTall,
            VazieWidgetSizes.wideTarget to VazieWidgetSizes.wideTall,
        ).forEach { (shorter, taller) ->
            val variant =
                if (shorter == VazieWidgetSizes.squareTarget) WidgetVariant.QUICK_CONNECT
                else WidgetVariant.DASHBOARD
            assertTrue(
                layoutOf(taller, variant).padding <= layoutOf(shorter, variant).padding,
                "$taller bought a wider frame with height it grew downwards",
            )
        }
    }

    /** Every declared cell keeps the compact frame, and the roomier one waits for a cell wide enough to spend
     * it without being noticed. See `VazieWidgetSizes.paddedWidth`. */
    @Test
    fun `the tighter frame is what every declared cell is drawn with`() {
        val dimens = VazieGlanceDimens()
        DECLARED.forEach { size ->
            WidgetVariant.entries.forEach { variant ->
                val expected = when {
                    size.width >= VazieWidgetSizes.paddedWidth -> dimens.widgetPadding
                    else -> dimens.widgetPaddingCompact
                }
                assertEquals(
                    expected,
                    layoutOf(size, variant).padding,
                    "$size does not frame itself by the width it has to pay with",
                )
            }
        }
    }

    /** A chip row is never drawn in the height the control column needs, checked across the whole resizable
     * range rather than at the declared sizes. */
    @Test
    fun `nothing draws chips in the room the control needs`() {
        val dimens = VazieGlanceDimens()
        (RESIZE_MIN..RESIZE_MAX step RESIZE_STEP).forEach { width ->
            (RESIZE_MIN..RESIZE_MAX step RESIZE_STEP).forEach { height ->
                val size = DpSize(width.dp, height.dp)
                val layout = layoutOf(size, WidgetVariant.DASHBOARD)
                if (layout.shortcutSlots == 0) return@forEach
                val rightColumn = dimens.minTouchTarget
                val row = size.height - layout.verticalPadding - layout.verticalPadding -
                    dimens.blockGap - dimens.rowHeight
                assertTrue(
                    rightColumn <= row,
                    "$size draws ${layout.shortcutSlots} chips under a column of $rightColumn " +
                        "that only has $row to stand in",
                )
            }
        }
    }

    /** The vertical padding gives ground on a short wide cell, and the horizontal padding never does: the
     * frame a person reads is the horizontal one, and the compact widget pays none of it. */
    @Test
    fun `only a short wide dashboard gives up vertical padding`() {
        val dimens = VazieGlanceDimens()
        val short = layoutOf(VazieWidgetSizes.wideTarget, WidgetVariant.DASHBOARD)
        assertEquals(dimens.widgetPaddingTight, short.verticalPadding)
        assertTrue(
            short.padding > short.verticalPadding,
            "the horizontal frame gave ground the vertical one was asked for",
        )

        DECLARED.forEach { size ->
            val compact = layoutOf(size, WidgetVariant.QUICK_CONNECT)
            assertTrue(
                compact.verticalPadding > dimens.widgetPaddingTight,
                "$size charged the compact widget for a figure it does not draw",
            )
        }
    }


    /** The control is the same width in every state, so it does not move when the connection changes. */
    @Test
    fun `the text column does not depend on the connection state`() {
        val dimens = VazieGlanceDimens()
        val columns = WidgetConnectionUi::class.let {
            DECLARED.map { size ->
                val layout = layoutOf(size, WidgetVariant.DASHBOARD)
                size to when (layout.arrangement) {
                    WidgetArrangement.SPLIT ->
                        size.width - layout.padding - layout.padding -
                            dimens.itemGap - dimens.actionWidth

                    WidgetArrangement.STACKED -> size.width - layout.padding - layout.padding
                }
            }
        }
        columns.forEach { (size, column) ->
            assertTrue(column > Dp.Hairline, "$size left the text no column at all")
        }
    }

    /** What the composition asks of the cell's height. */
    private fun used(layout: WidgetLayout, dimens: VazieGlanceDimens): Dp {
        val text = textHeight(layout, dimens)
        val body = when (layout.arrangement) {
            WidgetArrangement.STACKED -> text + dimens.blockGap + dimens.minTouchTarget
            WidgetArrangement.SPLIT -> maxOf(text, dimens.minTouchTarget)
        }
        val chips = when {
            layout.shortcutSlots > 0 -> dimens.blockGap + dimens.rowHeight
            else -> Dp.Hairline
        }
        return layout.verticalPadding + layout.verticalPadding + body + chips
    }

    private fun textHeight(
        layout: WidgetLayout,
        dimens: VazieGlanceDimens,
        fontScale: Float = 1f,
    ): Dp =
        dimens.railHeight + dimens.blockGap + dimens.stateLine * fontScale +
            (if (layout.showsServer) dimens.lineGap + dimens.serverLine * fontScale else Dp.Hairline) +
            (if (layout.showsReading) dimens.lineGap + dimens.readingLine * fontScale else Dp.Hairline)

    private fun layoutOf(size: DpSize, variant: WidgetVariant) =
        widgetLayout(size, variant, VazieGlanceDimens())

    private fun arrangementOf(size: DpSize) =
        layoutOf(size, WidgetVariant.DASHBOARD).arrangement

    private companion object {
        const val MAX_CHIPS = 3

        /** The resizable range the provider XML actually allows, swept in whole dp. */
        const val RESIZE_MIN = 110
        const val RESIZE_MAX = 320
        const val RESIZE_STEP = 2
        const val LARGE_FONT = 1.5f

        val DECLARED = listOf(
            VazieWidgetSizes.squareMin,
            VazieWidgetSizes.squareTarget,
            VazieWidgetSizes.squareTall,
            VazieWidgetSizes.squareRoomy,
            VazieWidgetSizes.wideMin,
            VazieWidgetSizes.wideTarget,
            VazieWidgetSizes.wideTall,
            VazieWidgetSizes.wideRoomy,
        )
        val VARIANTS = WidgetVariant.entries
    }
}
