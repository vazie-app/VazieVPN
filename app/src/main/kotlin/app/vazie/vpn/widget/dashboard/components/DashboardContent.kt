package app.vazie.vpn.widget.dashboard.components

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.components.VazieWidgetContent
import app.vazie.vpn.widget.presentation.WidgetLayout

/** The Vazie Dashboard: the state, where it is pointed, one reading, one control - and, when the cell is tall
 * enough that a row of them takes nothing away from the text, the configurations to switch to. */
@Composable
internal fun DashboardContent(
    state: VazieWidgetState,
    layout: WidgetLayout,
    modifier: GlanceModifier = GlanceModifier,
) {
    VazieWidgetContent(state = state, layout = layout, modifier = modifier)
}
