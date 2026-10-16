package app.vazie.vpn.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieGuideId

/** Everything Vazie can tell you about the parts of itself that live outside it. */
@Composable
fun GuidesScreen(
    state: GuidesUiState,
    onAction: (GuidesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(R.string.settings_guides_title),
                onBack = { onAction(GuidesAction.Back) },
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenHorizontal)
                .vazieScrollEdgePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.settings_guides_body),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
            )
            // Getting Started first, and above a heading rather than mixed into the list.
            VazieSectionHeader(title = stringResource(R.string.getting_started_section))
            VazieListCard {
                state.gettingStarted.forEachIndexed { index, topic ->
                    if (index > 0) VazieDivider()
                    VazieListItem(
                        title = stringResource(topic.titleRes),
                        subtitle = stringResource(topic.summaryRes),
                        trailingContent = { ChevronMark() },
                        onClick = { onAction(GuidesAction.OpenGettingStarted(topic)) },
                    )
                }
            }

            VazieSectionHeader(
                title = stringResource(R.string.settings_guides_system_section),
                modifier = Modifier.padding(top = spacing.sm),
            )
            VazieListCard {
                state.guides.forEachIndexed { index, guide ->
                    if (index > 0) VazieDivider()
                    GuideRow(guide = guide, onAction = onAction)
                }
            }
            // The tour, on its own card and below the guides.
            VazieListCard(modifier = Modifier.padding(top = spacing.sm)) {
                VazieListItem(
                    title = stringResource(R.string.settings_guides_replay_tour),
                    subtitle = stringResource(R.string.settings_guides_replay_tour_body),
                    trailingContent = { ChevronMark() },
                    onClick = { onAction(GuidesAction.ReplayTour) },
                )
            }
        }
    }
}

@Composable
private fun GuideRow(guide: GuideListEntry, onAction: (GuidesAction) -> Unit) {
    VazieListItem(
        title = stringResource(guide.id.titleRes),
        subtitle = stringResource(guide.id.summaryRes),
        trailingContent = { ChevronMark() },
        onClick = { onAction(GuidesAction.OpenGuide(guide.id)) },
        // The marker is a third line under a subtitle that already wraps, so the subtitle is
        // allowed the room it needs rather than being clipped to make space for one word.
        subtitleMaxLines = SubtitleLines,
    )
    if (guide.showViewed) {
        Text(
            text = stringResource(R.string.settings_guides_viewed),
            style = VazieTheme.typography.caption,
            color = VazieTheme.colors.textSecondary,
            modifier = Modifier.padding(
                start = VazieTheme.spacing.md,
                bottom = VazieTheme.spacing.xs,
            ),
        )
    }
}

@Composable
private fun ChevronMark() {
    Icon(imageVector = VazieIcons.ChevronRight, contentDescription = null)
}

private const val SubtitleLines = 3

/** One row of the list: which guide, and whether to say it has been read. */
@Immutable
data class GuideListEntry(val id: VazieGuideId, val showViewed: Boolean)

@Immutable
data class GuidesUiState(
    /** Product learning, above system integration. One topic today; see [GettingStartedTopic]. */
    val gettingStarted: List<GettingStartedTopic> = GettingStartedTopic.entries,
    val guides: List<GuideListEntry>,
)

sealed interface GuidesAction {
    data class OpenGuide(val id: VazieGuideId) : GuidesAction

    /** Open a product-learning topic. */
    data class OpenGettingStarted(val topic: GettingStartedTopic) : GuidesAction

    /** Run the first-run tour again, by choice. */
    data object ReplayTour : GuidesAction
    data object Back : GuidesAction
}
