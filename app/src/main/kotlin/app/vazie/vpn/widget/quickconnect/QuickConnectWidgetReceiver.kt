package app.vazie.vpn.widget.quickconnect

import androidx.glance.appwidget.GlanceAppWidget
import app.vazie.vpn.widget.VazieWidgetReceiver

/** The manifest entry point for [QuickConnectWidget]. */
class QuickConnectWidgetReceiver : VazieWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = QuickConnectWidget
}
