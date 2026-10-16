package app.vazie.vpn.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The geometry a guided tour is drawn with, in the one module allowed to hold a `dp` literal. */
object VazieGuideDefaults {

    /** How much air the cutout leaves around the control it reveals. */
    val spotlightInset: Dp = 8.dp

    /** The ring around the cutout. */
    val spotlightBorder: Dp = 2.dp

    /** How opaque the dim layer is. */
    const val SCRIM_ALPHA: Float = 0.72f

    /** The gap between the spotlight and the callout anchored to it. */
    val calloutGap: Dp = 12.dp

    /** How long the spotlight takes to travel between two targets. */
    const val TRANSITION_MILLIS: Int = 320
}
