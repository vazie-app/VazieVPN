package app.vazie.vpn.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp base unit, eight steps, plus the semantic aliases the design names directly. */
@Immutable
data class VazieSpacing(
    /** No space at all, as a token, so a conditional gap does not need a raw `0.dp` at a call site. */
    val zero: Dp = 0.dp,
    /** The half-step. */
    val xxxs: Dp = 2.dp,
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,

    /** Screen side margins. */
    val screenHorizontal: Dp = 20.dp,

    /** The room between a screen's top bar and its first block of content. */
    val screenContentTop: Dp = 20.dp,
    /** Padding inside cards. */
    val cardPadding: Dp = 16.dp,
    /** Standard list row height. */
    val listRowHeight: Dp = 56.dp,
    /** Gap between major sections. */
    val sectionGap: Dp = 32.dp,
    /** Accessibility minimum for anything tappable. */
    val minTouchTarget: Dp = 48.dp,
)
