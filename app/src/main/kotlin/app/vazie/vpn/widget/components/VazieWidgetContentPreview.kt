package app.vazie.vpn.widget.components

import androidx.compose.runtime.Composable
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import app.vazie.vpn.core.designsystem.glance.VazieGlanceDimens
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.WidgetConnectionUi
import app.vazie.vpn.widget.WidgetFixtures
import app.vazie.vpn.widget.presentation.WidgetVariant
import app.vazie.vpn.widget.presentation.widgetLayout

/** The shared composition at every cell that changes it, and in every state it can be in. */
@Composable
private fun preview(
    state: VazieWidgetState,
    variant: WidgetVariant,
    size: androidx.compose.ui.unit.DpSize,
) {
    VazieGlanceTheme {
        VazieWidgetContent(
            state = state,
            layout = widgetLayout(
                size = size,
                variant = variant,
                dimens = VazieGlanceDimens(),
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
private fun SmallestCellDisconnected() = preview(
    state = WidgetFixtures.idle,
    variant = WidgetVariant.QUICK_CONNECT,
    size = VazieWidgetSizes.squareMin,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
private fun SmallestCellFailed() = preview(
    state = WidgetFixtures.configured(WidgetConnectionUi.Failed),
    variant = WidgetVariant.QUICK_CONNECT,
    size = VazieWidgetSizes.squareMin,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 130, heightDp = 130)
@Composable
private fun SquareConnected() = preview(
    state = WidgetFixtures.connected,
    variant = WidgetVariant.QUICK_CONNECT,
    size = VazieWidgetSizes.squareTarget,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 110)
@Composable
private fun SqueezedWideConnecting() = preview(
    state = WidgetFixtures.configured(WidgetConnectionUi.Connecting),
    variant = WidgetVariant.DASHBOARD,
    size = VazieWidgetSizes.wideMin,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun WideConnected() = preview(
    state = WidgetFixtures.connected,
    variant = WidgetVariant.DASHBOARD,
    size = VazieWidgetSizes.wideTarget,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun WideNoConfiguration() = preview(
    state = VazieWidgetState.NoConfiguration,
    variant = WidgetVariant.DASHBOARD,
    size = VazieWidgetSizes.wideTarget,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 180)
@Composable
private fun WideTallConnected() = preview(
    state = WidgetFixtures.connected,
    variant = WidgetVariant.DASHBOARD,
    size = VazieWidgetSizes.wideTall,
)

/** The longest name a person could plausibly give a configuration, at the size with the least room for it.
 * Nothing here may push the control off the widget or shrink the state word. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun WideLongName() = preview(
    state = WidgetFixtures.connected.copy(
        selected = WidgetFixtures.selected.copy(
            name = "Home relay, evening",
        ),
    ),
    variant = WidgetVariant.DASHBOARD,
    size = VazieWidgetSizes.wideTarget,
)

