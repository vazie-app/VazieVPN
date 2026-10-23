package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The visible plate. */
private val ContainerSize = 44.dp

/** The touch target around it, which is the accessibility floor and not negotiable. */
private val TouchTargetSize = 48.dp

/** The ripple's reach. Half the touch target, so the circle fills the square it belongs to. */
private val RippleRadius = TouchTargetSize / 2

/** A square icon button: 44dp of plate inside 48dp of touch target. */
@Composable
fun VazieIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = VazieTheme.colors
    val description = contentDescription
    Box(
        modifier = modifier
            .size(TouchTargetSize)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = ripple(bounded = false, radius = RippleRadius),
                interactionSource = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ContainerSize)
                .clip(VazieTheme.shapes.sm)
                .background(if (enabled) colors.surfaceMuted else colors.disabled),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides
                    if (enabled) colors.onSurfaceMuted else colors.textDisabled,
                content = content,
            )
        }
    }
}
