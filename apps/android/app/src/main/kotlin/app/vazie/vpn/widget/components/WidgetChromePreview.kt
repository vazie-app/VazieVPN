package app.vazie.vpn.widget.components

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import app.vazie.vpn.MainActivity
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.WidgetConnectionUi
import app.vazie.vpn.widget.WidgetFixtures
import app.vazie.vpn.widget.presentation.widgetPresentation

/** Every state the chrome can be in, side by side, which is the only way to check the thing the design turns
 * on: that no two states look alike once the colour is taken away. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 320)
@Composable
private fun WidgetChromeStates() {
    val states = listOf(
        VazieWidgetState.NoConfiguration,
        WidgetFixtures.idle,
        WidgetFixtures.configured(WidgetConnectionUi.Preparing),
        WidgetFixtures.configured(WidgetConnectionUi.Connecting),
        WidgetFixtures.connected,
        WidgetFixtures.configured(WidgetConnectionUi.Disconnecting),
        WidgetFixtures.configured(WidgetConnectionUi.Failed),
        WidgetFixtures.configured(WidgetConnectionUi.NoInternet),
    )
    VazieGlanceTheme {
        val dimens = VazieGlanceTheme.dimens
        WidgetCanvas {
            Column(
                modifier = GlanceModifier.fillMaxWidth().padding(dimens.widgetPadding),
                horizontalAlignment = Alignment.Start,
            ) {
                states.forEach { state ->
                    val presentation = widgetPresentation(state)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetPhaseRail(
                            phase = presentation.phase,
                            tone = presentation.tone,
                        )
                        Spacer(GlanceModifier.width(dimens.itemGap))
                        WidgetActionButton(
                            presentation = presentation,
                            onClick = actionStartActivity<MainActivity>(),
                            fillsWidth = false,
                        )
                    }
                    Spacer(GlanceModifier.height(dimens.blockGap))
                }
            }
        }
    }
}

/** The chips, which only the dashboard draws and only when a row of them costs the text nothing. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 80)
@Composable
private fun WidgetChromeChips() {
    VazieGlanceTheme {
        WidgetCanvas {
            WidgetConfigurationChips(
                shortcuts = WidgetFixtures.shortcuts.take(CHIP_PREVIEW_COUNT),
                modifier = GlanceModifier.padding(VazieGlanceTheme.dimens.widgetPadding),
            )
        }
    }
}

private const val CHIP_PREVIEW_COUNT = 3
