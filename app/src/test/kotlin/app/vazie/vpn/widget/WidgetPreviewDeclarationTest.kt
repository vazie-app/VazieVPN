package app.vazie.vpn.widget

import androidx.compose.ui.unit.DpSize
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import java.io.DataInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the widget picker is promised, checked against what is in the repository. */
class WidgetPreviewDeclarationTest {

    @Test
    fun `every widget previews itself with a rendered image`() {
        WIDGETS.forEach { widget ->
            listOf("xml", "xml-v31").forEach { folder ->
                val info = File("src/main/res/$folder/${widget.info}_info.xml").readText()
                val drawable = PREVIEW_DRAWABLE.find(info)?.groupValues?.get(1)
                assertTrue(drawable != null, "$folder/${widget.info} declares no previewImage")
                assertTrue(
                    File("$DAY/$drawable.webp").isFile,
                    "$folder/${widget.info} points at a preview that has not been recorded: " +
                        "$drawable",
                )
            }
        }
    }

    @Test
    fun `a preview matches the device's theme rather than the developer's`() {
        WIDGETS.forEach { widget ->
            val info = File("src/main/res/xml-v31/${widget.info}_info.xml").readText()
            val drawable = PREVIEW_DRAWABLE.find(info)?.groupValues?.get(1)
            assertTrue(
                File("$NIGHT/$drawable.webp").isFile,
                "${widget.info} has no dark preview; a dark device would be shown a light card",
            )
        }
    }

    /** The picker is shown a card the size of the cell it is offering, on every device. */
    @Test
    fun `a preview is the size of the cell it advertises, whatever the device's density is`() {
        WIDGETS.forEach { widget ->
            listOf(DAY, NIGHT).forEach { folder ->
                val file = File("$folder/${widget.drawable}.webp")
                val (pixelWidth, pixelHeight) = file.webpSize()
                val scale = folder.bucketScale()
                assertEquals(
                    widget.cell.width.value.toInt(),
                    pixelWidth / scale,
                    "${file.path} is ${pixelWidth}px in a ${scale}x bucket, so a launcher reads it " +
                        "as ${pixelWidth / scale}dp wide and offers a cell of " +
                        "${widget.cell.width.value.toInt()}dp",
                )
                assertEquals(
                    widget.cell.height.value.toInt(),
                    pixelHeight / scale,
                    "${file.path} is ${pixelHeight}px in a ${scale}x bucket, so a launcher reads " +
                        "it as ${pixelHeight / scale}dp tall and offers a cell of " +
                        "${widget.cell.height.value.toInt()}dp",
                )
            }
        }
    }

    /** A preview keeps the widget's proportions, so the picker neither stretches nor crops it. */
    @Test
    fun `a preview is not stretched or cropped out of the widget's shape`() {
        WIDGETS.forEach { widget ->
            listOf(DAY, NIGHT).forEach { folder ->
                val file = File("$folder/${widget.drawable}.webp")
                val (pixelWidth, pixelHeight) = file.webpSize()
                assertEquals(
                    (widget.cell.width.value / widget.cell.height.value).toDouble(),
                    pixelWidth.toDouble() / pixelHeight,
                    ASPECT_TOLERANCE,
                    "${file.path} is ${pixelWidth}x$pixelHeight, which is not the shape of a " +
                        "${widget.cell.width.value.toInt()}x${widget.cell.height.value.toInt()} cell",
                )
            }
        }
    }

    /** Nothing is left where a preview is told not to scale. */
    @Test
    fun `no preview is filed where Android is told not to scale it`() {
        val offenders = File("src/main/res").walkTopDown()
            .filter { it.isFile && it.name.startsWith("widget_preview") }
            .filter { it.parentFile.name.contains("nodpi") }
            .map { it.path }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "a preview is back in a nodpi bucket, where its dp size is whatever the device's " +
                "density happens to make it:\n$offenders",
        )
    }

    @Test
    fun `no widget still previews itself as the launcher icon`() {
        infoFiles().forEach { file ->
            assertTrue(
                !file.readText().contains("android:previewImage=\"@mipmap/"),
                "${file.path} previews the app icon instead of the widget",
            )
        }
    }

    @Test
    fun `nothing is left of the hand-written previews`() {
        val offenders = (File("src/main/res").walkTopDown())
            .filter { it.isFile }
            .filter { it.name.startsWith("widget_preview") && it.extension == "xml" }
            .map { it.path }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "a hand-written preview is back, and it will drift from the widget:\n$offenders",
        )
    }

    @Test
    fun `no widget declares a preview layout for the rendered image to lose to`() {
        infoFiles().forEach { file ->
            assertTrue(
                !file.readText().contains("android:previewLayout="),
                "${file.path} declares a previewLayout; on API 31+ it wins over the rendered image, " +
                    "and it is a second implementation of the widget's design",
            )
        }
    }

    private fun infoFiles(): List<File> =
        (WIDGETS.map { "src/main/res/xml/${it.info}_info.xml" } +
            WIDGETS.map { "src/main/res/xml-v31/${it.info}_info.xml" })
            .map { File(it) }

    /** A WebP's dimensions, read from the RIFF header rather than decoded. */
    private fun File.webpSize(): Pair<Int, Int> {
        assertTrue(isFile, "$path is missing - the widget picker preview must be committed as a resource")
        val bytes = readBytes()
        fun u8(at: Int) = bytes[at].toInt() and 0xFF
        fun u16(at: Int) = u8(at) or (u8(at + 1) shl 8)
        fun u24(at: Int) = u16(at) or (u8(at + 2) shl 16)
        assertTrue(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP", "$path is not a WebP")
        return when (val chunk = String(bytes, 12, 4)) {
            "VP8X" -> (u24(24) + 1) to (u24(27) + 1)
            "VP8 " -> (u16(26) and 0x3FFF) to (u16(28) and 0x3FFF)
            "VP8L" -> {
                val bits = u16(21) or (u16(23) shl 16)
                ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
            }
            else -> error("$path starts with an unknown WebP chunk $chunk")
        }
    }

    /** What one dp is worth in the bucket a resource sits in. */
    private fun String.bucketScale(): Int {
        val bucket = substringAfterLast('/')
        // Longest suffix first, because "xxhdpi" ends with "xhdpi" and a shorter match would call a
        // 3x bucket a 2x one.
        return BUCKET_SCALES.keys.sortedByDescending(String::length)
            .firstOrNull(bucket::endsWith)
            ?.let(BUCKET_SCALES::getValue)
            ?: error("$this is not a density bucket a preview may be filed in")
    }

    private data class PreviewedWidget(val info: String, val drawable: String, val cell: DpSize)

    private companion object {
        /** Every widget Vazie ships, the cell each is designed for, and the list the picker is expected to
         * contain. */
        val WIDGETS = listOf(
            PreviewedWidget(
                info = "widget_quick_connect",
                drawable = "widget_preview_quick_connect",
                cell = VazieWidgetSizes.squareTarget,
            ),
            PreviewedWidget(
                info = "widget_dashboard",
                drawable = "widget_preview_dashboard",
                cell = VazieWidgetSizes.wideTarget,
            ),
        )

        const val DAY = "src/main/res/drawable-xxhdpi"
        const val NIGHT = "src/main/res/drawable-night-xxhdpi"

        val BUCKET_SCALES = mapOf("xxxhdpi" to 4, "xxhdpi" to 3, "xhdpi" to 2)

        const val ASPECT_TOLERANCE = 0.01

        val PREVIEW_DRAWABLE = Regex("""android:previewImage="@drawable/([^"]+)"""")
    }
}
