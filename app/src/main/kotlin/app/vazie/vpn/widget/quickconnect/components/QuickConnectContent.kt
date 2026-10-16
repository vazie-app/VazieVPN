package app.vazie.vpn.widget.quickconnect.components

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.components.VazieWidgetContent
import app.vazie.vpn.widget.presentation.WidgetLayout

/** Quick Connect: the state, the configuration when the cell is tall enough to name it, and one control. */
@Composable
internal fun QuickConnectContent(
    state: VazieWidgetState,
    layout: WidgetLayout,
    modifier: GlanceModifier = GlanceModifier,
) {
    VazieWidgetContent(state = state, layout = layout, modifier = modifier)
}
