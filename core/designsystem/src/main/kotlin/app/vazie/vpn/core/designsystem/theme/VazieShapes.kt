package app.vazie.vpn.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Cards and sheets use `lg`; buttons, fields and rows use `md`; badges and inline chips use `sm` or [pill].
 * Pills are for switches, chips and the connect button — not every container. */
@Immutable
data class VazieShapes(
    val smRadius: Dp = 12.dp,
    val mdRadius: Dp = 16.dp,
    val lgRadius: Dp = 22.dp,
    val xlRadius: Dp = 28.dp,
    /** The route card on Home. */
    val heroRadius: Dp = 32.dp,
) {
    // Typed as CornerBasedShape so these can back a Material 3 `Shapes` directly.
    val sm: CornerBasedShape = RoundedCornerShape(smRadius)
    val md: CornerBasedShape = RoundedCornerShape(mdRadius)
    val lg: CornerBasedShape = RoundedCornerShape(lgRadius)
    val xl: CornerBasedShape = RoundedCornerShape(xlRadius)
    val hero: CornerBasedShape = RoundedCornerShape(heroRadius)
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50)

    /** Bottom sheets: rounded top corners only. */
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = lgRadius, topEnd = lgRadius)
}
