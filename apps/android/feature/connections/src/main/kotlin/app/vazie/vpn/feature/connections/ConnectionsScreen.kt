package app.vazie.vpn.feature.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieDialog
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.component.VazieMessageState
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieSwipeAction
import app.vazie.vpn.core.designsystem.component.VazieSwipeableRow
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import app.vazie.vpn.core.designsystem.tour.vazieTourTarget
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.connections.components.ConfigurationRow

/** The configurations list: a section header, a floating card, a row per entry with a mark, a chip and a way
 * in. */
@Composable
internal fun ConnectionsScreen(
    state: ConnectionsUiState,
    onAction: (ConnectionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing

    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = { ConnectionsTopBar() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenHorizontal)
                .vazieScrollEdgePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.zero),
        ) {
            StandardList(state = state, onAction = onAction, compact = false)
        }
    }

    // Outside the scroll column: a dialog is not a row, and nesting it among them would give its
    // position in the list a meaning it does not have.
    DeleteDialog(state = state, onAction = onAction)
}

/** The configurations as a section of Home; [compact] replaces the illustrated empty state with one line. */
@Composable
internal fun ConnectionsList(
    state: ConnectionsUiState,
    onAction: (ConnectionsAction) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StandardList(state = state, onAction = onAction, compact = compact)
    }
    DeleteDialog(state = state, onAction = onAction)
}

@Composable
private fun DeleteDialog(state: ConnectionsUiState, onAction: (ConnectionsAction) -> Unit) {
    state.deleting?.let { row ->
        VazieDialog(
            title = stringResource(R.string.connections_delete_title),
            text = stringResource(R.string.connections_delete_body, row.name),
            onDismissRequest = { onAction(ConnectionsAction.DismissDelete) },
            confirmButton = {
                VazieButton(
                    text = stringResource(R.string.connections_delete_confirm),
                    onClick = { onAction(ConnectionsAction.ConfirmDelete) },
                    variant = VazieButtonVariant.Destructive,
                )
            },
            dismissButton = {
                VazieButton(
                    text = stringResource(R.string.connections_delete_cancel),
                    onClick = { onAction(ConnectionsAction.DismissDelete) },
                    variant = VazieButtonVariant.Text,
                )
            },
        )
    }
}

@Composable
private fun ConnectionsTopBar() {
    val spacing = VazieTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.connections_title),
            style = VazieTheme.typography.headline,
            color = VazieTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StandardList(
    state: ConnectionsUiState,
    onAction: (ConnectionsAction) -> Unit,
    compact: Boolean,
) {
    val spacing = VazieTheme.spacing

    VazieSectionHeader(
        title = stringResource(R.string.connections_my_configs),
        action = {
            VazieButton(
                text = stringResource(R.string.connections_add),
                onClick = { onAction(ConnectionsAction.AddConfiguration) },
                variant = VazieButtonVariant.Text,
            )
        },
    )

    if (state.isEmpty && compact) {
        Text(
            text = stringResource(R.string.connections_empty_body),
            style = VazieTheme.typography.body,
            color = VazieTheme.colors.textSecondary,
        )
    } else if (state.isEmpty) {
        // The empty state carries the action it describes.
        VazieMessageState(
            title = stringResource(R.string.connections_empty_title),
            description = stringResource(R.string.connections_empty_body),
            action = {
                VazieButton(
                    text = stringResource(R.string.connections_add_configuration),
                    onClick = { onAction(ConnectionsAction.AddConfiguration) },
                )
            },
        )
    } else {
        VazieListCard {
            state.configurations.forEachIndexed { index, row ->
                if (index > 0) VazieDivider()
                SwipeableConfiguration(row = row, first = index == 0, onAction = onAction) { rowModifier ->
                    ConfigurationRow(
                        row = row,
                        onSelect = { onAction(ConnectionsAction.SelectConfiguration(row.id)) },
                        onOpenDetails = { onAction(ConnectionsAction.OpenConfiguration(row.id)) },
                        modifier = rowModifier,
                    )
                }
            }
        }
    }
}

/** A configuration row with its swipe-to-delete attached. */
@Composable
private fun SwipeableConfiguration(
    row: ConfigurationRowUi,
    first: Boolean,
    onAction: (ConnectionsAction) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    VazieSwipeableRow(
        // Only the first row is an anchor. Guidance about the gesture points at one example of the thing it
        // is about, and a target registered by every row would be the same id claimed five times over.
        modifier = if (first) Modifier.vazieTourTarget(VazieTourTargetId.CONFIGURATION_ROW) else Modifier,
        start = null,
        end = VazieSwipeAction(
            icon = VazieIcons.Delete,
            label = stringResource(R.string.connections_delete),
            onAction = { onAction(ConnectionsAction.RequestDelete(row.id)) },
        ),
        content = content,
    )
}
