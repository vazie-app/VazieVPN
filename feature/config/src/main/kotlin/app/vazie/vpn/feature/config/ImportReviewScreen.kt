package app.vazie.vpn.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.VazieTextField
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.config.components.AddConfigTopBar
import app.vazie.vpn.feature.config.components.ConfigPreviewCard

/** Step 3: name it and check what is about to be saved. */
@Composable
fun ImportReviewScreen(
    state: AddConfigUiState,
    onAction: (AddConfigAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = (state.detection as? DetectionUiState.Recognized)?.preview
    val spacing = VazieTheme.spacing

    // Hoisted so the scaffold can be told when something has gone under the toolbar. The scaffold
    // cannot reach for it: the container is this screen's choice, and the state belongs to it.
    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            AddConfigTopBar(
                title = stringResource(R.string.config_review_title),
                onClose = { onAction(AddConfigAction.Close) },
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
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VazieTextField(
                value = state.name,
                onValueChange = { onAction(AddConfigAction.NameChanged(it)) },
                label = stringResource(R.string.config_field_name),
                placeholder = stringResource(R.string.config_name_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )

            if (preview != null) {
                ConfigPreviewCard(preview = preview, name = state.name)

                if (preview.keptParameters.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.config_review_kept_title,
                                preview.keptParameters.joinToString(", "),
                            ),
                            style = VazieTheme.typography.caption,
                            color = VazieTheme.colors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.config_review_kept_body),
                            style = VazieTheme.typography.caption,
                            color = VazieTheme.colors.textSecondary,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.config_review_secrets_note),
                style = VazieTheme.typography.caption,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        VazieButton(
            text = stringResource(R.string.config_action_save),
            onClick = { onAction(AddConfigAction.Save) },
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
        )
    }
}
