package app.vazie.vpn.config

import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.config.SyntheticLinks.HOST
import app.vazie.vpn.config.SyntheticLinks.link
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConfigParserRegistryTest {

    private val registry = ConfigParserRegistry.default()

    @Test
    fun `a VLESS link reaches the VLESS parser`() {
        val result = registry.parse(source(link()))

        assertEquals(ProtocolDescriptor.VLESS, assertIs<ParseResult.Recognized>(result).descriptor)
    }

    @Test
    fun `surrounding whitespace is not the user's mistake to fix`() {
        val result = registry.parse(source("\n  " + link() + "  \n"))

        assertIs<ParseResult.Recognized>(result)
    }

    @Test
    fun `nothing to read is its own answer`() {
        assertEquals(InvalidReason.Empty, invalid(source("   \n ")))
    }

    @Test
    fun `another protocol's link is named by its scheme`() {
        assertEquals(InvalidReason.UnknownScheme("vmess"), invalid(source("vmess://payload")))
        assertEquals(InvalidReason.UnknownScheme("https"), invalid(source("https://$HOST/config")))
    }

    @Test
    fun `text that is not a URI has no scheme to name`() {
        assertEquals(InvalidReason.UnknownScheme(null), invalid(source("just some text")))
    }

    @Test
    fun `the scheme is reported lowercased and nothing else of the input is`() {
        assertEquals(InvalidReason.UnknownScheme("https"), invalid(source("HTTPS://$HOST/config")))
    }

    private fun source(raw: String) = ConfigSource.PlainText(Secret.of(raw))

    private fun invalid(source: ConfigSource): InvalidReason =
        assertIs<ParseResult.Invalid>(registry.parse(source)).reason
}
