package app.vazie.vpn.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieGuideDefaults
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import kotlin.math.roundToInt

/** The dim layer, the cutout and the callout, over an interface the reader can still see. */
@Composable
fun VazieTourOverlay(
    target: Rect?,
    modifier: Modifier = Modifier,
    callout: @Composable () -> Unit,
) {
    val colors = VazieTheme.colors
    val density = LocalDensity.current
    val reduceMotion = VazieTheme.reduceMotion
    val inset = with(density) { VazieGuideDefaults.spotlightInset.toPx() }
    val borderWidth = with(density) { VazieGuideDefaults.spotlightBorder.toPx() }
    val radius = with(density) { VazieTheme.shapes.lg.topStart.toPx(Size.Zero, density) }
    val gap = with(density) { VazieGuideDefaults.calloutGap.roundToPx() }

    // Nothing at all until a target has been measured. Not a scrim without a hole, not a hole at
    // the origin - nothing. See the class comment.
    val spotlight = target?.inflate(inset) ?: return

    // Each edge animates separately, so the eye can follow the spotlight between steps.
    val spec = if (reduceMotion) snap() else tween<Float>(VazieGuideDefaults.TRANSITION_MILLIS)
    val left by animateFloatAsState(spotlight.left, spec, label = "spotlightLeft")
    val top by animateFloatAsState(spotlight.top, spec, label = "spotlightTop")
    val right by animateFloatAsState(spotlight.right, spec, label = "spotlightRight")
    val bottom by animateFloatAsState(spotlight.bottom, spec, label = "spotlightBottom")
    val current = remember(left, top, right, bottom) { Rect(left, top, right, bottom) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Swallows every tap, including inside the cutout, without claiming to be a control.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Without this, `Clear` erases through the window instead of through this layer.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(color = colors.scrim.copy(alpha = VazieGuideDefaults.SCRIM_ALPHA))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(current.left, current.top),
                size = Size(current.width, current.height),
                cornerRadius = CornerRadius(radius, radius),
                blendMode = BlendMode.Clear,
            )
            // The second of three channels identifying the target. See `VazieGuideDefaults`.
            // `VazieGuideDefaults`.
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset(current.left, current.top),
                size = Size(current.width, current.height),
                cornerRadius = CornerRadius(radius, radius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth),
            )
        }

        CalloutSlot(
            spotlight = current,
            gap = gap,
            modifier = Modifier
                .fillMaxSize()
                // The callout stays clear of system bars and cutouts; the spotlight gets the rest.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = VazieTheme.spacing.screenHorizontal),
            content = callout,
        )
    }
}

/** Puts the callout above the spotlight or below it, whichever side has room. */
@Composable
private fun CalloutSlot(
    spotlight: Rect,
    gap: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(
            Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight),
        )
        if (placeable == null) {
            return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Coordinates arrive in root space; this layout is inset, so convert first.
            val originY = coordinates?.positionInRoot()?.y ?: 0f
            val above = (spotlight.top - originY - gap).roundToInt()
            val below = (spotlight.bottom - originY + gap).roundToInt()

            val y = when {
                placeable.height <= above -> above - placeable.height
                placeable.height <= constraints.maxHeight - below -> below
                // Neither side fits: the callout takes the room there is and the spotlight is what
                // overlaps. See this function's documentation.
                else -> (constraints.maxHeight - placeable.height) / 2
            }
            placeable.place(
                x = 0,
                y = y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0)),
            )
        }
    }
}

private fun Rect.inflate(by: Float): Rect =
    Rect(left - by, top - by, right + by, bottom + by)
