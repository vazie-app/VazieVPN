package app.vazie.vpn.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the debug HTTP inspector is allowed to keep. */
class VazieTrafficRedactorTest {

    private val accessResponse = """
        {
          "id": "$ACCESS_ID",
          "state": "ACTIVE",
          "createdAt": "2026-08-31T07:21:24Z",
          "server": {
            "id": "$SERVER_ID",
            "displayName": "Example",
            "regionId": "zz-test",
            "countryCode": "ZZ",
            "city": "Example",
            "status": "AVAILABLE",
            "protocols": ["VLESS"]
          },
          "profile": {
            "protocol": "VLESS",
            "endpoint": { "host": "203.0.113.10", "port": 2053 },
            "credential": "$CREDENTIAL",
            "flow": "xtls-rprx-vision",
            "security": {
              "kind": "REALITY",
              "serverName": "www.example.com",
              "fingerprint": "chrome",
              "publicKey": "$PUBLIC_KEY",
              "shortId": "$SHORT_ID"
            },
            "transport": { "kind": "TCP" }
          }
        }
    """.trimIndent()

    @Test
    fun `credential material never survives redaction`() {
        val out = VazieTrafficRedactor.redact(accessResponse, accessResponse.length)

        assertFalse(out.contains(CREDENTIAL), "the VLESS credential survived")
        assertFalse(out.contains(PUBLIC_KEY), "the REALITY public key survived")
        assertFalse(out.contains(SHORT_ID), "the REALITY short id survived")
    }

    @Test
    fun `the shape a REALITY handshake depends on stays visible`() {
        // The whole reason for running an inspector. A redactor that blanked the
        // body would be safe and useless: these are the fields a failing handshake is diagnosed from.
        val out = VazieTrafficRedactor.redact(accessResponse, accessResponse.length)

        listOf(
            "ACTIVE", "Example", "zz-test", "ZZ", "AVAILABLE",
            "203.0.113.10", "2053",
            "VLESS", "xtls-rprx-vision", "REALITY", "www.example.com", "chrome", "TCP",
        ).forEach { assertTrue(out.contains(it), "expected `$it` to remain visible") }
    }

    @Test
    fun `identifiers are not mistaken for secrets`() {
        // `shortId` is a REALITY secret and `accessId` is not. A substring rule cannot tell them
        // apart, which is why matching is on the whole key name.
        val out = VazieTrafficRedactor.redact(accessResponse, accessResponse.length)

        assertTrue(out.contains(ACCESS_ID), "the access id was redacted")
        assertTrue(out.contains(SERVER_ID), "the server id was redacted")
    }

    @Test
    fun `every sensitive key is replaced wherever it appears`() {
        val body = buildString {
            append("{")
            append(VazieTrafficRedactor.REDACTED_KEYS.joinToString(",") { """"$it":"$SENTINEL"""" })
            append("}")
        }

        val out = VazieTrafficRedactor.redact(body, body.length)

        assertFalse(out.contains(SENTINEL), "a sensitive value survived")
        val decoded = Json.parseToJsonElement(out).jsonObject
        VazieTrafficRedactor.REDACTED_KEYS.forEach { key ->
            assertEquals(
                VazieTrafficRedactor.REDACTED,
                decoded[key]?.jsonPrimitive?.content,
                "`$key` was not redacted",
            )
        }
    }

    @Test
    fun `email sign-in keeps its address, code and token out of the inspector`() {
        val verifyRequest =
            """{"email":"person@example.com","code":"493817","platform":"ANDROID","deviceName":"Pixel 8"}"""
        val verifyResponse = """{"accountId":"$ACCESS_ID","token":"$SENTINEL","email":"person@example.com",""" +
            """"expiresAt":"2026-10-25T00:00:00Z","accountCreated":false}"""

        val request = VazieTrafficRedactor.redact(verifyRequest, verifyRequest.length)
        val response = VazieTrafficRedactor.redact(verifyResponse, verifyResponse.length)

        listOf(request, response).forEach { out ->
            assertFalse(out.contains("person@example.com"), "the address survived")
            assertFalse(out.contains("493817"), "the one-time code survived")
            assertFalse(out.contains(SENTINEL), "the session token survived")
        }
        assertTrue(request.contains("ANDROID"), "the platform was redacted")
        assertTrue(response.contains("accountCreated"), "the shape of the response was lost")
    }

    @Test
    fun `key matching ignores case`() {
        val body = """{"Credential":"$SENTINEL","PUBLICKEY":"$SENTINEL","ShortId":"$SENTINEL"}"""

        assertFalse(VazieTrafficRedactor.redact(body, body.length).contains(SENTINEL))
    }

    @Test
    fun `nested objects and arrays are walked, not skipped`() {
        // `servers` is an array of objects already; an array of credentials is the shape a batch
        // endpoint would take.
        val body = """{"access":[{"profile":{"credential":"$SENTINEL"}},{"token":"$SENTINEL"}]}"""

        assertFalse(VazieTrafficRedactor.redact(body, body.length).contains(SENTINEL))
    }

    @Test
    fun `a body that will not parse is withheld rather than passed through`() {
        // Returning the original here is the one mistake that would make the whole arrangement
        // pointless: the inspector falls back to the raw bytes whenever the decoder declines.
        val html = "<html><body>502 Bad Gateway $SENTINEL</body></html>"

        val out = VazieTrafficRedactor.redact(html, html.length)

        assertFalse(out.contains(SENTINEL), "an unparseable body was passed through")
        assertTrue(out.contains("withheld"), "the placeholder does not say what happened")
        assertTrue(out.contains(html.length.toString()), "the size is worth keeping for diagnosis")
    }

    @Test
    fun `an empty body redacts to an empty string, not a placeholder`() {
        assertEquals("", VazieTrafficRedactor.redact("", 0))
        assertEquals("", VazieTrafficRedactor.redact("   ", 3))
    }

    @Test
    fun `the header deny-list covers what Vazie actually sends, and the standard names too`() {
        // `Authorization` is the only header Vazie sends that carries a secret - see
        // `VazieApiClient.prepare`. The rest are standard names that cost nothing to deny in advance.
        listOf(
            "Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie",
            "X-Api-Key", "X-Internal-Token",
        ).forEach {
            assertTrue(it in VazieTrafficRedactor.REDACTED_HEADERS, "`$it` is not redacted")
        }
    }

    @Test
    fun `the correlation header is deliberately not redacted`() {
        // It is the one thing that ties a phone's failure to a line in the backend log, and it is
        // not a secret. Redacting it would remove the reason to run the inspector.
        assertFalse(VazieApiClient.REQUEST_ID_HEADER in VazieTrafficRedactor.REDACTED_HEADERS)
    }

    private companion object {
        const val SENTINEL = "sentinel-value-that-must-not-survive"
        const val ACCESS_ID = "00000000-0000-4000-8000-0000000000a1"
        const val SERVER_ID = "00000000-0000-4000-8000-0000000000a2"
        const val CREDENTIAL = "00000000-0000-4000-8000-0000000000c1"
        const val PUBLIC_KEY = "synthetic-reality-public-key-not-a-real-one"
        const val SHORT_ID = "0123456789abcdef"
    }
}
