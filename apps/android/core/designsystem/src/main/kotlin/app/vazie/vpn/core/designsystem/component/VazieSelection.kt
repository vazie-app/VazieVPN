package app.vazie.vpn.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import androidx.compose.ui.text.style.TextOverflow

/** Material's switch, wearing Vazie colours. */
@Composable
fun VazieSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = VazieTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            // The thumb sits on the primary track, so it is content on primary like any label.
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = colors.elevated,
            uncheckedTrackColor = colors.statusDotIdle,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = colors.elevated,
            disabledCheckedTrackColor = colors.disabled,
            disabledUncheckedThumbColor = colors.elevated,
            disabledUncheckedTrackColor = colors.disabled,
        ),
    )
}

private val TrackPadding = 4.dp

/** Two or more mutually exclusive options in a shared track. */
@Composable
fun <T> VazieSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = VazieTheme.colors
    // Derived rather than a separate token: the track hugs the thumb, so its radius is the thumb's
    // plus the padding around it.
    val trackShape = RoundedCornerShape(VazieTheme.shapes.smRadius + TrackPadding)

    Row(
        modifier = modifier
            .clip(trackShape)
            .background(colors.segmentedTrack)
            .padding(TrackPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(TrackPadding),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = label(option),
                style = VazieTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    !enabled -> colors.textDisabled
                    isSelected -> colors.textPrimary
                    else -> colors.textSecondary
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(VazieTheme.shapes.sm)
                    .background(if (isSelected) colors.surface else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(vertical = VazieTheme.spacing.xs),
            )
        }
    }
}

private val SwatchSize = 36.dp
private val CheckSize = 20.dp

/** sRGB relative luminance where black and white text swap over. */
private const val MidLuminance = 0.45f
private val SwatchTouchTarget = 48.dp

/** Colour swatches for picking an appearance. */
@Composable
fun <T> VazieSwatchPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    swatchColor: (T) -> Color,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = VazieTheme.colors
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val ring by animateDpAsState(
                targetValue = if (isSelected) VazieTheme.borders.strong else VazieTheme.borders.hairline,
                label = "swatchRing",
            )
            Box(
                modifier = Modifier
                    .size(SwatchTouchTarget)
                    // Clipped before `selectable`, so the ripple is the circle the user sees rather
                    // than the square the touch target is. The target itself stays 48dp.
                    .clip(CircleShape)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .semantics { contentDescription = label(option) },
                contentAlignment = Alignment.Center,
            ) {
                val fill = swatchColor(option)
                Box(
                    Modifier
                        .size(SwatchSize)
                        .clip(CircleShape)
                        .background(fill)
                        .border(
                            width = ring,
                            color = if (isSelected) colors.primary else colors.border,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        // A black or white check, since the ring alone is too subtle on a coloured swatch.
                        Icon(
                            imageVector = VazieIcons.Check,
                            contentDescription = null,
                            tint = if (fill.luminance() > MidLuminance) Color.Black else Color.White,
                            modifier = Modifier.size(CheckSize),
                        )
                    }
                }
            }
        }
    }
}

private val ChipTouchTarget = 48.dp

/** Mutually exclusive options as chips that **wrap** instead of sharing one track. */
@Composable
fun <T> VazieChoiceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    val shape = VazieTheme.shapes.pill
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .heightIn(min = ChipTouchTarget)
                    .clip(shape)
                    .background(if (isSelected) colors.segmentedTrack else Color.Transparent)
                    .border(
                        width = if (isSelected) VazieTheme.borders.strong else VazieTheme.borders.hairline,
                        color = if (isSelected) colors.primary else colors.border,
                        shape = shape,
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = VazieTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}
