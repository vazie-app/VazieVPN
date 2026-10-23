package app.vazie.vpn.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieMessageState
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** Step 4: saved. */
@Composable
fun ImportDoneScreen(
    state: AddConfigUiState,
    onAction: (AddConfigAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing

    VazieScreenScaffold(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = spacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
        ) {
            VazieMessageState(
                title = when (val count = state.importedCount) {
                    0 -> stringResource(R.string.config_done_title, state.name)
                    else -> pluralStringResource(R.plurals.config_done_many_title, count, count)
                },
                description = stringResource(R.string.config_done_body),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            VazieButton(
                text = stringResource(R.string.config_action_done),
                onClick = { onAction(AddConfigAction.Close) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
