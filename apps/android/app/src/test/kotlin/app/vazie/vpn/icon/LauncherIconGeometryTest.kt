package app.vazie.vpn.icon

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

/** The icon has to be inside the circle every launcher keeps, and centred in it. */
class LauncherIconGeometryTest {

    @Test
    fun `no launcher icon leaves the safe circle`() {
        forEachForeground { file, image ->
            val size = image.width
            val centre = size / 2.0
            val safeRadius = size * SAFE_DIAMETER_DP / 2 / CANVAS_DP
            var furthest = 0.0
            for (y in 0 until image.height) {
                for (x in 0 until size) {
                    if ((image.getRGB(x, y) ushr 24 and 0xFF) <= ALPHA_THRESHOLD) continue
                    furthest = maxOf(furthest, hypot(x + 0.5 - centre, y + 0.5 - centre))
                }
            }
            assertTrue(
                furthest <= safeRadius + TOLERANCE_PX,
                "${file.parentFile.name}/${file.name} reaches ${"%.1f".format(furthest)}px, " +
                    "outside the ${"%.1f".format(safeRadius)}px safe circle - a round mask clips it",
            )
        }
    }

    @Test
    fun `every launcher icon is centred on its canvas`() {
        forEachForeground { file, image ->
            val size = image.width
            var minX = size
            var minY = size
            var maxX = -1
            var maxY = -1
            for (y in 0 until image.height) {
                for (x in 0 until size) {
                    if ((image.getRGB(x, y) ushr 24 and 0xFF) <= ALPHA_THRESHOLD) continue
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            val offsetX = (minX + maxX + 1) / 2.0 - size / 2.0
            val offsetY = (minY + maxY + 1) / 2.0 - size / 2.0
            val allowed = size * MAX_OFFSET_FRACTION
            assertTrue(
                kotlin.math.abs(offsetX) <= allowed && kotlin.math.abs(offsetY) <= allowed,
                "${file.parentFile.name}/${file.name} sits " +
                    "(${"%+.1f".format(offsetX)}, ${"%+.1f".format(offsetY)})px off centre, " +
                    "more than the ${"%.1f".format(allowed)}px allowed",
            )
        }
    }

    @Test
    fun `a foreground is square and the right size for its density`() {
        forEachForeground { file, image ->
            assertTrue(
                image.width == image.height,
                "${file.parentFile.name}/${file.name} is ${image.width}x${image.height}",
            )
            val expected = DENSITIES.getValue(file.parentFile.name)
            assertTrue(
                image.width == expected,
                "${file.parentFile.name}/${file.name} is ${image.width}px, expected ${expected}px",
            )
        }
    }

    @Test
    fun `every density carries every icon`() {
        DENSITIES.keys.forEach { bucket ->
            FOREGROUNDS.forEach { name ->
                val file = File("src/main/res/$bucket/$name.webp")
                assertTrue(file.exists(), "missing ${file.path}")
            }
        }
    }

    private fun forEachForeground(check: (File, java.awt.image.BufferedImage) -> Unit) {
        DENSITIES.keys.forEach { bucket ->
            FOREGROUNDS.forEach { name ->
                val file = File("src/main/res/$bucket/$name.webp")
                if (!file.exists()) return@forEach
                check(file, ImageIO.read(file))
            }
        }
    }

    private companion object {
        const val CANVAS_DP = 108.0
        const val SAFE_DIAMETER_DP = 66.0
        const val ALPHA_THRESHOLD = 8
        const val TOLERANCE_PX = 1.5
        const val MAX_OFFSET_FRACTION = 0.015

        /** Every raster layer a launcher draws on the 108dp canvas, themed layer included. */
        val FOREGROUNDS = listOf(
            "ic_launcher_orbit_foreground",
            "ic_launcher_monochrome",
            "ic_launcher_lime_foreground",
            "ic_launcher_emerald_foreground",
            "ic_launcher_magenta_foreground",
            "ic_launcher_cotton_candy_foreground",
            "ic_launcher_crimson_foreground",
            "ic_launcher_gold_foreground",
            "ic_launcher_pearl_foreground",
            "ic_launcher_onyx_foreground",
        )

        val DENSITIES = mapOf(
            "mipmap-mdpi" to 108,
            "mipmap-hdpi" to 162,
            "mipmap-xhdpi" to 216,
            "mipmap-xxhdpi" to 324,
            "mipmap-xxxhdpi" to 432,
        )
    }
}
