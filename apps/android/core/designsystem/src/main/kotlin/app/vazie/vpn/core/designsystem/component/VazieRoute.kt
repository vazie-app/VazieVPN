package app.vazie.vpn.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieColors
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the route shows. A presentation state, not a connection state: the caller maps whatever its
 * connection layer says onto one of these, and nothing here knows about a VPN service. */
enum class VazieRouteState { Disconnected, Connecting, Connected, Reconnecting, Failed }

/** «Маршрут» — the line from this phone to the server, the central element of the "Маршрут" design. */
@Composable
fun VazieRoute(
    state: VazieRouteState,
    deviceLabel: String,
    serverLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    serverCode: String? = null,
    stateDescription: String? = null,
    onClick: (() -> Unit)? = null,
    animation: VazieRouteAnimation = rememberVazieRouteAnimation(state),
) {
    val colors = VazieTheme.colors
    val loops = routeLoops(animation, animate = !VazieTheme.reduceMotion && !LocalInspectionMode.current)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(VazieTheme.shapes.hero)
            .drawBehind { drawCardBackground(animation, colors) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                stateDescription?.let { this.stateDescription = it }
                if (onClick != null) role = Role.Button
            }
            .padding(start = CardPaddingHorizontal, end = CardPaddingHorizontal, top = CardPaddingTop, bottom = CardPaddingBottom),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ViewportWidth / ViewportHeight)
                .clearAndSetSemantics { },
        ) {
            val unit = maxWidth / ViewportWidth
            Canvas(modifier = Modifier.fillMaxSize()) {
                scale(size.width / ViewportWidth, pivot = Offset.Zero) {
                    drawRoute(animation, colors, loops)
                }
            }
            // The phone glyph, centred in the device node.
            Icon(
                imageVector = VazieIcons.Phone,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier
                    .offset(x = unit * (DeviceX - GlyphHalf), y = unit * (NodeY - GlyphHalf))
                    .size(unit * GlyphHalf * 2),
            )
            // The server node: its code in Martian Mono, or a server glyph for a configuration of one's own.
            Box(
                modifier = Modifier
                    .offset(x = unit * (ServerX - NodeRadius), y = unit * (NodeY - NodeRadius))
                    .size(unit * NodeRadius * 2),
                contentAlignment = Alignment.Center,
            ) {
                if (serverCode != null) {
                    Text(
                        text = serverCode,
                        style = VazieTheme.typography.mono.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                } else {
                    Icon(
                        imageVector = VazieIcons.Server,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(unit * GlyphHalf * 2),
                    )
                }
            }
            // Labels: start-aligned under the phone, end-aligned under the server, so a long Russian
            // city name grows towards the middle instead of off the card.
            Text(
                text = deviceLabel,
                style = VazieTheme.typography.bodySecondary,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(y = unit * LabelTop)
                    .width(maxWidth * LabelWidthFraction),
            )
            Text(
                text = serverLabel,
                style = VazieTheme.typography.bodySecondary,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = unit * LabelTop)
                    .width(maxWidth * LabelWidthFraction),
            )
        }
    }
}

/** The route's motion: how much of the line is drawn, how full the server node is, how settled the connected
 * look is, and which state the drawing is actually showing. */
@Stable
class VazieRouteAnimation internal constructor(initial: VazieRouteState, still: Boolean) {
    internal val line = Animatable(initialLine(initial, still))
    internal val fill = Animatable(initialFill(initial, still))
    internal val settle = Animatable(if (initial == VazieRouteState.Connected) 1f else 0f)

    /** The state the drawing is showing right now. */
    var shown: VazieRouteState by mutableStateOf(initial)
        private set

    /** Whether [state] has been reached by the connection but the route is still playing towards it. Only a
     * connection is ever held: a failure or a disconnection is shown at once. */
    fun isCatchingUpTo(state: VazieRouteState): Boolean =
        state == VazieRouteState.Connected && shown != VazieRouteState.Connected

    internal suspend fun playTo(target: VazieRouteState, still: Boolean) {
        if (still) {
            line.snapTo(initialLine(target, still = true))
            fill.snapTo(initialFill(target, still = true))
            settle.snapTo(if (target == VazieRouteState.Connected) 1f else 0f)
            shown = target
            return
        }
        when (target) {
            VazieRouteState.Connecting, VazieRouteState.Reconnecting -> coroutineScope {
                shown = target
                launch { settle.animateTo(0f, tween(SettleMillis)) }
                line.animateTo(1f, tween(remaining(line.value, 1f, LineMillis), easing = LinearOutSlowInEasing))
                // The tunnel is still being made: the node fills slowly towards "almost", and
                // waits.
                fill.animateTo(FillWhileWaiting, tween(remaining(fill.value, FillWhileWaiting, FillWaitMillis), easing = LinearOutSlowInEasing))
            }
            VazieRouteState.Connected -> {
                if (shown == VazieRouteState.Connected) {
                    line.snapTo(1f); fill.snapTo(1f); settle.snapTo(1f)
                    return
                }
                // Play the rest of the story before showing the result.
                shown = VazieRouteState.Connecting
                line.animateTo(1f, tween(remaining(line.value, 1f, LineMillis), easing = LinearOutSlowInEasing))
                fill.animateTo(1f, tween(remaining(fill.value, 1f, FillFinishMillis), easing = FastOutSlowInEasing))
                shown = VazieRouteState.Connected
                settle.animateTo(1f, tween(SettleMillis))
            }
            VazieRouteState.Disconnected -> coroutineScope {
                shown = VazieRouteState.Disconnected
                launch { settle.animateTo(0f, tween(SettleMillis)) }
                // The node drains, then the line is drawn back from the server to the phone.
                fill.animateTo(0f, tween(remaining(fill.value, 0f, DrainMillis), easing = FastOutSlowInEasing))
                line.animateTo(0f, tween(remaining(line.value, 0f, RetractMillis), easing = FastOutSlowInEasing))
            }
            VazieRouteState.Failed -> {
                shown = VazieRouteState.Failed
                line.snapTo(0f); fill.snapTo(0f); settle.snapTo(0f)
            }
        }
    }
}

/** Remember the route's motion for [state], so that the caller can read [VazieRouteAnimation.isCatchingUpTo]
 * and hand the same object to [VazieRoute]. */
@Composable
fun rememberVazieRouteAnimation(state: VazieRouteState): VazieRouteAnimation {
    val still = VazieTheme.reduceMotion || LocalInspectionMode.current
    val animation = remember { VazieRouteAnimation(state, still) }
    LaunchedEffect(state, still) { animation.playTo(state, still) }
    return animation
}

private fun initialLine(state: VazieRouteState, still: Boolean): Float = when (state) {
    VazieRouteState.Connected -> 1f
    VazieRouteState.Connecting, VazieRouteState.Reconnecting -> if (still) 1f else 0f
    VazieRouteState.Disconnected, VazieRouteState.Failed -> 0f
}

private fun initialFill(state: VazieRouteState, still: Boolean): Float = when (state) {
    VazieRouteState.Connected -> 1f
    VazieRouteState.Connecting, VazieRouteState.Reconnecting -> if (still) StillFill else 0f
    VazieRouteState.Disconnected, VazieRouteState.Failed -> 0f
}

/** The share of [fullMillis] left to travel from [from] to [to]. */
private fun remaining(from: Float, to: Float, fullMillis: Int): Int =
    (abs(to - from) * fullMillis).roundToInt().coerceAtLeast(1)

/** The looping values — the wave, the pulse at the tip, the sparks. */
@Immutable
private data class RouteLoops(val wave: State<Float>, val pulse: State<Float>, val flow: State<Float>)

@Composable
private fun routeLoops(animation: VazieRouteAnimation, animate: Boolean): RouteLoops {
    val still = remember { RouteLoops(mutableFloatStateOf(0f), mutableFloatStateOf(1f), mutableFloatStateOf(0f)) }
    if (!animate) return still
    val transition = rememberInfiniteTransition(label = "route")
    val moving = animation.shown != VazieRouteState.Disconnected && animation.shown != VazieRouteState.Failed
    if (!moving) return still
    return RouteLoops(
        wave = transition.animateFloat(
            initialValue = 0f,
            targetValue = TwoPi,
            animationSpec = infiniteRepeatable(tween(WaveCycleMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "wave",
        ),
        pulse = transition.animateFloat(
            initialValue = PulseLow,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PulseMillis), RepeatMode.Reverse),
            label = "pulse",
        ),
        flow = transition.animateFloat(
            initialValue = 0f,
            targetValue = -FlowPeriod,
            animationSpec = infiniteRepeatable(tween(FlowCycleMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "flow",
        ),
    )
}

private fun routePath(): Path = Path().apply {
    moveTo(DeviceX, NodeY)
    cubicTo(130.3f, 31f, 233.7f, 99f, ServerX, NodeY)
}

private fun DrawScope.drawCardBackground(animation: VazieRouteAnimation, colors: VazieColors) {
    drawRect(colors.surface)
    val settle = animation.settle.value
    if (animation.shown == VazieRouteState.Failed) {
        drawRect(glow(colors.error, GlowSoft, Offset(size.width * 0.7f, size.height * 0.45f)))
        return
    }
    // The connecting glow comes in with the line and hands over to the connected wash as it
    // settles.
    val connecting = animation.line.value * (1f - settle)
    if (connecting > 0f) {
        drawRect(glow(colors.routeStart, GlowStrong * connecting, Offset(size.width / 2, size.height * 0.45f)))
    }
    if (settle > 0f) {
        drawRect(
            Brush.linearGradient(
                colors = listOf(colors.routeSoft, colors.surface),
                start = Offset.Zero,
                end = Offset(size.width * 0.55f, size.height),
            ),
            alpha = settle,
        )
        drawRect(glow(colors.routeStart, GlowStrong * settle, Offset(size.width / 2, size.height * 0.4f)))
    }
}

private fun DrawScope.glow(color: Color, alpha: Float, center: Offset): Brush = Brush.radialGradient(
    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
    center = center,
    radius = size.width * 0.55f,
)

private fun DrawScope.drawRoute(animation: VazieRouteAnimation, colors: VazieColors, loops: RouteLoops) {
    val path = routePath()
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    val gradient = Brush.horizontalGradient(listOf(colors.routeStart, colors.routeEnd), startX = DeviceX, endX = ServerX)
    val dots = PathEffect.dashPathEffect(floatArrayOf(1.5f, 11f))
    fun segment(from: Float, to: Float): Path =
        Path().also { measure.getSegment(length * from, length * to, it, true) }
    fun at(fraction: Float): Offset = measure.getPosition(length * fraction.coerceIn(0f, 1f))

    val shown = animation.shown
    val line = animation.line.value
    val fill = animation.fill.value
    val settle = animation.settle.value

    if (shown == VazieRouteState.Failed) {
        drawPath(segment(0f, FailedReach), colors.routeStart.copy(alpha = 0.55f), style = Stroke(3.5f, cap = StrokeCap.Round))
        drawPath(
            segment(FailedResume, 1f),
            colors.error.copy(alpha = 0.8f),
            style = Stroke(3f, cap = StrokeCap.Round, pathEffect = dots),
        )
        val gap = at(FailedBreak)
        drawCircle(colors.errorContainer, radius = 13f, center = gap)
        drawCircle(colors.error, radius = 13f, center = gap, style = Stroke(2f))
        drawLine(colors.error, gap + Offset(0f, -5f), gap + Offset(0f, 0.5f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawCircle(colors.error, radius = 1.4f, center = gap + Offset(0f, 5f))
        drawNode(Offset(DeviceX, NodeY), colors.surfaceMuted, UnlitRing)
        drawNode(Offset(ServerX, NodeY), colors.surfaceMuted, colors.error)
        return
    }

    // The calm dotted route is always underneath; the drawn line grows over it and fades it out.
    val dotsAlpha = (0.8f - 0.3f * line) * (1f - settle)
    if (dotsAlpha > 0f) {
        drawPath(path, colors.textMuted.copy(alpha = dotsAlpha), style = Stroke(3f, cap = StrokeCap.Round, pathEffect = dots))
    }
    if (line > 0f) {
        val grown = segment(0f, line)
        drawPath(grown, gradient, alpha = 0.2f + 0.02f * settle, style = Stroke(12f + 2f * settle, cap = StrokeCap.Round))
        drawPath(grown, gradient, style = Stroke(4f + 0.5f * settle, cap = StrokeCap.Round))
    }
    // The tip that leads the line, while it is still on its way.
    if (shown == VazieRouteState.Connecting || shown == VazieRouteState.Reconnecting) {
        if (line in 0.001f..TipHideAt) {
            val tip = at(line)
            drawCircle(colors.routeEnd.copy(alpha = 0.35f), radius = 7f * loops.pulse.value, center = tip)
            drawCircle(colors.textPrimary, radius = 4f + loops.pulse.value, center = tip)
        }
    }
    // Sparks flowing along a settled connection.
    if (settle > 0f) {
        drawPath(
            path,
            colors.textPrimary.copy(alpha = 0.85f * settle),
            style = Stroke(
                2.2f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, FlowPeriod - 1f), loops.flow.value),
            ),
        )
    }

    val server = Offset(ServerX, NodeY)
    drawNode(Offset(DeviceX, NodeY), colors.surfaceMuted, if (line > 0f || settle > 0f) colors.routeStart else UnlitRing)
    drawCircle(colors.surfaceMuted, radius = NodeRadius, center = server)
    drawLiquid(server, fill, loops.wave.value, colors)
    drawCircle(lerp(UnlitRing, colors.routeStart, fill.coerceIn(0f, 1f)), radius = NodeRadius, center = server, style = Stroke(2f))
    if (settle > 0f) {
        val badge = Offset(351.3f, 47.7f)
        drawCircle(colors.routeStart.copy(alpha = settle), radius = 8f * (0.6f + 0.4f * settle), center = badge)
        val check = Path().apply {
            moveTo(badge.x - 3.5f, badge.y)
            relativeLineTo(2.5f, 2.5f)
            relativeLineTo(4.5f, -5f)
        }
        drawPath(check, colors.background.copy(alpha = settle), style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The server node filling like a glass: two sine waves, the back one lighter and out of phase, whose swell
 * calms as the node fills and is flat when it is full. */
private fun DrawScope.drawLiquid(center: Offset, level: Float, wave: Float, colors: VazieColors) {
    if (level <= 0.001f) return
    val node = Path().apply { addOval(Rect(center, NodeRadius)) }
    val amplitude = WaveAmplitude * (1f - level).coerceIn(0f, 1f).let { if (it > 0.1f) 1f else it * 10f }
    fun surface(phase: Float): Path = Path().apply {
        val top = center.y + NodeRadius - 2 * NodeRadius * level
        val left = center.x - NodeRadius
        moveTo(left, top + amplitude * sin(phase))
        var x = 0f
        while (x <= 2 * NodeRadius) {
            lineTo(left + x, top + amplitude * sin(phase + x / (2 * NodeRadius) * WaveLength))
            x += WaveStep
        }
        lineTo(left + 2 * NodeRadius, center.y + NodeRadius + 1f)
        lineTo(left, center.y + NodeRadius + 1f)
        close()
    }
    clipPath(node) {
        drawPath(surface(wave + BackWaveShift), colors.routeEnd.copy(alpha = LiquidBackAlpha))
        drawPath(surface(wave), colors.routeStart.copy(alpha = LiquidFrontAlpha))
    }
}

private fun DrawScope.drawNode(center: Offset, fill: Color, ring: Color) {
    drawCircle(fill, radius = NodeRadius, center = center)
    drawCircle(ring, radius = NodeRadius, center = center, style = Stroke(2f))
}

// Geometry of the design's viewport.
private const val ViewportWidth = 364f
private const val ViewportHeight = 150f
private const val DeviceX = 30f
private const val ServerX = 334f
private const val NodeY = 65f
private const val NodeRadius = 24f
private const val GlyphHalf = 10f
private const val LabelTop = 100f
private const val LabelWidthFraction = 0.42f

private val CardPaddingHorizontal = 8.dp
private val CardPaddingTop = 22.dp
private val CardPaddingBottom = 10.dp

private val UnlitRing = Color.White.copy(alpha = 0.13f)
private const val GlowStrong = 0.22f
private const val GlowSoft = 0.12f

// Motion.
private const val LineMillis = 1_400
private const val FillWaitMillis = 2_200
private const val FillFinishMillis = 550
private const val FillWhileWaiting = 0.82f
private const val StillFill = 0.6f
private const val SettleMillis = 450
private const val DrainMillis = 350
private const val RetractMillis = 900
private const val TipHideAt = 0.97f
private const val PulseLow = 0.6f
private const val PulseMillis = 600
private const val FlowPeriod = 27f
private const val FlowCycleMillis = 1_600
private const val WaveCycleMillis = 1_300
private const val WaveAmplitude = 2.6f
private const val WaveLength = 2f * PI.toFloat() * 1.4f
private const val WaveStep = 2f
private const val BackWaveShift = 2.1f
private const val TwoPi = 2f * PI.toFloat()
private const val LiquidFrontAlpha = 0.55f
private const val LiquidBackAlpha = 0.3f

// Where the failed line breaks.
private const val FailedReach = 0.6f
private const val FailedBreak = 0.66f
private const val FailedResume = 0.72f
