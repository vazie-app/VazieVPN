package app.vazie.vpn.config

import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XraySecurityKind
import app.vazie.vpn.api.XrayTransport
import app.vazie.vpn.api.XrayTransportKind
import app.vazie.vpn.config.SyntheticLinks.CDN_HOST
import app.vazie.vpn.config.SyntheticLinks.HOST
import app.vazie.vpn.config.SyntheticLinks.IPV4
import app.vazie.vpn.config.SyntheticLinks.IPV6
import app.vazie.vpn.config.SyntheticLinks.PORT
import app.vazie.vpn.config.SyntheticLinks.PUBLIC_KEY
import app.vazie.vpn.config.SyntheticLinks.SHORT_ID
import app.vazie.vpn.config.SyntheticLinks.USER_ID
import app.vazie.vpn.config.SyntheticLinks.link
import app.vazie.vpn.config.vless.VlessLinkParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parser's table. */
class VlessLinkParserTest {

    private val parser = VlessLinkParser()

    // region valid

    @Test
    fun `a bare link is TCP without security`() {
        val outbound = recognized(link())

        assertEquals(HOST, outbound.endpoint.host)
        assertEquals(PORT, outbound.endpoint.port)
        assertEquals(USER_ID, outbound.userId.expose())
        assertEquals(XraySecurityKind.NONE, outbound.security.kind)
        assertEquals(XrayTransportKind.TCP, outbound.transport.kind)
        assertNull(outbound.flow)
        assertTrue(outbound.unknownParameters.isEmpty)
    }

    @Test
    fun `encryption none is the only encryption VLESS has`() {
        val outbound = recognized(link(query = "encryption=none"))

        assertTrue(outbound.unknownParameters.isEmpty, "encryption is understood, not preserved")
    }

    @Test
    fun `TLS over TCP reads its server name and fingerprint`() {
        val outbound = recognized(link(query = "security=tls&sni=$HOST&fp=chrome&alpn=h2%2Chttp%2F1.1"))

        val security = assertIs<XraySecurity.Tls>(outbound.security)
        assertEquals(HOST, security.serverName)
        assertEquals("chrome", security.fingerprint)
        assertEquals(listOf("h2", "http/1.1"), security.alpn)
        assertEquals(false, security.allowInsecure)
    }

    @Test
    fun `REALITY over TCP with Vision reads every REALITY parameter`() {
        val outbound = recognized(
            link(
                query = "security=reality&type=tcp&flow=xtls-rprx-vision" +
                    "&sni=$HOST&fp=chrome&pbk=$PUBLIC_KEY&sid=$SHORT_ID&spx=%2F",
            ),
        )

        val security = assertIs<XraySecurity.Reality>(outbound.security)
        assertEquals(HOST, security.serverName)
        assertEquals("chrome", security.fingerprint)
        assertEquals(PUBLIC_KEY, security.publicKey.expose())
        assertEquals(SHORT_ID, security.shortId?.expose())
        assertEquals("/", security.spiderX)
        assertEquals(XrayFlow.XTLS_RPRX_VISION, outbound.flow)
    }

    @Test
    fun `WebSocket reads its path and host header`() {
        val outbound = recognized(
            link(query = "security=tls&type=ws&path=%2Fvazie&host=$CDN_HOST&sni=$HOST"),
        )

        val transport = assertIs<XrayTransport.WebSocket>(outbound.transport)
        assertEquals("/vazie", transport.path)
        assertEquals(CDN_HOST, transport.host)
    }

    @Test
    fun `WebSocket without a path gets the default one`() {
        val outbound = recognized(link(query = "type=ws"))

        assertEquals("/", assertIs<XrayTransport.WebSocket>(outbound.transport).path)
    }

    @Test
    fun `the longer spelling of the WebSocket transport is accepted too`() {
        val outbound = recognized(link(query = "type=websocket"))

        assertEquals(XrayTransportKind.WEBSOCKET, outbound.transport.kind)
    }

    @Test
    fun `gRPC reads its service name and mode`() {
        val outbound = recognized(link(query = "security=tls&type=grpc&serviceName=vazie&mode=multi"))

        val transport = assertIs<XrayTransport.Grpc>(outbound.transport)
        assertEquals("vazie", transport.serviceName)
        assertTrue(transport.multiMode)
    }

    @Test
    fun `an IPv4 literal is a host`() {
        assertEquals(IPV4, recognized(link(host = IPV4)).endpoint.host)
    }

    @Test
    fun `an IPv6 literal in brackets is a host`() {
        assertEquals(IPV6, recognized(link(host = "[$IPV6]")).endpoint.host)
    }

    @Test
    fun `the fragment becomes the suggested name, percent-decoded`() {
        assertEquals("Home relay", draft(link(fragment = "Home%20relay")).suggestedName)
    }

    @Test
    fun `a fragment with an unencoded percent sign survives as it was typed`() {
        assertEquals("100% relay", draft(link(fragment = "100% relay")).suggestedName)
    }

    @Test
    fun `no fragment means no suggested name`() {
        assertNull(draft(link()).suggestedName)
    }

    @Test
    fun `older parameter spellings are read as the fields they always meant`() {
        val outbound = recognized(
            link(query = "security=reality&peer=$HOST&fingerprint=firefox&publicKey=$PUBLIC_KEY&shortId=$SHORT_ID"),
        )

        val security = assertIs<XraySecurity.Reality>(outbound.security)
        assertEquals(HOST, security.serverName)
        assertEquals("firefox", security.fingerprint)
        assertEquals(PUBLIC_KEY, security.publicKey.expose())
        assertEquals(SHORT_ID, security.shortId?.expose())
    }

    // endregion

    // region preservation

    @Test
    fun `a parameter Vazie does not understand is kept, name and value`() {
        val outbound = recognized(link(query = "security=tls&packetEncoding=xudp&fragment=1%2C40-60"))

        assertEquals(listOf("packetEncoding", "fragment"), outbound.unknownParameters.names)
        assertEquals(
            listOf("xudp", "1,40-60"),
            outbound.unknownParameters.entries.map { it.value.expose() },
        )
    }

    @Test
    fun `a parameter belonging to another transport is kept rather than misread`() {
        val outbound = recognized(link(query = "type=tcp&path=%2Fnot-a-tcp-thing"))

        assertEquals(listOf("path"), outbound.unknownParameters.names)
    }

    @Test
    fun `a repeated parameter keeps its repeats`() {
        val outbound = recognized(link(query = "security=tls&sni=$HOST&sni=$CDN_HOST"))

        assertEquals(HOST, assertIs<XraySecurity.Tls>(outbound.security).serverName)
        assertEquals(listOf("sni"), outbound.unknownParameters.names)
        assertEquals(CDN_HOST, outbound.unknownParameters.entries.single().value.expose())
    }

    @Test
    fun `a parameter without a value is still a parameter`() {
        val outbound = recognized(link(query = "debug"))

        assertEquals(listOf("debug"), outbound.unknownParameters.names)
    }

    // endregion

    // region unsupported

    @Test
    fun `a transport Vazie has no support for is named, not rejected`() {
        assertEquals(UnsupportedFeature.Transport("kcp"), unsupported(link(query = "type=kcp")))
        assertEquals(UnsupportedFeature.Transport("xhttp"), unsupported(link(query = "type=xhttp")))
    }

    @Test
    fun `a security mode Vazie has no support for is named`() {
        assertEquals(
            UnsupportedFeature.Security("xtls"),
            unsupported(link(query = "security=xtls&flow=xtls-rprx-direct")),
        )
    }

    @Test
    fun `a flow Vazie has no support for is named`() {
        assertEquals(
            UnsupportedFeature.Flow("xtls-rprx-vision-udp443"),
            unsupported(link(query = "security=tls&flow=xtls-rprx-vision-udp443")),
        )
    }

    @Test
    fun `VLESS encryption other than none is recognised and unsupported`() {
        assertEquals(
            UnsupportedFeature.Encryption("mlkem768x25519plus"),
            unsupported(link(query = "encryption=mlkem768x25519plus")),
        )
    }

    @Test
    fun `an unsupported link still says which protocol it is`() {
        val result = assertIs<ParseResult.Unsupported>(parser.parse(link(query = "type=quic")))

        assertEquals(ProtocolDescriptor.VLESS, result.descriptor)
    }

    // endregion

    // region invalid

    @Test
    fun `another scheme is not this parser's business`() {
        assertEquals(false, parser.accepts("https://" + HOST))
    }

    @Test
    fun `a user id that is not a UUID is rejected`() {
        assertEquals(InvalidReason.MalformedUserId, invalid(link(userId = "not-a-uuid")))
    }

    @Test
    fun `a link with no user id is rejected`() {
        assertEquals(InvalidReason.MissingUserId, invalid(SyntheticLinks.SCHEME + HOST + ":443"))
    }

    @Test
    fun `a link with no host is rejected`() {
        assertEquals(InvalidReason.MissingHost, invalid(link(host = "")))
    }

    @Test
    fun `a host that is not a name or an address is rejected`() {
        assertEquals(InvalidReason.MalformedHost, invalid(link(host = "relay example net")))
    }

    @Test
    fun `a link with no port is rejected`() {
        assertEquals(InvalidReason.MissingPort, invalid(link(port = "")))
    }

    @Test
    fun `a port outside the range is rejected`() {
        assertEquals(InvalidReason.InvalidPort, invalid(link(port = "99999")))
        assertEquals(InvalidReason.InvalidPort, invalid(link(port = "https")))
    }

    @Test
    fun `an unclosed IPv6 bracket is a malformed URI`() {
        assertEquals(InvalidReason.MalformedUri, invalid(link(host = "[$IPV6", port = "443")))
    }

    @Test
    fun `a broken percent escape in the query is rejected`() {
        assertEquals(InvalidReason.MalformedEncoding, invalid(link(query = "security=tls&sni=%zz")))
    }

    @Test
    fun `a broken percent escape in the user id is rejected`() {
        assertEquals(InvalidReason.MalformedEncoding, invalid(link(userId = "%zz")))
    }

    @Test
    fun `REALITY without a public key names the parameter it needs`() {
        assertEquals(
            InvalidReason.MissingParameter("pbk"),
            invalid(link(query = "security=reality&sni=$HOST")),
        )
    }

    @Test
    fun `REALITY over WebSocket is a combination no server can serve`() {
        assertEquals(
            InvalidReason.ConflictingParameters(listOf("security", "type")),
            invalid(link(query = "security=reality&type=ws&pbk=$PUBLIC_KEY")),
        )
    }

    @Test
    fun `Vision without TLS is a combination that cannot work`() {
        assertEquals(
            InvalidReason.ConflictingParameters(listOf("flow", "security")),
            invalid(link(query = "flow=xtls-rprx-vision")),
        )
    }

    @Test
    fun `Vision over a transport other than TCP is a combination that cannot work`() {
        assertEquals(
            InvalidReason.ConflictingParameters(listOf("flow", "type")),
            invalid(link(query = "security=tls&type=ws&flow=xtls-rprx-vision")),
        )
    }

    // endregion

    // region privacy

    @Test
    fun `no failure carries any part of the link it read`() {
        val inputs = listOf(
            link(userId = "not-a-uuid"),
            link(host = ""),
            link(port = "99999"),
            link(query = "security=reality"),
            link(query = "security=tls&sni=%zz"),
            link(query = "type=kcp", fragment = "Home relay"),
        )

        inputs.forEach { input ->
            val rendered = parser.parse(input).toString()
            listOf(USER_ID, HOST, SyntheticLinks.SCHEME, "Home relay").forEach { fragment ->
                assertTrue(
                    fragment !in rendered,
                    "a parse failure rendered as \"$rendered\", which contains input",
                )
            }
        }
    }

    @Test
    fun `a recognised outbound hides its secrets when it is printed`() {
        val outbound = recognized(link(query = "security=reality&pbk=$PUBLIC_KEY&sid=$SHORT_ID"))

        val rendered = outbound.toString()
        assertTrue(USER_ID !in rendered, "the user id was printed")
        assertTrue(PUBLIC_KEY !in rendered, "the REALITY public key was printed")
        assertTrue(SHORT_ID !in rendered, "the REALITY short id was printed")
    }

    // endregion

    private fun draft(raw: String): ProfileDraft.Xray =
        assertIs<ProfileDraft.Xray>(assertIs<ParseResult.Recognized>(parser.parse(raw)).draft)

    private fun recognized(raw: String): XrayOutbound.Vless =
        assertIs<XrayOutbound.Vless>(draft(raw).outbound)

    private fun unsupported(raw: String): UnsupportedFeature =
        assertIs<ParseResult.Unsupported>(parser.parse(raw)).feature

    private fun invalid(raw: String): InvalidReason =
        assertIs<ParseResult.Invalid>(parser.parse(raw)).reason
}
