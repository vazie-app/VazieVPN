package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** Signal bars with a reading beside them — how well a server answers from here. */
@Composable
fun VazieSignal(
    bars: Int,
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
) {
    val colors = VazieTheme.colors
    val filled = when {
        bars >= 3 -> colors.success
        bars == 2 -> colors.warning
        bars == 1 -> colors.error
        else -> colors.textDisabled
    }
    val empty = colors.border
    Row(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalBars(bars = bars.coerceIn(0, VazieSignalDefaults.MAX_BARS), filled = filled, empty = empty)
        Text(text = label, style = VazieTheme.typography.monoSmall, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun SignalBars(bars: Int, filled: Color, empty: Color) {
    val max = VazieSignalDefaults.MAX_BARS
    Canvas(modifier = Modifier.size(width = BAR_WIDTH * max + BAR_GAP * (max - 1), height = BAR_HEIGHT)) {
        val width = BAR_WIDTH.toPx()
        val gap = BAR_GAP.toPx()
        val radius = CornerRadius(width / 2, width / 2)
        for (index in 0 until max) {
            val height = size.height * (index + 1) / max
            drawRoundRect(
                color = if (index < bars) filled else empty,
                topLeft = Offset(index * (width + gap), size.height - height),
                size = Size(width, height),
                cornerRadius = radius,
            )
        }
    }
}

object VazieSignalDefaults {
    const val MAX_BARS: Int = 4
}

private val BAR_WIDTH = 3.dp
private val BAR_GAP = 2.dp
private val BAR_HEIGHT = 12.dp
