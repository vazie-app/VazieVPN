package app.vazie.vpn.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One soft shadow; flat surfaces use a 1dp border instead. */
@Immutable
data class VazieElevation(
    val none: Dp = 0.dp,
    val restingY: Dp = 6.dp,
    val restingBlur: Dp = 16.dp,
    val restingAlpha: Float = 0.06f,
    val floatingY: Dp = 8.dp,
    val floatingBlur: Dp = 24.dp,
    val floatingAlpha: Float = 0.08f,
)

/** `rgb(30, 20, 60)` from the design; alpha is applied per surface. */
val VazieShadowColor: Color = Color(red = 30, green = 20, blue = 60)

/** @param alpha 0.06 for rows and chips, 0.08-0.10 for floating surfaces. */
fun Modifier.vazieShadow(
    shape: Shape,
    y: Dp = 8.dp,
    blur: Dp = 24.dp,
    alpha: Float = 0.06f,
    color: Color = VazieShadowColor,
): Modifier = drawBehind {
    val blurPx = blur.toPx()
    val yPx = y.toPx()
    if (blurPx <= 0f && yPx == 0f) return@drawBehind

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        // Transparent fill so only the shadow layer paints, matching a CSS box-shadow.
        frameworkPaint.color = android.graphics.Color.TRANSPARENT
        frameworkPaint.setShadowLayer(blurPx, 0f, yPx, color.copy(alpha = alpha).toArgb())
        canvas.drawOutline(shape.createOutline(size, layoutDirection, this), paint)
    }
}
