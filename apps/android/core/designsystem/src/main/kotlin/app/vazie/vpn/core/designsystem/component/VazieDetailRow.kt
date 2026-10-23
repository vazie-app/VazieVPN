package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A key and a value on one line: the proportional key and the roomier row the design draws. */
@Composable
fun VazieDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    technical: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val spacing = VazieTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = spacing.md,
                vertical = spacing.sm,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = label,
            style = VazieTheme.typography.bodySecondary,
            color = VazieTheme.colors.textSecondary,
            modifier = Modifier,
        )
        Text(
            text = value,
            style = if (technical) VazieTheme.typography.mono else VazieTheme.typography.body,
            color = VazieTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        trailingContent?.invoke()
    }
}
