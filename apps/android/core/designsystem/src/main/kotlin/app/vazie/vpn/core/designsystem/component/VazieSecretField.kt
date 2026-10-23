package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.R
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme

private val FieldMinHeight = 48.dp
private const val MaskCharacter = '•'
private const val MaskLength = 8

/** Read-only display for a value that is hidden until the user asks for it. */
@Composable
fun VazieSecretField(
    value: String,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    valid: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    val stateLabel = stringResource(
        if (revealed) R.string.vazie_a11y_value_shown else R.string.vazie_a11y_value_hidden,
    )

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = VazieTheme.typography.labelSmall,
                color = if (valid) colors.successText else colors.textSecondary,
                modifier = Modifier.padding(bottom = spacing.xxs),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FieldMinHeight)
                .background(colors.surface, VazieTheme.shapes.md)
                .border(
                    width = VazieTheme.borders.regular,
                    color = if (valid) colors.success else colors.border,
                    shape = VazieTheme.shapes.md,
                )
                .padding(horizontal = spacing.sm, vertical = spacing.xs)
                .semantics { stateDescription = stateLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = if (revealed) value else MaskCharacter.toString().repeat(MaskLength),
                style = VazieTheme.typography.mono,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (valid) {
                // The check makes "verified" visible without colour and to a screen reader.
                Icon(
                    imageVector = VazieIcons.Check,
                    contentDescription = stringResource(R.string.vazie_a11y_value_valid),
                    tint = colors.successText,
                    modifier = Modifier.size(spacing.lg),
                )
            }
            trailingContent?.invoke()
        }
    }
}
