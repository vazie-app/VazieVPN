package app.vazie.vpn.widget.quickconnect.components

import androidx.compose.runtime.Composable
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import app.vazie.vpn.core.designsystem.glance.VazieGlanceDimens
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.WidgetFixtures
import app.vazie.vpn.widget.presentation.WidgetVariant
import app.vazie.vpn.widget.presentation.widgetLayout

/** Quick Connect at the two cells it is drawn at, in the two states somebody deciding whether to add it would
 * want to see: what it looks like doing nothing, and what it looks like working. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
private fun QuickConnectSmallestDisconnected() {
    VazieGlanceTheme {
        QuickConnectContent(
            state = WidgetFixtures.idle,
            layout = quickConnectLayout(VazieWidgetSizes.squareMin),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 130, heightDp = 130)
@Composable
private fun QuickConnectDisconnected() {
    VazieGlanceTheme {
        QuickConnectContent(
            state = WidgetFixtures.idle,
            layout = quickConnectLayout(VazieWidgetSizes.squareTarget),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 130, heightDp = 130)
@Composable
private fun QuickConnectConnected() {
    VazieGlanceTheme {
        QuickConnectContent(
            state = WidgetFixtures.connected,
            layout = quickConnectLayout(VazieWidgetSizes.squareTarget),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 130, heightDp = 180)
@Composable
private fun QuickConnectTallNoConfiguration() {
    VazieGlanceTheme {
        QuickConnectContent(
            state = VazieWidgetState.NoConfiguration,
            layout = quickConnectLayout(VazieWidgetSizes.squareTall),
        )
    }
}

@Composable
private fun quickConnectLayout(size: androidx.compose.ui.unit.DpSize) = widgetLayout(
    size = size,
    variant = WidgetVariant.QUICK_CONNECT,
    dimens = VazieGlanceDimens(),
)
