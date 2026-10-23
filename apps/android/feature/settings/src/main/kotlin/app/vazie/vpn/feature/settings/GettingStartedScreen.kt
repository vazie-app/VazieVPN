package app.vazie.vpn.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieExampleBlock
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.SyntheticConfigExample

/** What a configuration is, where one comes from, and what to do with it. */
@Composable
fun GettingStartedScreen(
    state: GettingStartedUiState,
    onAction: (GettingStartedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val scroll = rememberScrollState()

    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(state.topic.toolbarTitleRes),
                onBack = { onAction(GettingStartedAction.Back) },
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenHorizontal)
                .vazieScrollEdgePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(state.topic.introRes),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            // Sections are data, so adding a topic is a list change, not a layout change.
            state.topic.sections(state.supportedFormats).forEach { section ->
                Section(title = stringResource(section.headingRes))
                Text(
                    text = section.body(),
                    style = VazieTheme.typography.body,
                    color = VazieTheme.colors.textSecondary,
                )
                if (section.showsExample) {
                    VazieExampleBlock(
                        text = SyntheticConfigExample.VLESS_LINK,
                        description = stringResource(R.string.getting_started_example_a11y),
                    )
                }
            }
        }

        // The call to action is pinned, not at the end of the scroll.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal)
                .padding(bottom = spacing.md),
        ) {
            // Only a topic that ends in doing something has an action.
            state.topic.actionRes?.let { label ->
                VazieButton(
                    text = stringResource(label),
                    onClick = { onAction(GettingStartedAction.AddConfiguration) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A heading inside the guide. */
@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = VazieTheme.typography.titleSmall,
        color = VazieTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().padding(top = VazieTheme.spacing.xs),
    )
}

@Immutable
data class GettingStartedUiState(
    val topic: GettingStartedTopic,
    /** The formats Vazie can read, as one already-formatted phrase. */
    val supportedFormats: String,
)

sealed interface GettingStartedAction {

    /** Into the wizard that already exists. */
    data object AddConfiguration : GettingStartedAction
    data object Back : GettingStartedAction
}
