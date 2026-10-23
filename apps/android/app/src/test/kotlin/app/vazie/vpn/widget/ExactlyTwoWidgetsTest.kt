package app.vazie.vpn.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** There are two Vazie widgets, and the picker must agree with the source tree about which two. */
class ExactlyTwoWidgetsTest {

    @Test
    fun `the picker offers exactly the widgets Vazie ships`() {
        val declared = PROVIDER_METADATA.findAll(manifest())
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            EXPECTED,
            declared.toSet(),
            "the manifest declares a different set of widgets than Vazie ships",
        )
        assertEquals(
            EXPECTED.size,
            declared.size,
            "a widget is declared twice: $declared",
        )
    }

    @Test
    fun `nothing is left over from the widget that was removed`() {
        // Names, not classes: the leftovers that matter are the ones no compiler looks at - a
        // string resource, a drawable, an entry in a layout folder.
        val offenders = File("src/main")
            .walkTopDown()
            .filter { it.isFile }
            .filter { file -> REMOVED.any { file.name.contains(it) } }
            .map { it.path }
            .toList()

        assertTrue(offenders.isEmpty(), "the removed widget left files behind:\n$offenders")
    }

    @Test
    fun `every declared widget has a receiver class to back it`() {
        RECEIVER_NAME.findAll(manifest())
            .map { it.groupValues[1] }
            .filter { it.startsWith(".widget.") }
            .forEach { name ->
                val path = "src/main/kotlin/app/vazie/vpn" + name.replace('.', '/') + ".kt"
                assertTrue(File(path).exists(), "$name is declared but not written: $path")
            }
    }

    private fun manifest(): String = File("src/main/AndroidManifest.xml").readText()

    private companion object {
        val EXPECTED = setOf("widget_quick_connect_info", "widget_dashboard_info")

        /** Fragments of the removed widget's names. Anything still carrying one of these is a leftover. */
        val REMOVED = listOf("ConfigurationSwitcher", "configuration_switcher")

        val PROVIDER_METADATA = Regex("""android:resource="@xml/([^"]+)"""")
        val RECEIVER_NAME = Regex("""<receiver\s+android:name="([^"]+)"""")
    }
}
