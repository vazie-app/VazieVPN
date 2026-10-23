package app.vazie.vpn.core.designsystem.glance

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Widget dimensions. */
@Immutable
data class VazieGlanceDimens(
    /** Padding between the widget's rounded background and its content. */
    val widgetPadding: Dp = 16.dp,
    /** Padding at the smallest cell a widget may be resized to. */
    val widgetPaddingCompact: Dp = 12.dp,
    /** Vertical padding for a short wide dashboard cell. */
    val widgetPaddingTight: Dp = 8.dp,
    /** Corner radius of the widget canvas. Ignored below API 31, where the launcher squares it off. */
    val widgetCorner: Dp = 24.dp,
    /** Gap between stacked lines of text. */
    val lineGap: Dp = 2.dp,
    /** Gap between a widget's distinct blocks — the rail, the state, the action. */
    val blockGap: Dp = 6.dp,
    /** Gap between a row's items. */
    val itemGap: Dp = 8.dp,
    /** Thickness of one segment of the phase rail. */
    val railHeight: Dp = 4.dp,
    /** Length of one segment of the phase rail. */
    val railSegment: Dp = 14.dp,
    /** Gap between the rail's segments. */
    val railGap: Dp = 3.dp,
    /** What one line of each text role costs in height, for the layout that decides how many fit. */
    val stateLine: Dp = 22.dp,
    val serverLine: Dp = 18.dp,
    val readingLine: Dp = 16.dp,
    /** The width of the connect control on a wide widget, the same in every state. */
    val actionWidth: Dp = 96.dp,
    /** Corner radius of the connect control, matching `VazieShapes.md`. */
    val actionCorner: Dp = 16.dp,
    /** The hairline that makes an outlined action read as a button rather than as a tinted label. */
    val actionBorder: Dp = 1.dp,
    /** Minimum height of anything tappable. 48dp is the accessibility floor and it does not relax because the
     * surface is small — a widget that misses a tap is worse than a widget that shows one line less. */
    val minTouchTarget: Dp = 48.dp,
    /** Corner radius of a configuration chip. */
    val rowCorner: Dp = 12.dp,
    /** Height of a configuration chip. */
    val rowHeight: Dp = 36.dp,
)
