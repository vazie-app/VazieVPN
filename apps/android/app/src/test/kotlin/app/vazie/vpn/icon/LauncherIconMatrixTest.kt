package app.vazie.vpn.icon

import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every face a person can choose has to exist as a resource, and no resource may exist without one. */
class LauncherIconMatrixTest {

    @Test
    fun `there is exactly one adaptive icon per pairing the marks offer`() {
        val expected = VazieAppIcon.entries.flatMap { icon ->
            icon.plates.map { plate -> resourceName(icon, plate) }
        }.toSet()

        val actual = adaptiveIcons()
            .map { it.name.removeSuffix(".xml") }
            .filterNot { it in APPLICATION_LEVEL }
            .toSet()

        assertEquals(expected, actual, "the icon matrix and mipmap-anydpi/ disagree")
    }

    /** Every cell names a plate that exists and a foreground that exists. */
    @Test
    fun `every layer a cell names is a file that exists`() {
        adaptiveIcons().forEach { file ->
            val text = file.readText()
            val background = LAYER.find(text, text.indexOf("<background"))
            assertTrue(background != null, "${file.name} declares no background")

            REFERENCE.findAll(text.substringAfter("<adaptive-icon")).forEach { match ->
                val (type, name) = match.destructured
                assertTrue(
                    resourceExists(type, name),
                    "${file.name} points at @$type/$name, which does not exist",
                )
            }
        }
    }

    /** The plate a cell is named after is the plate it draws. */
    @Test
    fun `each cell draws the plate its name promises`() {
        VazieAppIcon.entries.forEach { icon ->
            icon.plates.forEach { plate ->
                val file = File(MIPMAP, "${resourceName(icon, plate)}.xml")
                val text = file.readText()
                val expected = "@drawable/ic_launcher_plate_${plate.id}"
                assertTrue(
                    text.contains("<background android:drawable=\"$expected\""),
                    "${file.name} is named for ${plate.id} and does not draw $expected",
                )
            }
        }
    }

    /** The flat mark changes ink with the plate; the illustrated three never do. */
    @Test
    fun `only the flat mark varies its foreground by plate`() {
        VazieAppIcon.entries.forEach { icon ->
            val foregrounds = icon.plates.map { plate ->
                foregroundOf(File(MIPMAP, "${resourceName(icon, plate)}.xml"))
            }
            if (icon == VazieAppIcon.MONOCHROME) {
                assertEquals(
                    "@drawable/ic_launcher_mark_ink",
                    foregroundOf(File(MIPMAP, "${resourceName(icon, VazieAppIconPlate.LIGHT)}.xml")),
                    "the flat mark on the light plate must be ink",
                )
                assertEquals(
                    icon.plates.size,
                    foregrounds.toSet().size,
                    "the flat mark takes one ink per plate: paper on dark, ink on light",
                )
            } else {
                assertEquals(
                    1,
                    foregrounds.toSet().size,
                    "${icon.name} carries its own colour and must be one file on every plate",
                )
            }
        }
    }

    /** Every illustrated mark has a raster at all five densities. */
    /** Every mark stands on its own signature, and every plate it offers is one of the five that exist. */
    @Test
    fun `every mark offers its signature and nothing that is not a plate`() {
        VazieAppIcon.entries.forEach { icon ->
            assertTrue(
                icon.signaturePlate in icon.plates,
                "${icon.name} does not offer its own signature plate",
            )
            assertEquals(
                icon.plates.size,
                icon.plates.toSet().size,
                "${icon.name} lists a plate twice",
            )
            icon.plates.forEach { plate ->
                assertTrue(plate in VazieAppIconPlate.concrete, "${icon.name} offers $plate")
                assertTrue(icon.supports(plate), "${icon.name}.supports disagrees with its own list")
            }
        }
    }

    @Test
    fun `illustrated marks ship at every density`() {
        val illustrated = VazieAppIcon.entries - VazieAppIcon.MONOCHROME
        illustrated.forEach { icon ->
            DENSITIES.forEach { density ->
                val file = File(RES, "mipmap-$density/ic_launcher_${icon.id}_foreground.webp")
                assertTrue(file.exists(), "${icon.name} has no $density foreground")
            }
        }
    }

    private fun resourceName(icon: VazieAppIcon, plate: VazieAppIconPlate) =
        "ic_launcher_${icon.id}_${plate.id}"

    private fun foregroundOf(file: File): String =
        REFERENCE.findAll(file.readText().substringAfter("<foreground"))
            .first()
            .let { "@${it.groupValues[1]}/${it.groupValues[2]}" }

    private fun adaptiveIcons(): List<File> =
        File(MIPMAP).listFiles()?.filter { it.extension == "xml" }.orEmpty().sortedBy { it.name }

    private fun resourceExists(type: String, name: String): Boolean = when (type) {
        "drawable" -> File(RES, "drawable/$name.xml").exists()
        "mipmap" -> DENSITIES.any { File(RES, "mipmap-$it/$name.webp").exists() }
        else -> false
    }

    private companion object {
        const val RES = "src/main/res"
        const val MIPMAP = "$RES/mipmap-anydpi"

        /** The `<application>` icons: the fallback before any alias is enabled, same pairing as the
         * default alias. */
        val APPLICATION_LEVEL = setOf("ic_launcher", "ic_launcher_round")
        val DENSITIES = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val LAYER = Regex("""<(background|foreground|monochrome)\b""")
        val REFERENCE = Regex("""android:drawable="@(drawable|mipmap)/([^"]+)"""")
    }
}
