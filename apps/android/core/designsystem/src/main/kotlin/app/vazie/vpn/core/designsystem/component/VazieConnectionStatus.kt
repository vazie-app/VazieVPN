package app.vazie.vpn.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieColors
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity

/** The connection status line of the "Маршрут" design: **an icon, a word, an explanation**. Colour is only
 * the third signal — each state has its own icon, and [title] says it in words. */
@Composable
fun VazieConnectionStatus(
    state: VazieRouteState,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleTechnical: Boolean = false,
    subtitleContentDescription: String? = null,
) {
    val colors = VazieTheme.colors
    val look = statusLook(state, colors)
    val pulsing = !VazieTheme.reduceMotion &&
        (state == VazieRouteState.Connecting || state == VazieRouteState.Reconnecting)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm + VazieTheme.spacing.xxxs),
    ) {
        val iconAlpha = if (pulsing) {
            val transition = rememberInfiniteTransition(label = "status-pulse")
            val alpha by transition.animateFloat(
                initialValue = PulseLow,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(PulseMillis), RepeatMode.Reverse),
                label = "alpha",
            )
            alpha
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .size(TileSize)
                .clip(RoundedCornerShape(TileRadius))
                .background(look.container)
                .alpha(iconAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Icon(look.icon, contentDescription = null, tint = look.content, modifier = Modifier.size(IconSize))
        }
        // The explanation always keeps room for two lines, so the route and the button under it do not jump
        // when "Трафик идёт напрямую через вашего провайдера" (two lines) becomes "00:42 · VLESS" (one).
        val reserve = with(LocalDensity.current) {
            VazieTheme.typography.bodySecondary.lineHeight.toDp() * SubtitleLines
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = VazieTheme.typography.headline,
                color = colors.textPrimary,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = if (subtitleTechnical) VazieTheme.typography.mono else VazieTheme.typography.bodySecondary,
                    color = colors.textSecondary,
                    // A ticking value is read in the caller's words ("Подключено 42 минуты"), not
                    // second by second.
                    modifier = Modifier
                        .heightIn(min = reserve)
                        .then(
                            if (subtitleContentDescription != null) {
                                Modifier.clearAndSetSemantics { contentDescription = subtitleContentDescription }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

private data class StatusLook(val icon: ImageVector, val container: Color, val content: Color)

private fun statusLook(state: VazieRouteState, colors: VazieColors): StatusLook = when (state) {
    VazieRouteState.Disconnected -> StatusLook(VazieIcons.Power, colors.surfaceMuted, colors.textSecondary)
    VazieRouteState.Connecting -> StatusLook(VazieIcons.Clock, colors.routeSoft, colors.routeStart)
    VazieRouteState.Connected -> StatusLook(VazieIcons.Check, colors.routeSoft, colors.routeStart)
    VazieRouteState.Reconnecting -> StatusLook(VazieIcons.Retry, colors.routeSoft, colors.routeStart)
    VazieRouteState.Failed -> StatusLook(VazieIcons.Alert, colors.errorContainer, colors.error)
}

/** The connection timer: redrawn every second, read to TalkBack at minute granularity. */
@Composable
fun VazieConnectionTimer(
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = VazieTheme.typography.monoDisplay,
        color = VazieTheme.colors.textPrimary,
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
    )
}

/** One technical reading: a label in Geologica, the value in Martian Mono under it ("Протокол" / "VLESS
 * Reality"). [icon], when given, sits before the label in the route colour. */
@Composable
fun VazieTechnicalValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = VazieTheme.colors
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs + VazieTheme.spacing.xxxs),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.routeStart, modifier = Modifier.size(ValueIconSize))
            }
            Text(text = label, style = VazieTheme.typography.caption, color = colors.textSecondary)
        }
        Text(text = value, style = VazieTheme.typography.monoValue, color = colors.textPrimary)
    }
}

private val TileSize = 44.dp
private val TileRadius = 15.dp
private val IconSize = 22.dp
private val ValueIconSize = 15.dp
private const val PulseLow = 0.55f
private const val SubtitleLines = 2
private const val PulseMillis = 900
