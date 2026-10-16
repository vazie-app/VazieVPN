package app.vazie.vpn.widget.presentation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import app.vazie.vpn.core.designsystem.glance.VazieGlanceDimens
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes

/** How much widget there is, and therefore how much widget to draw. */
internal data class WidgetLayout(
    val arrangement: WidgetArrangement,
    val detail: WidgetDetail,
    val shortcutSlots: Int,
    /** The frame left and right, decided by the width that pays for it. See `paddedWidth`. */
    val padding: Dp,
    /** The frame above and below, decided by the height that pays for it. See `paddedHeight`. */
    val verticalPadding: Dp,
    val variant: WidgetVariant,
    /** The connect control's width beside the text, scaled to the font; unused when stacked. See
     * [widgetLayout]. */
    val actionWidth: Dp,
    val tunnelIsUp: Boolean,
    /** Whether the text column can hold a state spelled out in full. */
    val spellsStateInFull: Boolean,
    /** Whether the cell is wide enough for the state word at full size and in full wording. */
    val statesInFull: Boolean,
) {
    val showsServer: Boolean get() = detail != WidgetDetail.STATE_ONLY
    val showsReading: Boolean get() = detail == WidgetDetail.FULL

    /** Whether this cell may name Vazie's other locations. */


}

/** Where the action stands. */
internal enum class WidgetArrangement {
    /** Under the text, full width. The only arrangement a narrow cell has room for. */
    STACKED,

    /** Beside the text. Buys back the height the stacked control would have taken. */
    SPLIT,
}

/** How many lines of text there is room for. */
internal enum class WidgetDetail { STATE_ONLY, STATE_AND_SERVER, FULL }

/** Which widget is asking. The two differ in exactly one thing - whether configuration chips may appear - and
 * that difference is the product decision the picker's two entries describe. */
internal enum class WidgetVariant { QUICK_CONNECT, DASHBOARD }

internal fun widgetLayout(
    size: DpSize,
    variant: WidgetVariant,
    dimens: VazieGlanceDimens,
    fontScale: Float = 1f,
    tunnelIsUp: Boolean = false,
): WidgetLayout {
    val arrangement = when {
        size.width >= VazieWidgetSizes.splitWidth -> WidgetArrangement.SPLIT
        else -> WidgetArrangement.STACKED
    }

    // The horizontal frame follows the width only; see `paddedWidth`.
    val padding = when {
        size.width >= VazieWidgetSizes.paddedWidth -> dimens.widgetPadding
        else -> dimens.widgetPaddingCompact
    }

    // The vertical frame is still the height's business, and the rule is the one it always was: a
    // 110dp cell that keeps 16dp margins loses a line of text to them.
    val roomyVertical = when {
        size.height >= VazieWidgetSizes.paddedHeight -> dimens.widgetPadding
        else -> dimens.widgetPaddingCompact
    }

    // Up and down, a short split dashboard cell gives back four dp at each end. The frame a person reads is
    // the horizontal one - beside the state word, beside the control - and it is untouched.
    val verticalPadding = when {
        arrangement != WidgetArrangement.SPLIT -> roomyVertical
        variant != WidgetVariant.DASHBOARD -> roomyVertical
        size.height >= VazieWidgetSizes.paddedHeight -> roomyVertical
        else -> dimens.widgetPaddingTight
    }

    val stackedCost = dimens.minTouchTarget + dimens.blockGap
    val textRoom = when (arrangement) {
        WidgetArrangement.STACKED -> size.height - verticalPadding - verticalPadding - stackedCost
        WidgetArrangement.SPLIT -> size.height - verticalPadding - verticalPadding
    }

    // Line budgets scale with the font size, so an overflowing line is dropped instead of sliced; the chip
    // row is reserved before the text can use it.
    val chipsRoom = when {
        variant == WidgetVariant.DASHBOARD && size.height >= VazieWidgetSizes.selectableHeight ->
            dimens.blockGap + dimens.rowHeight

        else -> Dp.Hairline
    }


    val railRoom = dimens.railHeight + dimens.blockGap
    val stateRoom = railRoom + dimens.stateLine * fontScale
    val serverLine = dimens.serverLine * fontScale
    val readingLine = dimens.readingLine * fontScale
    val detail = when {
        textRoom >= stateRoom + serverLine + readingLine -> WidgetDetail.FULL
        textRoom >= stateRoom + serverLine -> WidgetDetail.STATE_AND_SERVER
        else -> WidgetDetail.STATE_ONLY
    }

    // Chips are the dashboard's, and only once a row of them fits under a full text block without
    // taking a line away from it - and only in the row `chipsRoom` set aside for them above.
    val chipRoom = textRoom - stateRoom - serverLine - readingLine -
        dimens.blockGap - dimens.rowHeight
    val shortcutSlots = when {
        variant != WidgetVariant.DASHBOARD -> NO_SHORTCUTS
        chipsRoom == Dp.Hairline -> NO_SHORTCUTS
        detail != WidgetDetail.FULL -> NO_SHORTCUTS
        chipRoom < dimens.lineGap -> NO_SHORTCUTS
        size.width >= VazieWidgetSizes.chipRowWidth -> WIDE_SHORTCUTS
        else -> NARROW_SHORTCUTS
    }

    // Fixed per cell, never per state; scales with the font but never below what the state word needs.
    val actionWidth = when (arrangement) {
        WidgetArrangement.STACKED -> dimens.actionWidth
        WidgetArrangement.SPLIT -> {
            val wanted = dimens.actionWidth * fontScale
            val sparable = size.width - padding - padding - dimens.itemGap -
                VazieWidgetSizes.shortStateWidth * fontScale
            maxOf(dimens.actionWidth, minOf(wanted, sparable))
        }
    }

    val textColumn = when (arrangement) {
        WidgetArrangement.SPLIT ->
            size.width - padding - padding - dimens.itemGap - actionWidth

        WidgetArrangement.STACKED -> size.width - padding - padding
    }

    return WidgetLayout(
        arrangement = arrangement,
        detail = detail,
        shortcutSlots = shortcutSlots,
        padding = padding,
        verticalPadding = verticalPadding,
        variant = variant,
        actionWidth = actionWidth,
        tunnelIsUp = tunnelIsUp,
        spellsStateInFull = textColumn >= VazieWidgetSizes.fullStateWidth,
        statesInFull = size.width >= VazieWidgetSizes.roomyWidth,
    )
}


private const val NO_SHORTCUTS = 0
private const val NARROW_SHORTCUTS = 2
private const val WIDE_SHORTCUTS = 3
