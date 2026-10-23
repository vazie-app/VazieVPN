package app.vazie.vpn.widget.dashboard

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import app.vazie.vpn.widget.VazieWidgetStateStore
import app.vazie.vpn.widget.tunnelIsUp
import app.vazie.vpn.widget.presentation.WidgetVariant
import app.vazie.vpn.widget.presentation.widgetLayout
import app.vazie.vpn.widget.dashboard.components.DashboardContent

/** The 4×2 status widget. */
internal object VazieDashboardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            VazieWidgetSizes.wideMin,
            VazieWidgetSizes.wideTarget,
            VazieWidgetSizes.wideTall,
            VazieWidgetSizes.wideRoomy,
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val preferences = currentState<Preferences>()
        val state = VazieWidgetStateStore.read(preferences)
        VazieGlanceTheme(appearance = VazieWidgetStateStore.readAppearance(preferences)) {
            DashboardContent(
                state = state,
                layout = widgetLayout(
                    size = LocalSize.current,
                    variant = WidgetVariant.DASHBOARD,
                    dimens = VazieGlanceTheme.dimens,
                    fontScale = LocalContext.current.resources.configuration.fontScale,
                    tunnelIsUp = state.tunnelIsUp,
                ),
            )
        }
    }
}
