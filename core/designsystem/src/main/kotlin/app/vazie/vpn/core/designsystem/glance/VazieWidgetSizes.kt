package app.vazie.vpn.core.designsystem.glance

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The cells Vazie's widgets are drawn for, and the one width at which the composition turns. */
object VazieWidgetSizes {

    /** The smallest 2×2 a launcher may hand over. */
    val squareMin: DpSize = DpSize(110.dp, 110.dp)

    /** A 2×2 on a typical grid. */
    val squareTarget: DpSize = DpSize(130.dp, 130.dp)

    /** A 2×2 stretched down a row. */
    val squareTall: DpSize = DpSize(130.dp, 180.dp)

    /** A 4×2 a launcher has squeezed. */
    val wideMin: DpSize = DpSize(180.dp, 110.dp)

    /** The 4×2 the wide widget is designed for. */
    val wideTarget: DpSize = DpSize(250.dp, 110.dp)

    /** A 4×2 stretched down a row. */
    val wideTall: DpSize = DpSize(250.dp, 180.dp)

    /** What a mainstream launcher actually hands a 2×2, which is nothing like 130×130. */
    val squareRoomy: DpSize = DpSize(180.dp, 200.dp)

    /** What a mainstream launcher actually hands a 4×2, which is not 250×180 either. */
    val wideRoomy: DpSize = DpSize(390.dp, 200.dp)

    /** At or above this width the action stands beside the text instead of under it. */
    val splitWidth = 240.dp

    /** At or above this width the state word is set at full size and spelled in full. */
    val roomyWidth = 170.dp

    /** The narrowest text column that can hold a connection state spelled out in full. */
    val fullStateWidth = 140.dp

    /** At or above this height a cell can afford the roomier padding. */
    val paddedHeight = 168.dp

    /** At or above this height the dashboard can give its Vazie locations a row they can be tapped in. */
    val selectableHeight = 150.dp

    /** At or above this width a third configuration chip has somewhere to go. */
    val chipRowWidth = 300.dp

    /** At or above this width a cell can afford the roomier frame left and right. */
    val paddedWidth = 280.dp

    /** The narrowest text column worth leaving beside the control, and what stops the control eating it. */
    val shortStateWidth = 92.dp
}
