package app.vazie.vpn.system

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Quick Settings tile and the ongoing notification carry the same monochrome Vazie mark. */
class SystemSurfaceMarkTest {

    @Test
    fun `the Quick Settings tile carries the app's monochrome icon and not an action glyph`() {
        val tile = TILE.find(manifest())?.value
        assertTrue(tile != null, "the tile service is missing from the manifest")
        assertTrue(
            tile.contains("android:icon=\"@drawable/ic_tile_vazie\""),
            "the tile does not carry the app's monochrome icon:\n$tile",
        )
        assertTrue(
            !tile.contains("ic_shortcut"),
            "the tile is back on a shortcut glyph, which is an action and not an identity",
        )
    }

    @Test
    fun `the ongoing VPN notification carries the monochrome mark`() {
        val notifications = File(VPN_RUNTIME, "kotlin/app/vazie/vpn/runtime/VpnNotifications.kt")
        assertTrue(notifications.exists(), "expected ${notifications.absolutePath}")
        assertTrue(
            notifications.readText().contains("setSmallIcon(R.drawable.ic_vazie_status)"),
            "the ongoing notification is not using the monochrome mark",
        )
    }

    /** One artwork at every density: the notification's copy is the tile's, byte for byte. */
    @Test
    fun `the notification icon is the tile icon at every density`() {
        DENSITIES.forEach { density ->
            val tile = File("src/main/res/drawable-$density/ic_tile_vazie.webp")
            val status = File(VPN_RUNTIME, "res/drawable-$density/ic_vazie_status.webp")
            assertTrue(tile.exists() && status.exists(), "missing $density: $tile or $status")
            assertTrue(tile.readBytes().contentEquals(status.readBytes()), "the $density copies have drifted apart")
        }
    }

    /** The old simplified shield with a cut-out V never comes back to a system surface. */
    @Test
    fun `the simplified status mark is gone`() {
        assertTrue(!File(VPN_RUNTIME, "res/drawable/ic_vazie_mark_status.xml").exists())
    }

    private fun manifest(): String = File("src/main/AndroidManifest.xml").readText()

    private companion object {
        const val VPN_RUNTIME = "../vpn/runtime/src/main"
        val DENSITIES = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val TILE = Regex("""<service\b[^>]*VazieTileService.*?</service>""", RegexOption.DOT_MATCHES_ALL)
    }
}
