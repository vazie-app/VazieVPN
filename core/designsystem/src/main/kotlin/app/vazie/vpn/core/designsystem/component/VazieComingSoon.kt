package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The badge for something the product intends to have and does not have yet. */
@Composable
fun VazieComingSoonBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = VazieTheme.typography.labelSmall,
        color = VazieTheme.colors.onAccentContainer,
        modifier = modifier
            .clip(VazieTheme.shapes.pill)
            .background(VazieTheme.colors.accentContainer)
            .padding(
                horizontal = VazieTheme.spacing.sm,
                vertical = VazieTheme.spacing.xxs,
            ),
    )
}
