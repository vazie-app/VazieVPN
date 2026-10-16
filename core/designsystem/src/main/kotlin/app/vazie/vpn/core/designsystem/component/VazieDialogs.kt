package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** Material's dialog with Vazie surfaces; confirmation is a slot, so a destructive button fits.
 * [text] for one paragraph of prose, [content] for anything else; if both are given, [content] wins. */
@Composable
fun VazieDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    content: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = {
            Text(text = title, style = VazieTheme.typography.title, color = VazieTheme.colors.textPrimary)
        },
        text = content ?: text?.let {
            {
                Text(text = it, style = VazieTheme.typography.body, color = VazieTheme.colors.textSecondary)
            }
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = VazieTheme.shapes.lg,
        containerColor = VazieTheme.colors.elevated,
    )
}

private val HandleWidth = 36.dp
private val HandleHeight = 4.dp

/** Modal sheet with the design's drag handle and top-only corner radius. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VazieBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
        containerColor = VazieTheme.colors.elevated,
        scrimColor = VazieTheme.colors.scrim.copy(alpha = ScrimAlpha),
        shape = VazieTheme.shapes.sheet,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(vertical = VazieTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = HandleWidth, height = HandleHeight)
                        .clip(VazieTheme.shapes.pill)
                        .background(VazieTheme.colors.border),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(
                start = VazieTheme.spacing.lg,
                end = VazieTheme.spacing.lg,
                bottom = VazieTheme.spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = VazieTheme.typography.title,
                    color = VazieTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = VazieTheme.spacing.xs),
                )
            }
            content()
        }
    }
}

private const val ScrimAlpha = 0.4f

/** Row of dialog actions, aligned the way the design draws them. */
@Composable
fun VazieDialogActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
