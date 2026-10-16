package app.vazie.vpn.shortcuts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A shortcut icon is drawn by somebody else, on a background nobody chose. */
class ShortcutIconTest {

    @Test
    fun `every shortcut icon carries its own background`() {
        // The whole root cause in one assertion. A launcher gives a shortcut icon no plate; an icon
        // that does not bring one is drawn straight onto whatever is behind it.
        icons().forEach { file ->
            val text = file.readText()
            assertTrue(
                text.contains("<adaptive-icon"),
                "${file.name} is not an adaptive icon, so it has no background of its own",
            )
            assertTrue(
                text.contains("<background"),
                "${file.name} declares no background layer",
            )
        }
    }

    @Test
    fun `every shortcut icon survives a themed launcher`() {
        icons().forEach { file ->
            assertTrue(
                file.readText().contains("<monochrome"),
                "${file.name} has no layer for a themed launcher to tint",
            )
        }
    }

    @Test
    fun `every shortcut foreground is drawn on the canvas a launcher masks`() {
        // 108dp is the adaptive-icon canvas; the launcher may crop to a 66dp circle.
        foregrounds().forEach { file ->
            val text = file.readText()
            assertTrue(
                VIEWPORT_WIDTH.find(text)?.groupValues?.get(1) == CANVAS &&
                    VIEWPORT_HEIGHT.find(text)?.groupValues?.get(1) == CANVAS,
                "${file.name} is not drawn on the ${CANVAS}dp adaptive canvas",
            )
        }
    }

    @Test
    fun `no shortcut glyph is a stroke`() {
        // Themed icons tint the alpha channel flat, so strokes become hairlines; shapes must be filled.
        foregrounds().forEach { file ->
            val markup = file.readText().replace(COMMENT, "")
            assertTrue(
                !markup.contains("android:strokeWidth"),
                "${file.name} draws its glyph with a stroke",
            )
            assertTrue(
                markup.contains("android:fillColor"),
                "${file.name} has no fill",
            )
        }
    }

    @Test
    fun `a generated shortcut icon is adaptive and carries only a two-letter mark`() {
        // Configuration shortcuts draw only their row mark at runtime, never profile material.
        val source = File("src/main/kotlin/app/vazie/vpn/shortcuts/VazieShortcuts.kt").readText()
        assertTrue(!source.contains("createWithBitmap("), "a shortcut icon is a plain bitmap, not adaptive")
        assertTrue(source.contains("createWithAdaptiveBitmap("), "the generated icon is not adaptive")
        assertEquals(1, DRAW_TEXT.findAll(source).count(), "the icon draws more text than its mark")
        assertTrue(DRAW_TEXT.find(source)!!.value.contains("mark"), "the icon draws something other than the mark")
    }

    private fun icons(): List<File> = File("src/main/res/drawable")
        .listFiles()
        .orEmpty()
        .filter { it.name.startsWith("ic_shortcut_") && !it.name.endsWith("_foreground.xml") }
        .also { assertTrue(it.isNotEmpty(), "no shortcut icons found") }

    private fun foregrounds(): List<File> = File("src/main/res/drawable")
        .listFiles()
        .orEmpty()
        .filter { it.name.startsWith("ic_shortcut_") && it.name.endsWith("_foreground.xml") }
        .also { assertTrue(it.isNotEmpty(), "no shortcut foregrounds found") }

    private companion object {
        const val CANVAS = "108"
        val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
        val VIEWPORT_WIDTH = Regex("""android:viewportWidth="([0-9.]+)"""")
        val VIEWPORT_HEIGHT = Regex("""android:viewportHeight="([0-9.]+)"""")
        val DRAW_TEXT = Regex("""drawText\([^\n]*""")
    }
}
