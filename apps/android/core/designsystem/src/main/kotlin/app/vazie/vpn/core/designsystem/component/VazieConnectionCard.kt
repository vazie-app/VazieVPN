package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** What stands on the left of a [VazieConnectionCard]. */
@Immutable
sealed interface VazieConnectionMark {
    /** Two or three letters in Martian Mono — a country code or a protocol ("VL"). */
    data class Code(val code: String) : VazieConnectionMark

    /** A server with no code to show. */
    data object Server : VazieConnectionMark

    /** Nothing chosen yet: a dashed tile with a plus. */
    data object Empty : VazieConnectionMark

    /** Not available yet. */
    data object Locked : VazieConnectionMark
}

/** The selected-connection card of the "Маршрут" design: a mark, a title, one line of detail and, optionally,
 * a badge and an action ("Сменить"). */
@Composable
fun VazieConnectionCard(
    title: String,
    mark: VazieConnectionMark,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleTechnical: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    locked: Boolean = false,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    val shape = VazieTheme.shapes.lg
    val interactive = onClick != null && !locked
    val announcement = contentDescription ?: listOfNotNull(title, subtitle).joinToString(". ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CardMinHeight)
            .clip(shape)
            .background(colors.surface)
            .then(if (interactive) Modifier.clickable(onClick = onClick!!) else Modifier)
            .semantics(mergeDescendants = true) {
                this.contentDescription = announcement
                if (onClick != null) role = Role.Button
                if (locked) disabled()
            }
            .padding(start = spacing.sm + spacing.xxxs, end = spacing.sm, top = spacing.sm + spacing.xxxs, bottom = spacing.sm + spacing.xxxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxxs),
    ) {
        ConnectionMark(mark)
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = VazieTheme.typography.titleSmall,
                color = if (locked) colors.textSecondary else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null || badge != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    badge?.invoke()
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = if (subtitleTechnical) VazieTheme.typography.mono else VazieTheme.typography.bodySecondary,
                            color = if (locked) colors.textMuted else colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (actionLabel != null && !locked) {
            Row(
                modifier = Modifier.clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = actionLabel, style = VazieTheme.typography.label, color = colors.primaryText, maxLines = 1)
                Icon(VazieIcons.ChevronRight, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(ChevronSize))
            }
        } else if (locked) {
            Icon(VazieIcons.Lock, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(ChevronSize))
        }
    }
}

@Composable
private fun ConnectionMark(mark: VazieConnectionMark) {
    val colors = VazieTheme.colors
    val shape = RoundedCornerShape(MarkRadius)
    val base = Modifier
        .size(MarkSize)
        .clip(shape)
    when (mark) {
        is VazieConnectionMark.Code -> Box(base.background(colors.surfaceMuted), contentAlignment = Alignment.Center) {
            Text(
                text = mark.code,
                style = VazieTheme.typography.mono.copy(fontWeight = FontWeight(MarkWeight)),
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
        VazieConnectionMark.Server -> Box(base.background(colors.surfaceMuted), contentAlignment = Alignment.Center) {
            Icon(VazieIcons.Server, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(GlyphSize))
        }
        VazieConnectionMark.Locked -> Box(base.background(colors.segmentedTrack), contentAlignment = Alignment.Center) {
            Icon(VazieIcons.Server, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(GlyphSize))
        }
        VazieConnectionMark.Empty -> Box(
            base.drawBehind {
                drawRoundRect(
                    color = colors.border,
                    cornerRadius = CornerRadius(MarkRadius.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(VazieIcons.Plus, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(GlyphSize))
        }
    }
}

private val CardMinHeight = 72.dp
private val MarkSize = 44.dp
private val MarkRadius = 14.dp
private const val MarkWeight = 550
private val GlyphSize = 22.dp
private val ChevronSize = 18.dp
