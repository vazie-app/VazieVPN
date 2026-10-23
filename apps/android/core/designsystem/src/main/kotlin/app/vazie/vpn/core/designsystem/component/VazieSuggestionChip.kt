package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A small tappable suggestion under a field — a completion or a correction. Compact to the eye, a full
 * touch target to the finger. */
@Composable
fun VazieSuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    Text(
        text = text,
        style = VazieTheme.typography.labelSmall,
        color = VazieTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(VazieTheme.shapes.md)
            .background(VazieTheme.colors.surfaceMuted)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
    )
}
