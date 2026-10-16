package app.vazie.vpn.widget

import app.vazie.vpn.core.designsystem.theme.Appearance
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import app.vazie.vpn.widget.dashboard.VazieDashboardWidget
import app.vazie.vpn.widget.quickconnect.QuickConnectWidget

/** The one way widget state changes. */
internal object VazieWidgetUpdater {

    private val widgets: List<GlanceAppWidget>
        get() = listOf(QuickConnectWidget, VazieDashboardWidget)

    /** Writes [state] into every placed Vazie widget and repaints them. */
    suspend fun publish(context: Context, state: VazieWidgetState, appearance: Appearance = Appearance.Default) {
        val manager = GlanceAppWidgetManager(context)
        widgets.forEach { widget ->
            manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { preferences ->
                    VazieWidgetStateStore.write(preferences, state, appearance)
                }
            }
            widget.updateAll(context)
        }
    }
}
