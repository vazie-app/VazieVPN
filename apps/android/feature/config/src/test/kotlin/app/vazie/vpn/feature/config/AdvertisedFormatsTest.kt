package app.vazie.vpn.feature.config

import app.vazie.vpn.config.ConfigParserRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The screens may name a format only if a parser reads it. */
class AdvertisedFormatsTest {

    @Test
    fun `the advertised phrase is exactly what the registry holds`() {
        val registry = ConfigParserRegistry.default()

        assertEquals(
            registry.parserIds().map { it.uppercase() },
            registry.advertisedFormats().split(" · "),
            "the sentence on screen stopped matching the parsers behind it",
        )
    }

    @Test
    fun `today that is VLESS and nothing else`() {
        // Written out, so claiming a new format is a deliberate change to this test.
        assertEquals(listOf("VLESS"), ConfigParserRegistry.default().parserIds().map { it.uppercase() })
    }

    @Test
    fun `no screen advertises a protocol nothing reads`() {
        val advertised = ConfigParserRegistry.default().parserIds().map { it.lowercase() }

        listOf("wireguard", "vmess", "trojan", "shadowsocks", "openvpn").forEach { absent ->
            assertFalse(
                absent in advertised,
                "$absent is advertised but no parser reads it",
            )
        }
    }

    /** User-facing strings must not name a format either, except a string marked `_planned`, which says
     * what is coming rather than what works. */
    @Test
    fun `the strings name no protocol the parsers do not read`() {
        val planned = Regex("""<string name="[a-z_]+_planned">.*?</string>""", RegexOption.DOT_MATCHES_ALL)
        val strings = java.io.File("src/main/res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .map { it.path to it.readText().replace(planned, "") }
            .toList()

        assertTrue(strings.isNotEmpty(), "no string resources were scanned")
        strings.forEach { (path, text) ->
            listOf("WireGuard", "VMess", "Trojan", "Shadowsocks", "OpenVPN").forEach { absent ->
                assertFalse(
                    text.contains(absent, ignoreCase = true),
                    "$path advertises $absent, which no parser reads",
                )
            }
        }
    }
}
