package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import androidx.compose.ui.text.style.TextOverflow

/** How destructive an action is, which is the only thing a tile branches on. */
enum class VazieActionTone { Normal, Destructive }

/** One management action: an icon, its name, and a target big enough to press. */
@Composable
fun VazieActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: VazieActionTone = VazieActionTone.Normal,
) {
    val colors = VazieTheme.colors
    val destructive = tone == VazieActionTone.Destructive
    val content = if (destructive) colors.onErrorContainer else colors.textPrimary
    val outline = if (destructive) colors.onErrorContainer else colors.border
    val shape = VazieTheme.shapes.md

    Column(
        modifier = modifier
            .clip(shape)
            .background(if (destructive) colors.errorContainer else colors.surface)
            .border(VazieTheme.borders.hairline, outline, shape)
            // The whole tile is the target, announced as a button.
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = VazieTheme.spacing.minTouchTarget)
            .padding(vertical = VazieTheme.spacing.sm, horizontal = VazieTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        Icon(
            imageVector = icon,
            // The label beside it is the announcement. A description here would be the action's
            // name read twice.
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(VazieTheme.spacing.xl).clearAndSetSemantics { },
        )
        Text(
            text = label,
            style = VazieTheme.typography.labelSmall,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A management action as a full-width row: the icon beside the name rather than above it. */
@Composable
fun VazieActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: VazieActionTone = VazieActionTone.Normal,
) {
    val colors = VazieTheme.colors
    val destructive = tone == VazieActionTone.Destructive
    val content = if (destructive) colors.onErrorContainer else colors.textPrimary
    val outline = if (destructive) colors.onErrorContainer else colors.border
    val shape = VazieTheme.shapes.md

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (destructive) colors.errorContainer else colors.surface)
            .border(VazieTheme.borders.hairline, outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = VazieTheme.spacing.minTouchTarget)
            .padding(horizontal = VazieTheme.spacing.md, vertical = VazieTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(VazieTheme.spacing.xl).clearAndSetSemantics { },
        )
        Text(
            text = label,
            style = VazieTheme.typography.label,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A set of management actions, laid out across or down depending on whether across fits. */
@Composable
fun VazieActionGroup(
    actions: List<VazieAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val spacing = VazieTheme.spacing
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gaps = spacing.xs * (actions.size - 1)
        val each = (maxWidth - gaps) / actions.size
        if (each >= MinTileWidth * LocalDensity.current.fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                actions.forEach { action ->
                    VazieActionTile(
                        icon = action.icon,
                        label = action.label,
                        onClick = action.onClick,
                        tone = action.tone,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                actions.forEach { action ->
                    VazieActionRow(
                        icon = action.icon,
                        label = action.label,
                        onClick = action.onClick,
                        tone = action.tone,
                    )
                }
            }
        }
    }
}

/** One entry in a [VazieActionGroup]. */
@Immutable
data class VazieAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val tone: VazieActionTone = VazieActionTone.Normal,
)

/** The narrowest a tile may be before its label starts breaking words. */
private val MinTileWidth = 96.dp
