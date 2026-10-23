package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** How far the toolbar's own edge sits from the screen's. */
private val ToolbarEdge = 12.dp

/** Tall enough for a 48dp target plus air, so a long title never squeezes the row. */
private val ToolbarHeight = 64.dp

/** The one toolbar every Vazie screen with a back button uses. */
@Composable
fun VazieToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ToolbarHeight)
            .padding(horizontal = ToolbarEdge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
    ) {
        if (onBack != null) {
            VazieIconButton(onClick = onBack, contentDescription = backContentDescription) {
                Icon(imageVector = VazieIcons.ArrowLeft, contentDescription = null)
            }
        }
        Text(
            text = title,
            style = VazieTheme.typography.title,
            color = VazieTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                // Without a back button the title has to line up with the body text below it, which
                // sits at `screenHorizontal`. With one, the button already provides that alignment.
                .padding(start = if (onBack == null) VazieTheme.spacing.xs else VazieTheme.spacing.zero),
        )
        actions()
    }
}
