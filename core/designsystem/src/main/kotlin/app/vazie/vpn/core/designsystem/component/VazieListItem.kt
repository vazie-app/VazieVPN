package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** How many lines a subtitle gets before it is cut. */
const val DefaultSubtitleLines = 2

/** The row shape the design reuses everywhere: an optional leading mark, a title with an optional subtitle,
 * and optional trailing content. */
@Composable
fun VazieListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    subtitleMaxLines: Int = DefaultSubtitleLines,
    subtitleAccessory: @Composable (() -> Unit)? = null,
    /** One line by default; a row whose title must survive a large system font lets it wrap. */
    titleMaxLines: Int = 1,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    val titleColor = if (enabled) colors.textPrimary else colors.textDisabled
    val subtitleColor = if (enabled) colors.textSecondary else colors.textDisabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .defaultMinSize(minHeight = spacing.listRowHeight)
            .padding(horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        leadingContent?.invoke()
        // The vertical padding belongs to the text, not to the row.
        Column(Modifier.weight(1f).padding(vertical = spacing.sm)) {
            Text(
                text = title,
                style = VazieTheme.typography.titleSmall,
                color = titleColor,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            // A short reading after the subtitle, on its line (a server's signal). The text gives
            // way first, so the reading is never the part that gets cut.
            if (subtitle != null || subtitleAccessory != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = VazieTheme.typography.bodySecondary,
                            color = subtitleColor,
                            maxLines = subtitleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    subtitleAccessory?.invoke()
                }
            }
        }
        if (trailingContent != null) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) colors.textSecondary else colors.textDisabled,
            ) {
                trailingContent()
            }
        }
    }
}
