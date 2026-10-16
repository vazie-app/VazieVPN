package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieSpacing
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.theme.vazieUppercase

/** A titled section of technical read-outs: a heading and a rule, no card. */
@Composable
fun VazieConsoleSection(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            // The rail, and the reason the heading is legible at all.
            Box(
                modifier = Modifier
                    .height(with(LocalDensity.current) {
                        VazieTheme.typography.monoSmall.lineHeight.toDp()
                    })
                    .width(VazieTheme.borders.strong)
                    .background(colors.technicalValueAccent),
            )
            Text(
                text = title.vazieUppercase(),
                style = VazieTheme.typography.monoSmall,
                color = colors.technicalValueAccent,
                // Announced as a heading so a screen reader can jump between sections instead of
                // reading forty readings in a row to find one.
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            action?.invoke()
        }
        HorizontalDivider(thickness = VazieTheme.borders.hairline, color = colors.technicalDivider)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(spacing.zero),
            content = content,
        )
    }
}

/** How much a read-out's value is worth looking at. */
enum class VazieReadoutTone { Plain, Accent, Absent }

/** One aligned key/value pair. */
@Composable
fun VazieReadout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: VazieReadoutTone = VazieReadoutTone.Plain,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    // Padding before the minimum size, so 48dp is the whole row.
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(vertical = spacing.xs)
                        .defaultMinSize(minHeight = spacing.minTouchTarget)
                } else {
                    Modifier.padding(vertical = spacing.xs)
                }
            )
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = label.vazieUppercase(),
            style = VazieTheme.typography.monoSmall,
            color = colors.technicalLabel,
            // Aligned by baseline, so the key stays on the value's first line when it wraps.
            modifier = Modifier
                .weight(VazieConsoleDefaults.LabelWeight)
                .clearAndSetSemantics { },
        )
        Text(
            text = value,
            style = VazieTheme.typography.mono,
            color = when (tone) {
                VazieReadoutTone.Plain -> colors.technicalValue
                VazieReadoutTone.Accent -> colors.technicalValueAccent
                VazieReadoutTone.Absent -> colors.technicalLabel
            },
            modifier = Modifier
                .weight(VazieConsoleDefaults.ValueWeight)
                .clearAndSetSemantics { },
        )
        trailingContent?.invoke()
    }
        // The rule under every row, and the fix for "rows bleed into one another".
        if (divider) {
            HorizontalDivider(
                thickness = VazieTheme.borders.hairline,
                color = colors.technicalDivider,
            )
        }
    }
}

/** A measurement and what it measures: the value first, the name of it under. */
@Composable
fun VazieMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: VazieReadoutTone = VazieReadoutTone.Plain,
) {
    val colors = VazieTheme.colors
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        },
    ) {
        Text(
            text = value,
            style = VazieTheme.typography.monoTitle,
            color = when (tone) {
                VazieReadoutTone.Plain -> colors.technicalValue
                VazieReadoutTone.Accent -> colors.technicalValueAccent
                VazieReadoutTone.Absent -> colors.technicalLabel
            },
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            text = label.vazieUppercase(),
            style = VazieTheme.typography.monoSmall,
            color = colors.technicalLabel,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The one geometry every key/value read-out pair is laid out on. */
object VazieConsoleDefaults {

    /** The share of a Console row that belongs to the key. */
    const val LabelWeight: Float = 0.4f

    /** The share of a Console row that belongs to the value. */
    const val ValueWeight: Float = 0.6f

    /** The narrowest a status group may get before its operational action stops sharing its line. */
    val MinInlineActionWidth: Dp = 300.dp
}
