package app.vazie.vpn.feature.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.home.R

private const val DotCount = 3
private const val RestAlpha = 0.25f
private const val StepMillis = 220
private const val PulseMillis = 620

/** The signal the design asks for while a tunnel is opening — deliberately not a generic spinner
 * (DESIGN_SYSTEM §8). */
@Composable
internal fun ConnectingPulse(modifier: Modifier = Modifier) {
    val reduceMotion = VazieTheme.reduceMotion
    val transition = rememberInfiniteTransition(label = "connecting")

    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        repeat(DotCount) { index ->
            val animated by transition.animateFloat(
                initialValue = RestAlpha,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = PulseMillis, delayMillis = index * StepMillis),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(VazieTheme.spacing.xs)
                    .clip(CircleShape)
                    .background(VazieTheme.colors.primary.copy(alpha = if (reduceMotion) RestAlpha else animated)),
            )
        }
    }
}
