package app.vazie.vpn.config.subscription

import app.vazie.vpn.config.SyntheticLinks
import app.vazie.vpn.config.SyntheticLinks.HOST
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionLinkTest {

    @Test
    fun `an https address is a subscription link, named by its host`() {
        val link = assertNotNull(SubscriptionLink.parse("https://$HOST/sub/$TOKEN"))

        assertEquals(HOST, link.host)
        assertEquals("https://$HOST/sub/$TOKEN", link.url.expose())
    }

    @Test
    fun `the port, the query and the fragment are not part of the host`() {
        listOf("https://$HOST:8443/sub", "https://$HOST?token=$TOKEN", "https://$HOST#name", "https://$HOST").forEach {
            assertEquals(HOST, SubscriptionLink.parse(it)?.host, it)
        }
        assertEquals("[${SyntheticLinks.IPV6}]", SubscriptionLink.parse("https://[${SyntheticLinks.IPV6}]:8443/sub")?.host)
    }

    @Test
    fun `whitespace around the address and the case of the scheme are forgiven`() {
        assertEquals(HOST, SubscriptionLink.parse("\n  HTTPS://${HOST.uppercase()}/sub \n")?.host)
    }

    @Test
    fun `anything that is not one https address is not a subscription link`() {
        listOf(
            "",
            "just some text",
            SyntheticLinks.link(),
            "http://$HOST/sub",
            "https://",
            "https:///sub",
            "https://$HOST:port/sub",
            "https://$HOST/sub and more",
            "see https://$HOST/sub",
            "https://user@$HOST/sub",
        ).forEach { assertNull(SubscriptionLink.parse(it), it) }
    }

    @Test
    fun `an address without TLS is told apart, so the refusal can say why`() {
        assertTrue(SubscriptionLink.isInsecure(" HTTP://$HOST/sub"))
        assertFalse(SubscriptionLink.isInsecure("https://$HOST/sub"))
        assertFalse(SubscriptionLink.isInsecure(SyntheticLinks.link()))
    }

    @Test
    fun `the address is not what the link prints`() {
        assertFalse(TOKEN in SubscriptionLink.parse("https://$HOST/sub/$TOKEN")!!.url.toString())
    }

    private companion object {
        const val TOKEN = "not-a-real-subscription-token"
    }
}
