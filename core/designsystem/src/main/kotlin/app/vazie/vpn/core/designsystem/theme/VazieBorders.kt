package app.vazie.vpn.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Outline widths: the width, not only the colour, carries focus and error. */
@Immutable
data class VazieBorders(
    /** Dividers and flat surfaces that use an outline instead of a shadow. */
    val hairline: Dp = 1.dp,
    /** Resting outline on an interactive surface. */
    val regular: Dp = 1.5.dp,
    /** Focused, selected or invalid outline. */
    val strong: Dp = 2.dp,
)
