package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieColors
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.theme.vazieShadow

/** Generic severity for anything that reports state — a dot, a chip, a badge. */
enum class VazieStatusTone { Neutral, Info, Success, Warning, Error }

@Immutable
internal data class StatusPalette(val indicator: Color, val label: Color)

/** Pure resolution so the mapping can be tested without composing anything, and so a colour role is never
 * picked ad hoc at a call site. */
internal fun statusPalette(tone: VazieStatusTone, colors: VazieColors): StatusPalette = when (tone) {
    VazieStatusTone.Neutral -> StatusPalette(colors.statusDotIdle, colors.textSecondary)
    VazieStatusTone.Info -> StatusPalette(colors.primary, colors.primaryText)
    VazieStatusTone.Success -> StatusPalette(colors.success, colors.successText)
    VazieStatusTone.Warning -> StatusPalette(colors.warning, colors.warningText)
    VazieStatusTone.Error -> StatusPalette(colors.error, colors.errorText)
}

private val DotSize = 8.dp

/** The dot never carries meaning on its own — it always sits next to a label, so screen readers get the text
 * and the dot is hidden from them. */
@Composable
fun VazieStatusDot(tone: VazieStatusTone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .size(DotSize)
            .clip(CircleShape)
            .background(statusPalette(tone, VazieTheme.colors).indicator),
    )
}

@Composable
fun VazieStatusChip(
    label: String,
    tone: VazieStatusTone,
    modifier: Modifier = Modifier,
) {
    val palette = statusPalette(tone, VazieTheme.colors)
    val shape = VazieTheme.shapes.pill
    Row(
        modifier = modifier
            .vazieShadow(shape, alpha = VazieTheme.elevation.restingAlpha)
            .clip(shape)
            .background(VazieTheme.colors.surface)
            .padding(horizontal = VazieTheme.spacing.sm, vertical = VazieTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
    ) {
        VazieStatusDot(tone)
        Text(text = label, style = VazieTheme.typography.labelSmall, color = palette.label)
    }
}

/** How much weight a badge carries. `Strong` is for the accent-tinted family in the design, `Muted` for the
 * quieter one. */
enum class VazieBadgeTone { Muted, Strong }

/** Short technical label — a protocol name, an engine name. Monospace, because in the design every technical
 * value is monospace. */
@Composable
fun VazieBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: VazieBadgeTone = VazieBadgeTone.Muted,
) {
    val colors = VazieTheme.colors
    val container = when (tone) {
        VazieBadgeTone.Muted -> colors.surfaceMuted
        VazieBadgeTone.Strong -> colors.accentContainer
    }
    val content = when (tone) {
        VazieBadgeTone.Muted -> colors.engineBadgeFg
        VazieBadgeTone.Strong -> colors.onAccentContainer
    }
    Text(
        text = label,
        style = VazieTheme.typography.mono,
        color = content,
        modifier = modifier
            .clip(
                VazieTheme.shapes.pill
            )
            .background(container)
            .padding(horizontal = VazieTheme.spacing.sm, vertical = VazieTheme.spacing.xxs),
    )
}

private val MarkSize = 36.dp

/** Square mark that leads a row — the design fills it with two or three monospace characters, but it takes
 * any content. */
@Composable
fun VazieMark(
    modifier: Modifier = Modifier,
    tone: VazieBadgeTone = VazieBadgeTone.Muted,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = VazieTheme.colors
    val container = when (tone) {
        VazieBadgeTone.Muted -> colors.surfaceMuted
        VazieBadgeTone.Strong -> colors.accentContainer
    }
    Box(
        modifier = modifier
            .then(
                if (contentDescription == null) Modifier.clearAndSetSemantics { }
                else Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
            )
            .size(MarkSize)
            .clip(VazieTheme.shapes.sm)
            .background(container),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
