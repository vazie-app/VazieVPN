package app.vazie.vpn.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieAction
import app.vazie.vpn.core.designsystem.component.VazieActionGroup
import app.vazie.vpn.core.designsystem.component.VazieActionRow
import app.vazie.vpn.core.designsystem.component.VazieActionTone
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieDetailRow
import app.vazie.vpn.core.designsystem.component.VazieDialog
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieFieldAction
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.VazieSecretField
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieTextField
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.LastUsed

/** A stored configuration: one `Configuration` heading, the fields under it, the credential, the actions, the
 * danger. */
@Composable
fun ConfigDetailsScreen(
    state: ConfigDetailsUiState,
    onAction: (ConfigDetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    // Hoisted so the scaffold can be told when something has gone under the toolbar. The scaffold
    // cannot reach for it: the container is this screen's choice, and the state belongs to it.
    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = state.name,
                onBack = { onAction(ConfigDetailsAction.Back) },
                backContentDescription = stringResource(R.string.config_back),
            )
        },
    ) {
        when {
            state.loading -> Unit
            state.missing -> Text(
                text = stringResource(R.string.config_details_missing),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.padding(
                    horizontal = spacing.screenHorizontal,
                    vertical = spacing.sm,
                ),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = spacing.screenHorizontal)
                    .vazieScrollEdgePadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                VazieSectionHeader(title = stringResource(R.string.config_section_configuration))
                ConfigurationCard(state = state, onAction = onAction)

                VazieSectionHeader(title = stringResource(R.string.config_section_credentials))
                VazieSecretField(
                    value = state.secret,
                    revealed = state.secretRevealed,
                    label = state.secretLabel,
                    trailingContent = {
                        VazieFieldAction(
                            label = stringResource(
                                if (state.secretRevealed) {
                                    R.string.config_secret_hide
                                } else {
                                    R.string.config_secret_show
                                },
                            ),
                            onClick = { onAction(ConfigDetailsAction.ToggleSecret) },
                        )
                    },
                )
                if (state.keptParameters.isNotEmpty()) {
                    KeptParameters(
                        parameters = state.keptParameters.joinToString(", "),
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }

                // Three actions the eye can tell apart before it reads them.
                VazieSectionHeader(title = stringResource(R.string.config_section_actions))
                VazieActionGroup(
                    actions = listOf(
                        VazieAction(
                            icon = VazieIcons.Rename,
                            label = stringResource(R.string.config_action_rename),
                            onClick = { onAction(ConfigDetailsAction.RequestRename) },
                        ),
                        VazieAction(
                            icon = VazieIcons.Duplicate,
                            label = stringResource(R.string.config_action_duplicate),
                            onClick = { onAction(ConfigDetailsAction.Duplicate) },
                        ),
                        VazieAction(
                            icon = VazieIcons.Export,
                            label = stringResource(R.string.config_action_export),
                            onClick = { onAction(ConfigDetailsAction.RequestExport) },
                        ),
                    ),
                )

                // Its own heading, its own space, nothing beside it. See the class documentation.
                VazieSectionHeader(
                    title = stringResource(R.string.config_section_danger),
                    modifier = Modifier.padding(top = spacing.md),
                )
                VazieActionRow(
                    icon = VazieIcons.Delete,
                    label = stringResource(R.string.config_action_delete),
                    onClick = { onAction(ConfigDetailsAction.RequestDelete) },
                    tone = VazieActionTone.Destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    state.renameDraft?.let { draft ->
        RenameDialog(
            draft = draft,
            valid = state.renameValid,
            onAction = onAction,
        )
    }
    if (state.confirmingExport) ExportDialog(onAction = onAction)
    if (state.confirmingDelete) DeleteDialog(name = state.name, onAction = onAction)
}

@Composable
private fun ConfigurationCard(
    state: ConfigDetailsUiState,
    onAction: (ConfigDetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    VazieCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues()) {
        VazieDetailRow(
            label = stringResource(R.string.config_field_protocol),
            value = listOfNotNull(state.protocolLabel, state.securityLabel).joinToString(SEPARATOR),
        )
        state.transportLabel?.let {
            VazieDivider()
            VazieDetailRow(
                label = stringResource(R.string.config_field_transport),
                value = listOfNotNull(it, state.transportDetail).joinToString(SEPARATOR),
            )
        }
        VazieDivider()
        VazieDetailRow(
            label = stringResource(R.string.config_field_server),
            value = state.server,
        )
        state.serverName?.let {
            VazieDivider()
            VazieDetailRow(label = stringResource(R.string.config_field_sni), value = it)
        }
        state.fingerprint?.let {
            VazieDivider()
            VazieDetailRow(
                label = stringResource(R.string.config_field_fingerprint),
                value = it,
            )
        }
        state.flow?.let {
            VazieDivider()
            VazieDetailRow(label = stringResource(R.string.config_field_flow), value = it)
        }
        VazieDivider()
        VazieDetailRow(
            label = stringResource(R.string.config_field_last_used),
            value = state.lastUsed.text(),
            technical = false,
        )
    }
}

/** The bucket as a sentence. See `LastUsed` for why the choice was made before it got here. */
@Composable
private fun LastUsed.text(): String = when (this) {
    LastUsed.Never -> stringResource(R.string.config_last_used_never)
    LastUsed.JustNow -> stringResource(R.string.config_last_used_just_now)
    is LastUsed.Today -> stringResource(
        R.string.config_last_used_today,
        "%02d:%02d".format(hour, minute),
    )

    LastUsed.Yesterday -> stringResource(R.string.config_last_used_yesterday)
    is LastUsed.Earlier -> stringResource(
        R.string.config_last_used_earlier,
        "%02d.%02d.%04d".format(day, month, year),
    )
}

@Composable
private fun KeptParameters(parameters: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        Text(
            text = stringResource(R.string.config_review_kept_title, parameters),
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

@Composable
private fun RenameDialog(
    draft: String,
    valid: Boolean,
    onAction: (ConfigDetailsAction) -> Unit,
) {
    VazieDialog(
        title = stringResource(R.string.config_rename_title),
        onDismissRequest = { onAction(ConfigDetailsAction.DismissRename) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
                VazieTextField(
                    value = draft,
                    onValueChange = { onAction(ConfigDetailsAction.EditRename(it)) },
                    label = stringResource(R.string.config_field_name),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.config_rename_body),
                    style = VazieTheme.typography.caption,
                    color = VazieTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            VazieButton(
                text = stringResource(R.string.config_rename_confirm),
                onClick = { onAction(ConfigDetailsAction.ConfirmRename) },
                enabled = valid,
            )
        },
        dismissButton = {
            VazieButton(
                text = stringResource(R.string.config_rename_cancel),
                onClick = { onAction(ConfigDetailsAction.DismissRename) },
                variant = VazieButtonVariant.Text,
            )
        },
    )
}

/** The one dialog whose job is to inform rather than to ask. */
@Composable
private fun ExportDialog(onAction: (ConfigDetailsAction) -> Unit) {
    VazieDialog(
        title = stringResource(R.string.config_export_title),
        onDismissRequest = { onAction(ConfigDetailsAction.DismissExport) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
                Text(
                    text = stringResource(R.string.config_export_warning),
                    style = VazieTheme.typography.body,
                    color = VazieTheme.colors.warningText,
                )
                Text(
                    text = stringResource(R.string.config_export_body),
                    style = VazieTheme.typography.body,
                    color = VazieTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            VazieButton(
                text = stringResource(R.string.config_export_confirm),
                onClick = { onAction(ConfigDetailsAction.ConfirmExport) },
            )
        },
        dismissButton = {
            VazieButton(
                text = stringResource(R.string.config_export_cancel),
                onClick = { onAction(ConfigDetailsAction.DismissExport) },
                variant = VazieButtonVariant.Text,
            )
        },
    )
}

@Composable
private fun DeleteDialog(name: String, onAction: (ConfigDetailsAction) -> Unit) {
    VazieDialog(
        title = stringResource(R.string.config_delete_title),
        onDismissRequest = { onAction(ConfigDetailsAction.DismissDelete) },
        text = stringResource(R.string.config_delete_body, name),
        confirmButton = {
            VazieButton(
                text = stringResource(R.string.config_delete_confirm),
                onClick = { onAction(ConfigDetailsAction.ConfirmDelete) },
                variant = VazieButtonVariant.Destructive,
            )
        },
        dismissButton = {
            VazieButton(
                text = stringResource(R.string.config_delete_cancel),
                onClick = { onAction(ConfigDetailsAction.DismissDelete) },
                variant = VazieButtonVariant.Text,
            )
        },
    )
}

private const val SEPARATOR = " · "
