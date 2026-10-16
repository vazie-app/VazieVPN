package app.vazie.vpn.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.theme.vazieShadow

private val IndicatorSize = 8.dp

/** How far the tab's ripple travels from the centre of the item. */
private val TabRippleRadius = 28.dp

/** The floating bar the design draws for top-level destinations. */
@Composable
fun VazieBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = VazieTheme.shapes.xl
    val spacing = VazieTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .vazieShadow(shape, alpha = VazieTheme.elevation.floatingAlpha)
            .clip(shape)
            .background(VazieTheme.colors.surface)
            .padding(
                vertical = spacing.sm,
                horizontal = spacing.xs,
            )
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The dot above the label is decoration; selection is announced through `selectable`, so the state is never
 * carried by colour alone. */
@Composable
fun RowScope.VazieBottomBarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VazieTheme.colors
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        label = "navIndicator",
    )
    // The whole tab is the touch target; only the press indication is circular.
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .weight(1f)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .indication(interaction, ripple(bounded = false, radius = TabRippleRadius))
            .defaultMinSize(minHeight = VazieTheme.spacing.minTouchTarget)
            .padding(vertical = VazieTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        Box(
            Modifier
                .size(IndicatorSize)
                .clip(CircleShape)
                .background(
                    if (indicatorAlpha > 0f) {
                        colors.primary.copy(alpha = indicatorAlpha)
                    } else {
                        colors.border
                    },
                ),
        )
        Text(
            text = label,
            style = VazieTheme.typography.labelSmall,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
