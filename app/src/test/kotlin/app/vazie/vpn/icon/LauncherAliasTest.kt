package app.vazie.vpn.icon

import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The manifest and the icon switcher have to agree, and nothing at a call site makes them. */
class LauncherAliasTest {

    @Test
    fun `every mark on every plate has an alias in the manifest`() {
        val manifest = manifest()
        AppIconSwitcher.faces.forEach { face ->
            val name = AppIconSwitcher.aliasClassName(face).removePrefix(PACKAGE)
            assertTrue(
                manifest.contains("android:name=\"$name\""),
                "${face.icon.name} on ${face.plate.name} points at $name, " +
                    "which the manifest does not declare",
            )
        }
    }

    /** The matrix is the two enums multiplied, and the manifest holds exactly that many entries. */
    @Test
    fun `the alias matrix is exactly the pairings the marks offer`() {
        // A sum over the marks, not a product with the plates: each mark only on the plates it was checked
        // on.
        assertEquals(
            VazieAppIcon.entries.sumOf { it.plates.size },
            AppIconSwitcher.faces.size,
        )
        assertEquals(AppIconSwitcher.faces.size, AppIconSwitcher.faces.toSet().size)
        assertEquals(AppIconSwitcher.faces.size, aliases().size)
    }

    @Test
    fun `alias names are unique, so no two faces address the same component`() {
        val names = AppIconSwitcher.faces.map(AppIconSwitcher::aliasClassName)
        assertEquals(names.size, names.toSet().size, "two faces resolve to one alias")
    }

    @Test
    fun `exactly one alias ships enabled, and it is the default face`() {
        val enabled = aliases().filter { it.contains("android:enabled=\"true\"") }
        assertEquals(1, enabled.size, "a launcher must show one Vazie, not ${enabled.size}")
        val defaultName =
            AppIconSwitcher.aliasClassName(VazieLauncherFace.Default).removePrefix(PACKAGE)
        assertTrue(
            enabled.single().contains("android:name=\"$defaultName\""),
            "the enabled alias is not the default face",
        )
    }

    /** A pair outside the curated set has no alias, so it must never become a face. */
    @Test
    fun `a launcher face refuses a pair the mark does not offer`() {
        assertFailsWith<IllegalArgumentException> {
            VazieLauncherFace(VazieAppIcon.PEARL, VazieAppIconPlate.ROSE)
        }
    }

    @Test
    fun `every alias is the same activity, so switching icons is not a second app`() {
        aliases().forEach { alias ->
            assertTrue(
                alias.contains("android:targetActivity=\".MainActivity\""),
                "an alias points somewhere other than MainActivity:\n$alias",
            )
        }
    }

    @Test
    fun `every alias carries its own icon and the app's own name`() {
        aliases().forEach { alias ->
            assertTrue(alias.contains("android:icon=\"@mipmap/"), "an alias has no icon:\n$alias")
            assertTrue(
                alias.contains("android:label=\"@string/app_name\""),
                "an alias renamed the app:\n$alias",
            )
        }
        val icons = ICON.findAll(aliases().joinToString(separator = "\n")).map { it.groupValues[1] }
        assertEquals(
            AppIconSwitcher.faces.size,
            icons.toSet().size,
            "two aliases share a drawable, so two choices look identical",
        )
    }

    @Test
    fun `the launcher entry is the aliases, not the activity`() {
        val activity = ACTIVITY.find(manifest())?.value.orEmpty()
        assertTrue(activity.isNotEmpty(), "MainActivity is missing from the manifest")
        assertTrue(
            !activity.contains("android.intent.category.LAUNCHER"),
            "MainActivity still declares LAUNCHER, so it appears beside whichever alias is enabled",
        )
    }

    private fun manifest(): String {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue(file.exists(), "expected the manifest at ${file.absolutePath}")
        return file.readText()
    }

    private fun aliases(): List<String> = ALIAS.findAll(manifest()).map { it.value }.toList()

    private companion object {
        const val PACKAGE = "app.vazie.vpn"
        val ALIAS = Regex("""<activity-alias\b.*?</activity-alias>""", RegexOption.DOT_MATCHES_ALL)
        val ACTIVITY = Regex("""<activity\b.*?</activity>""", RegexOption.DOT_MATCHES_ALL)
        val ICON = Regex("""android:icon="@mipmap/([^"]+)"""")
    }
}
