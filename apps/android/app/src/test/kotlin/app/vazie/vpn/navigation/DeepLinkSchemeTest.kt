package app.vazie.vpn.navigation

import app.vazie.vpn.feature.config.ADD_CONFIG_DEEP_LINK
import app.vazie.vpn.feature.connections.CONNECTIONS_DEEP_LINK
import app.vazie.vpn.feature.home.HOME_CONNECT_DEEP_LINK
import app.vazie.vpn.feature.home.HOME_DEEP_LINK
import app.vazie.vpn.feature.settings.SETTINGS_DEEP_LINK
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What this application will and will not be launched by. */
class DeepLinkSchemeTest {

    @Test
    fun `the manifest registers one product-scoped scheme and nothing else`() {
        assertEquals(setOf("vazie-vpn"), manifestSchemes())
    }

    @Test
    fun `the shared family scheme is not registered here`() {
        assertTrue(
            "vazie" !in manifestSchemes(),
            "`vazie://` is the family's, not this product's: app.vazie.letter and app.vazie.relay " +
                "would collide with it, and a link only one app can open would open a chooser",
        )
    }

    @Test
    fun `no credential-carrying scheme is registered`() {
        val schemes = manifestSchemes()
        listOf("vless", "vmess", "trojan", "ss", "shadowsocks", "wireguard", "wg", "hysteria", "tuic")
            .forEach { protocol ->
                assertTrue(
                    protocol !in schemes,
                    "`$protocol://` is registered: a configuration arriving as a URL passes through " +
                        "the OS, the launcher and recents before Vazie sees it",
                )
            }
    }

    @Test
    fun `every declared deep link is registered, scheme and host`() {
        val filters = manifestFilters()
        listOf(
            HOME_DEEP_LINK,
            HOME_CONNECT_DEEP_LINK,
            CONNECTIONS_DEEP_LINK,
            SETTINGS_DEEP_LINK,
            ADD_CONFIG_DEEP_LINK,
        ).forEach { deepLink ->
            val scheme = deepLink.substringBefore("://")
            val host = deepLink.substringAfter("://").substringBefore("?").substringBefore("/")
            assertTrue(
                scheme to host in filters,
                "$deepLink has no matching <data android:scheme android:host> filter; declared: $filters",
            )
        }
    }

    @Test
    fun `the manifest filters no host the app cannot render`() {
        assertEquals(
            setOf("home", "connections", "settings", "add-config"),
            manifestFilters().map { it.second }.toSet(),
            "a host is filtered that no route declares, so the app would be launched for a " +
                "destination it has to guess at",
        )
    }

    private fun manifestSchemes(): Set<String> = manifestFilters().map { it.first }.toSet()

    private fun manifestFilters(): Set<Pair<String, String>> {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(manifest.exists(), "expected the manifest at ${manifest.absolutePath}")
        return DATA.findAll(manifest.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toSet()
    }

    private companion object {
        /** `<data android:scheme="…" android:host="…" />`, in that order, which is how they are written. */
        val DATA = Regex("""android:scheme="([^"]+)"\s+android:host="([^"]+)"""")
    }
}
