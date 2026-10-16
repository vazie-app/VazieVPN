package app.vazie.vpn.core.designsystem.glance

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle

/** Widget type. */
@Immutable
data class VazieGlanceTypography(
    val state: TextStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    val stateCompact: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    val server: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    val body: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    val reading: TextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    val technical: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
    ),
    val action: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
)
