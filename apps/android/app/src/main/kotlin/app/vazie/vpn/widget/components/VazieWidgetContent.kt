package app.vazie.vpn.widget.components

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.WidgetSubjectUi
import app.vazie.vpn.widget.presentation.ReadingsKind
import app.vazie.vpn.widget.presentation.WidgetAction
import app.vazie.vpn.widget.presentation.WidgetArrangement
import app.vazie.vpn.widget.presentation.WidgetLayout
import app.vazie.vpn.widget.presentation.lastUsedText
import app.vazie.vpn.widget.presentation.readingsKind
import app.vazie.vpn.widget.presentation.widgetPresentation
import app.vazie.vpn.widget.toGlanceAction

/** The whole widget. Both of them. */
@Composable
internal fun VazieWidgetContent(
    state: VazieWidgetState,
    layout: WidgetLayout,
    modifier: GlanceModifier = GlanceModifier,
) {
    val dimens = VazieGlanceTheme.dimens
    val presentation = widgetPresentation(state)
    val configured = state as? VazieWidgetState.Configured
    val action = presentation.action.toGlanceAction()
    val openApp = WidgetAction.OpenHome.toGlanceAction()
    // Read before layout: alignment depends on whether chips are actually drawn, not whether they would fit.
    val shortcuts = configured?.shortcuts?.take(layout.shortcutSlots).orEmpty()

    WidgetCanvas(modifier = modifier) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = layout.padding, vertical = layout.verticalPadding),
            horizontalAlignment = Alignment.Start,
        ) {
            when (layout.arrangement) {
                WidgetArrangement.STACKED -> {
                    // Centred, so a one-word block in the smallest cell does not look half-loaded.
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        WidgetStateBlock(
                            presentation = presentation,
                            layout = layout,
                            subject = configured?.selected,
                            reading = configured?.reading(layout),
                            onClick = openApp,
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }
                    Spacer(GlanceModifier.height(dimens.blockGap))
                    WidgetActionButton(
                        presentation = presentation,
                        onClick = action,
                        fillsWidth = true,
                    )
                }

                WidgetArrangement.SPLIT -> {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        // Centred against the right column when nothing follows the row, and started from the
                        // top when a chip row does.
                        verticalAlignment = when {
                            shortcuts.isEmpty() -> Alignment.CenterVertically
                            else -> Alignment.Top
                        },
                    ) {
                        WidgetStateBlock(
                            presentation = presentation,
                            layout = layout,
                            subject = configured?.selected,
                            reading = configured?.reading(layout),
                            onClick = openApp,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(GlanceModifier.width(dimens.itemGap))
                        Column(horizontalAlignment = Alignment.End) {
                            WidgetActionButton(
                                presentation = presentation,
                                onClick = action,
                                fillsWidth = false,
                                // One width for every state, scaled with the font size.
                                width = layout.actionWidth,
                            )
                        }
                    }
                }
            }
            // One row of saved-configuration shortcuts.
            if (shortcuts.isNotEmpty()) {
                Spacer(GlanceModifier.height(dimens.blockGap))
                WidgetConfigurationChips(shortcuts = shortcuts)
            }
        }
    }
}

/** The one supporting line, and what competes for it. */
@Composable
private fun VazieWidgetState.Configured.reading(layout: WidgetLayout): String? {
    val context = LocalContext.current
    return when (readingsKind(connection = connection, layout = layout)) {
        ReadingsKind.LAST_USED -> lastUsedText(context, lastUsed)
        ReadingsKind.NONE -> null
    }
}
