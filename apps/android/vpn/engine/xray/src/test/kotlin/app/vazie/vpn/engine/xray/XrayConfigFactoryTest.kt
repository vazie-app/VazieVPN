package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The translation from Vazie's normalized profile to the JSON Xray runs. */
class XrayConfigFactoryTest {

    @Test
    fun `the tun descriptor reaches Xray through the environment`() {
        val config = config(XrayTestProfiles.profile(), tunDescriptor = 42)

        assertEquals("42", config["env"]!!.jsonObject["xray.tun.fd"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the inbound is a tun, not a socks listener`() {
        val inbound = config(XrayTestProfiles.profile()).inbounds().single().jsonObject

        assertEquals("tun", inbound["protocol"]!!.jsonPrimitive.content)
        assertEquals(MTU, inbound["settings"]!!.jsonObject["mtu"]!!.jsonPrimitive.int)
        assertNull(inbound["port"], "a tun inbound has no port; a proxy listener would")
    }

    @Test
    fun `tcp becomes raw with its header type`() {
        val stream = config(
            XrayTestProfiles.profile(transport = XrayTransport.Tcp(headerType = "http")),
        ).stream()

        assertEquals("raw", stream["network"]!!.jsonPrimitive.content)
        assertEquals(
            "http",
            stream["rawSettings"]!!.jsonObject["header"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `tcp without a header type says none rather than omitting it`() {
        val stream = config(XrayTestProfiles.profile(transport = XrayTransport.Tcp())).stream()

        assertEquals(
            "none",
            stream["rawSettings"]!!.jsonObject["header"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `websocket carries its path and host`() {
        val stream = config(
            XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(),
                transport = XrayTransport.WebSocket(path = "/vazie", host = "cdn.example.net"),
            ),
        ).stream()

        assertEquals("ws", stream["network"]!!.jsonPrimitive.content)
        val ws = stream["wsSettings"]!!.jsonObject
        assertEquals("/vazie", ws["path"]!!.jsonPrimitive.content)
        assertEquals("cdn.example.net", ws["host"]!!.jsonPrimitive.content)
    }

    @Test
    fun `grpc carries its service name and mode`() {
        val stream = config(
            XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(),
                transport = XrayTransport.Grpc(serviceName = "vazie", multiMode = true),
            ),
        ).stream()

        assertEquals("grpc", stream["network"]!!.jsonPrimitive.content)
        val grpc = stream["grpcSettings"]!!.jsonObject
        assertEquals("vazie", grpc["serviceName"]!!.jsonPrimitive.content)
        assertEquals(true, grpc["multiMode"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `resolved dial address preserves TLS identity`() {
        val profile = XrayTestProfiles.profile(
            security = XrayTestProfiles.tls(serverName = null),
        )
        val config = config(profile, dialAddress = "203.0.113.17")

        assertEquals(
            "203.0.113.17",
            config.proxy()["settings"]!!.jsonObject["vnext"]!!.jsonArray.single()
                .jsonObject["address"]!!.jsonPrimitive.content,
        )
        assertEquals(
            XrayTestProfiles.HOST,
            config.stream()["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `resolved plaintext websocket and grpc preserve HTTP authority`() {
        val websocket = config(
            XrayTestProfiles.profile(
                security = XraySecurity.None,
                transport = XrayTransport.WebSocket(path = "/vpn"),
            ),
            dialAddress = "203.0.113.17",
        ).stream()["wsSettings"]!!.jsonObject
        val grpc = config(
            XrayTestProfiles.profile(
                security = XraySecurity.None,
                transport = XrayTransport.Grpc(serviceName = "vpn"),
            ),
            dialAddress = "203.0.113.17",
        ).stream()["grpcSettings"]!!.jsonObject

        assertEquals(XrayTestProfiles.HOST, websocket["host"]!!.jsonPrimitive.content)
        assertEquals(XrayTestProfiles.HOST, grpc["authority"]!!.jsonPrimitive.content)
    }

    @Test
    fun `security none writes no security block at all`() {
        val stream = config(XrayTestProfiles.profile(security = XraySecurity.None)).stream()

        assertEquals("none", stream["security"]!!.jsonPrimitive.content)
        assertNull(stream["tlsSettings"])
        assertNull(stream["realitySettings"])
    }

    @Test
    fun `tls carries server name, fingerprint and alpn`() {
        val stream = config(
            XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(alpn = listOf("h2", "http/1.1")),
            ),
        ).stream()

        assertEquals("tls", stream["security"]!!.jsonPrimitive.content)
        val tls = stream["tlsSettings"]!!.jsonObject
        assertEquals(XrayTestProfiles.HOST, tls["serverName"]!!.jsonPrimitive.content)
        assertEquals("hellochrome_120", tls["fingerprint"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("h2", "http/1.1"),
            tls["alpn"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `tls never writes allowInsecure, because the core no longer accepts it`() {
        // A profile asking for it is refused by `XraySupport` before this runs; the assertion is
        // that nothing writes the key even by accident, because Xray fails the whole config on it.
        val stream = config(
            XrayTestProfiles.profile(security = XrayTestProfiles.tls(allowInsecure = true)),
        ).stream()

        assertNull(stream["tlsSettings"]!!.jsonObject["allowInsecure"])
    }

    @Test
    fun `a browser fingerprint resolves to that browser's last pre-quantum build`() {
        // Xray's `chrome` sends a post-quantum ClientHello over 1700 bytes, which can hang REALITY silently.
        val resolved = mapOf(
            "chrome" to "hellochrome_120",
            "firefox" to "hellofirefox_120",
            "edge" to "helloedge_106",
        )

        resolved.forEach { (requested, expected) ->
            val stream = config(
                XrayTestProfiles.profile(
                    security = XrayTestProfiles.reality(fingerprint = requested),
                ),
            ).stream()

            assertEquals(
                expected,
                stream["realitySettings"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content,
                "fingerprint $requested",
            )
        }
    }

    @Test
    fun `a fingerprint with no post-quantum build of its own is passed through`() {
        listOf("safari", "ios", "android", "hellochrome_120").forEach { requested ->
            val stream = config(
                XrayTestProfiles.profile(
                    security = XrayTestProfiles.reality(fingerprint = requested),
                ),
            ).stream()

            assertEquals(
                requested,
                stream["realitySettings"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content,
                "fingerprint $requested",
            )
        }
    }

    @Test
    fun `reality without a stated fingerprint still names one`() {
        // Xray falls back to the newest Chrome when the key is absent, which is the build this
        // cannot use, so the pre-quantum choice is written out rather than left to the default.
        val stream = config(
            XrayTestProfiles.profile(security = XrayTestProfiles.reality(fingerprint = null)),
        ).stream()

        assertEquals(
            "hellochrome_120",
            stream["realitySettings"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `tls without a stated fingerprint stays without one`() {
        // Plain TLS reads an absent fingerprint as "do not imitate a browser at all", so inventing
        // one here would change what the profile asked for.
        val stream = config(
            XrayTestProfiles.profile(security = XrayTestProfiles.tls(fingerprint = null)),
        ).stream()

        assertNull(stream["tlsSettings"]!!.jsonObject["fingerprint"])
    }

    @Test
    fun `reality carries its key material and defaults spiderX`() {
        val stream = config(
            XrayTestProfiles.profile(security = XrayTestProfiles.reality()),
        ).stream()

        assertEquals("reality", stream["security"]!!.jsonPrimitive.content)
        val reality = stream["realitySettings"]!!.jsonObject
        assertEquals(XrayTestProfiles.PUBLIC_KEY, reality["publicKey"]!!.jsonPrimitive.content)
        assertEquals(XrayTestProfiles.SHORT_ID, reality["shortId"]!!.jsonPrimitive.content)
        assertEquals("/", reality["spiderX"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reality without a short id writes an empty one rather than omitting it`() {
        // Xray hex-decodes `shortId` into a fixed buffer; absent and empty are the same to it, and
        // writing the key explicitly keeps the config's shape the same for every REALITY profile.
        val stream = config(
            XrayTestProfiles.profile(security = XrayTestProfiles.reality(shortId = null)),
        ).stream()

        assertEquals("", stream["realitySettings"]!!.jsonObject["shortId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `vision travels on the user, not on the stream`() {
        val config = config(
            XrayTestProfiles.profile(
                security = XrayTestProfiles.reality(),
                flow = XrayFlow.XTLS_RPRX_VISION,
            ),
        )
        val user = config.proxy()["settings"]!!.jsonObject["vnext"]!!.jsonArray
            .single().jsonObject["users"]!!.jsonArray.single().jsonObject

        assertEquals("xtls-rprx-vision", user["flow"]!!.jsonPrimitive.content)
        assertEquals("none", user["encryption"]!!.jsonPrimitive.content)
        assertNull(config.stream()["flow"])
    }

    @Test
    fun `no flow means no flow key, not an empty one`() {
        val user = config(XrayTestProfiles.profile()).proxy()["settings"]!!.jsonObject["vnext"]!!
            .jsonArray.single().jsonObject["users"]!!.jsonArray.single().jsonObject

        assertNull(user["flow"])
    }

    @Test
    fun `dns goes through the tunnel and is asked for IPv4 only`() {
        val config = config(XrayTestProfiles.profile())
        val dns = config["dns"]!!.jsonObject

        assertEquals("UseIPv4", dns["queryStrategy"]!!.jsonPrimitive.content)
        assertTrue(
            dns["servers"]!!.jsonArray.all { it.jsonPrimitive.content.startsWith("https://") },
            "a plaintext resolver would leak every lookup to whoever carries it",
        )
        val rules = config["routing"]!!.jsonObject["rules"]!!.jsonArray
        assertEquals("53", rules.first().jsonObject["port"]!!.jsonPrimitive.content)
        assertEquals("dns-out", rules.first().jsonObject["outboundTag"]!!.jsonPrimitive.content)
    }

    @Test
    fun `IPv6 destinations are blackholed rather than proxied or ignored`() {
        val rules = config(XrayTestProfiles.profile())["routing"]!!.jsonObject["rules"]!!.jsonArray
        val ipv6 = rules.single { rule ->
            rule.jsonObject["ip"]?.jsonArray?.any { it.jsonPrimitive.content == "::/0" } == true
        }

        assertEquals("block", ipv6.jsonObject["outboundTag"]!!.jsonPrimitive.content)
    }

    @Test
    fun `everything else falls through to the proxy`() {
        val rules = config(XrayTestProfiles.profile())["routing"]!!.jsonObject["rules"]!!.jsonArray

        val last = rules.last().jsonObject
        assertEquals("proxy", last["outboundTag"]!!.jsonPrimitive.content)
        assertEquals("tcp,udp", last["network"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the engine is told to log nothing`() {
        val log = config(XrayTestProfiles.profile())["log"]!!.jsonObject

        assertEquals(
            "none",
            log["loglevel"]!!.jsonPrimitive.content,
            "engine log lines are built out of the configuration that produced them",
        )
    }

    @Test
    fun `the interface name is a constant and never the profile's name`() {
        val inbound = config(
            XrayTestProfiles.profile(name = "Alice's work relay"),
        ).inbounds().single().jsonObject

        assertEquals(
            "vazie-tun",
            inbound["settings"]!!.jsonObject["name"]!!.jsonPrimitive.contentOrNull,
        )
    }

    private fun config(
        profile: app.vazie.vpn.api.VpnProfile.Xray,
        tunDescriptor: Int = 7,
        dialAddress: String = profile.outbound.endpoint.host,
    ): JsonObject = Json.parseToJsonElement(
        XrayConfigFactory.create(
            profile = profile,
            mtu = MTU,
            tunDescriptor = tunDescriptor,
            dialAddress = dialAddress,
        )
    ).jsonObject

    private fun JsonObject.inbounds(): JsonArray = this["inbounds"]!!.jsonArray

    private fun JsonObject.proxy(): JsonObject = this["outbounds"]!!.jsonArray
        .map { it.jsonObject }
        .single { it["tag"]!!.jsonPrimitive.content == "proxy" }

    private fun JsonObject.stream(): JsonObject = proxy()["streamSettings"]!!.jsonObject

    private companion object {
        const val MTU = 1500
    }
}
