package app.vazie.vpn.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieExampleBlock
import app.vazie.vpn.core.designsystem.component.VazieIconButton
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieTextField
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.SyntheticConfigExample
import app.vazie.vpn.feature.config.components.AddConfigTopBar

/** Step 1: a field for the link and, beside it, a button that fills it from the clipboard. [link] is held by
 * the route, never by the state object, because it is a credential. */
@Composable
fun AddConfigMethodScreen(
    state: AddConfigUiState,
    link: String,
    onLinkChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    onAction: (AddConfigAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val spacing = VazieTheme.spacing
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            AddConfigTopBar(
                title = stringResource(R.string.config_method_title),
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
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.config_method_have_link),
                style = VazieTheme.typography.title,
                color = VazieTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.config_method_reads, state.supportedFormats),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                VazieTextField(
                    value = link,
                    onValueChange = onLinkChange,
                    label = stringResource(R.string.config_method_link_label),
                    singleLine = false,
                    textStyle = VazieTheme.typography.monoSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                VazieIconButton(onClick = onPaste, contentDescription = stringResource(R.string.config_action_paste)) {
                    Icon(imageVector = VazieIcons.Paste, contentDescription = null)
                }
            }
            Text(
                text = stringResource(R.string.config_method_example_label),
                style = VazieTheme.typography.caption,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xs),
            )
            VazieExampleBlock(
                text = SyntheticConfigExample.VLESS_LINK,
                description = stringResource(R.string.config_method_example_a11y),
            )
            Text(
                text = stringResource(R.string.config_method_unsure),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Text(
                text = stringResource(R.string.config_method_subscription),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.config_method_planned, state.supportedFormats),
                style = VazieTheme.typography.caption,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal)
                .padding(bottom = spacing.md),
        ) {
            VazieButton(
                text = stringResource(R.string.config_action_continue),
                onClick = onSubmit,
                enabled = link.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
