package app.vazie.vpn.core.designsystem.icon

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few glyphs the kit needs for itself, drawn to the documented icon language: 24dp grid, 2dp stroke,
 * rounded caps and joins. */
object VazieIcons {

    val ChevronRight: ImageVector by lazy {
        stroked("ChevronRight") { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    }

    val ArrowLeft: ImageVector by lazy {
        stroked("ArrowLeft") {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }

    val Check: ImageVector by lazy {
        stroked("Check") { moveTo(5f, 13f); lineTo(9.5f, 17.5f); lineTo(19f, 7f) }
    }

    val Close: ImageVector by lazy {
        stroked("Close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    /** The management actions a configuration has, as placeholders for artwork that does not exist yet. */
    val Rename: ImageVector by lazy {
        stroked("Rename") {
            moveTo(4f, 20f); lineTo(8f, 19f); lineTo(19f, 8f); lineTo(16f, 5f); lineTo(5f, 16f); close()
        }
    }

    val Duplicate: ImageVector by lazy {
        stroked("Duplicate") {
            moveTo(9f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(9f, 20f); close()
            moveTo(15f, 5f); lineTo(4f, 5f); lineTo(4f, 16f)
        }
    }

    val Export: ImageVector by lazy {
        stroked("Export") {
            moveTo(12f, 15f); lineTo(12f, 4f)
            moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
            moveTo(5f, 14f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 14f)
        }
    }

    /** Paste: a clipboard with a sheet on it. */
    val Paste: ImageVector by lazy {
        svg(
            "Paste",
            "M9 4h6v3H9z",
            "M15 5.5h2.5a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V7a1.5 1.5 0 0 1 1.5-1.5H9",
            "M9 12h6",
            "M9 15.5h4",
        )
    }

    val Delete: ImageVector by lazy {
        stroked("Delete") {
            moveTo(5f, 7f); lineTo(19f, 7f)
            moveTo(10f, 4f); lineTo(14f, 4f)
            moveTo(7f, 7f); lineTo(8f, 20f); lineTo(16f, 20f); lineTo(17f, 7f)
        }
    }

    val Plus: ImageVector by lazy {
        stroked("Plus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    // "Маршрут" icons, drawn from the approved design's own SVG paths so they match it exactly.

    /** Connect / disconnect. */
    val Power: ImageVector by lazy { svg("Power", "M12 3.5v8", "M7 6.5a7.5 7.5 0 1 0 10 0") }

    /** Try again. */
    val Retry: ImageVector by lazy {
        svg("Retry", "M19.5 11A7.5 7.5 0 0 0 6 7.2", "M5 3.8v4h4", "M4.5 13A7.5 7.5 0 0 0 18 16.8", "M19 20.2v-4h-4")
    }

    /** Settings: eight teeth at equal angles on a round body, as wide as it is tall. The mockup's own outline
     * was 15.4 × 18.4 and read as a stretched gear at 22 dp, so this one is drawn on a circle. */
    val Settings: ImageVector by lazy {
        svg(
            "Settings",
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
            "M10.21 5.34 L10.68 3.20 L13.32 3.20 L13.79 5.34 L15.45 6.02 L17.29 4.85 L19.15 6.71 " +
                "L17.98 8.55 L18.66 10.21 L20.80 10.68 L20.80 13.32 L18.66 13.79 L17.98 15.45 " +
                "L19.15 17.29 L17.29 19.15 L15.45 17.98 L13.79 18.66 L13.32 20.80 L10.68 20.80 " +
                "L10.21 18.66 L8.55 17.98 L6.71 19.15 L4.85 17.29 L6.02 15.45 L5.34 13.79 " +
                "L3.20 13.32 L3.20 10.68 L5.34 10.21 L6.02 8.55 L4.85 6.71 L6.71 4.85 L8.55 6.02Z",
        )
    }

    /** This device, at the start of the route. */
    val Phone: ImageVector by lazy {
        svg("Phone", "M10 2.8h4a3 3 0 0 1 3 3v12.4a3 3 0 0 1-3 3h-4a3 3 0 0 1-3-3V5.8a3 3 0 0 1 3-3z", "M11 18h2")
    }

    /** Something went wrong. */
    val Alert: ImageVector by lazy {
        svg("Alert", "M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0", "M12 11v5", "M12 8h0.01")
    }

    /** In progress. */
    val Clock: ImageVector by lazy {
        svg("Clock", "M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0", "M12 7.5V12l3 2")
    }

    /** Not available yet. */
    val Lock: ImageVector by lazy {
        svg("Lock", "M7.5 11V8a4.5 4.5 0 0 1 9 0v3", "M7.5 11h9a2.5 2.5 0 0 1 2.5 2.5v4a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5v-4A2.5 2.5 0 0 1 7.5 11z")
    }

    /** A server. */
    val Server: ImageVector by lazy {
        svg(
            "Server",
            "M6 4h12a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M6 13h12a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
            "M8 7.5h.01",
            "M8 16.5h.01",
        )
    }
}

private fun filled(
    name: String,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(androidx.compose.ui.graphics.Color.Black),
        pathBuilder = pathBuilder,
    )
}.build()

private fun stroked(
    name: String,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}.build()

/** A stroked icon from SVG path data, at the design's 1.8 stroke. */
private fun svg(name: String, vararg paths: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    paths.forEach { data ->
        addPath(
            pathData = addPathNodes(data),
            stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = RouteStroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

private const val RouteStroke = 1.8f
