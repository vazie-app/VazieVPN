package app.vazie.vpn.widget.dashboard

import androidx.glance.appwidget.GlanceAppWidget
import app.vazie.vpn.widget.VazieWidgetReceiver

/** The manifest entry point for [VazieDashboardWidget]. */
class VazieDashboardWidgetReceiver : VazieWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = VazieDashboardWidget
}
