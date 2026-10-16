package app.vazie.vpn.widget.presentation

import app.vazie.vpn.core.designsystem.glance.VazieGlanceDimens
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import app.vazie.vpn.widget.WidgetConnectionUi
import kotlin.test.Test
import kotlin.test.assertEquals

/** The readings line is where a widget is most tempted to invent data. */
class WidgetReadingsTest {

    private companion object {
        /** The cell with room for a reading, and the cell without one - taken from the layout rather than
         * constructed, so a change to the thresholds is felt here too. */
        val FULL = widgetLayout(
            size = VazieWidgetSizes.wideTarget,
            variant = WidgetVariant.DASHBOARD,
            dimens = VazieGlanceDimens(),
        )
        val SMALLEST = widgetLayout(
            size = VazieWidgetSizes.squareMin,
            variant = WidgetVariant.QUICK_CONNECT,
            dimens = VazieGlanceDimens(),
        )

        /** A 2×2 stretched tall enough to have a third line - and still not a dashboard. */
        val SQUARE_TALL = widgetLayout(
            size = VazieWidgetSizes.squareTall,
            variant = WidgetVariant.QUICK_CONNECT,
            dimens = VazieGlanceDimens(),
        )
    }
}
