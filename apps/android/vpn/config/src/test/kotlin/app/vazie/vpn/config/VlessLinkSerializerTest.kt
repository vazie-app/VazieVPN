package app.vazie.vpn.config

import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.config.vless.VlessLinkParser
import app.vazie.vpn.config.vless.VlessLinkSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The serializer is only correct if the parser agrees with it. */
class VlessLinkSerializerTest {

    private val parser = VlessLinkParser()

    @Test
    fun `a plain link survives being written and read again`() {
        assertRoundTrip(SyntheticLinks.link(query = "encryption=none&type=tcp&security=none"))
    }

    @Test
    fun `reality parameters survive`() {
        assertRoundTrip(
            SyntheticLinks.link(
                query = "encryption=none&type=tcp&security=reality" +
                    "&pbk=${SyntheticLinks.PUBLIC_KEY}&sid=${SyntheticLinks.SHORT_ID}" +
                    "&sni=${SyntheticLinks.HOST}&fp=chrome&flow=xtls-rprx-vision",
            ),
        )
    }

    @Test
    fun `websocket transport survives, path and all`() {
        assertRoundTrip(
            SyntheticLinks.link(
                host = SyntheticLinks.CDN_HOST,
                query = "encryption=none&type=ws&security=tls&sni=${SyntheticLinks.CDN_HOST}" +
                    "&path=%2Fvazie%2Fedge&host=${SyntheticLinks.CDN_HOST}",
            ),
        )
    }

    @Test
    fun `a parameter Vazie has never heard of comes back out`() {
        // Unknown parameters survive export, so an exported link matches the imported one.
        val outbound = outboundOf(
            SyntheticLinks.link(
                query = "encryption=none&type=tcp&security=none&packetEncoding=xudp&mux=8",
            ),
        )
        assertTrue("packetEncoding" in outbound.unknownParameters.names)

        val serialized = VlessLinkSerializer.serialize(outbound, name = null).expose()
        assertTrue(serialized.contains("packetEncoding=xudp"), serialized)
        assertTrue(serialized.contains("mux=8"), serialized)
    }

    @Test
    fun `an IPv6 host keeps its brackets`() {
        // Spelling as meaning: `2001:db8::1:443` has no unambiguous split into host and port, and a
        // parser reading it gets a different address or none.
        val link = SyntheticLinks.link(
            host = "[${SyntheticLinks.IPV6}]",
            query = "encryption=none&type=tcp&security=none",
        )
        val serialized = VlessLinkSerializer.serialize(outboundOf(link), name = null).expose()

        assertTrue(serialized.contains("[${SyntheticLinks.IPV6}]:${SyntheticLinks.PORT}"), serialized)
        assertRoundTrip(link)
    }

    @Test
    fun `a name with characters that mean something in a URL survives`() {
        // `#` starts the fragment, `&` separates parameters, `%` starts an escape. A name
        // containing them is a name a person typed, and it has to come back as typed.
        val awkward = "Дом #1 & «работа» 100%"
        val outbound = outboundOf(SyntheticLinks.link(query = "encryption=none&type=tcp&security=none"))
        val serialized = VlessLinkSerializer.serialize(outbound, name = awkward).expose()

        val reparsed = parser.parse(serialized)
        assertIs<ParseResult.Recognized>(reparsed)
        assertEquals(awkward, reparsed.draft.suggestedName)
    }

    @Test
    fun `serializing twice produces the same link`() {
        // Deterministic output, which is what makes a diff of two exports mean something and what
        // stops "export" being a different string every time it is tapped.
        val outbound = outboundOf(
            SyntheticLinks.link(
                query = "encryption=none&type=ws&security=tls&path=%2Fa&host=${SyntheticLinks.HOST}" +
                    "&sni=${SyntheticLinks.HOST}&zzz=1&aaa=2",
            ),
        )
        assertEquals(
            VlessLinkSerializer.serialize(outbound, name = "One").expose(),
            VlessLinkSerializer.serialize(outbound, name = "One").expose(),
        )
    }

    @Test
    fun `the result is a Secret, and says nothing when printed`() {
        // The type is the point. A share link is a credential, and the wrapper is what stops it
        // reaching a log through an interpolated string somebody added in a hurry.
        val outbound = outboundOf(SyntheticLinks.link(query = "encryption=none&type=tcp&security=none"))
        val secret = VlessLinkSerializer.serialize(outbound, name = "Home")

        assertTrue(SyntheticLinks.USER_ID !in secret.toString(), secret.toString())
        assertTrue(SyntheticLinks.HOST !in secret.toString(), secret.toString())
    }

    /** Parses, serializes, parses again, and asserts nothing changed. */
    private fun assertRoundTrip(link: String) {
        val original = outboundOf(link)
        val serialized = VlessLinkSerializer.serialize(original, name = null).expose()
        val reparsed = outboundOf(serialized)

        assertEquals(
            original.toString(),
            reparsed.toString(),
            "not equal after a round trip:\n$serialized",
        )
        assertCredentialsSurvive(original, reparsed, serialized)
    }

    /** The fields `toString()` redacts, compared by their contents. */
    private fun assertCredentialsSurvive(
        original: XrayOutbound.Vless,
        reparsed: XrayOutbound.Vless,
        serialized: String,
    ) {
        assertEquals(original.userId.expose(), reparsed.userId.expose(), "the UUID changed")

        val before = original.security
        val after = reparsed.security
        when (before) {
            is XraySecurity.Reality -> {
                assertIs<XraySecurity.Reality>(after, "REALITY became something else:\n$serialized")
                assertEquals(
                    before.publicKey.expose(),
                    after.publicKey.expose(),
                    "the REALITY public key changed",
                )
                assertEquals(
                    before.shortId?.expose(),
                    after.shortId?.expose(),
                    "the REALITY short id changed",
                )
            }

            // Neither holds a secret; `toString()` above already compared them in full.
            is XraySecurity.Tls, XraySecurity.None -> Unit
        }
    }

    private fun outboundOf(link: String): XrayOutbound.Vless {
        val parsed = parser.parse(link)
        assertIs<ParseResult.Recognized>(parsed, "could not parse: $link")
        val draft = parsed.draft
        assertIs<ProfileDraft.Xray>(draft)
        return draft.outbound as XrayOutbound.Vless
    }
}
