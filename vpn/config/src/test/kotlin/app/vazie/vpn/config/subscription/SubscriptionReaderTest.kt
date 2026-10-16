package app.vazie.vpn.config.subscription

import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.config.SyntheticLinks.CDN_HOST
import app.vazie.vpn.config.SyntheticLinks.HOST
import app.vazie.vpn.config.SyntheticLinks.USER_ID
import app.vazie.vpn.config.SyntheticLinks.link
import app.vazie.vpn.core.model.Secret
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionReaderTest {

    private val reader = SubscriptionReader(ConfigParserRegistry.default())

    private val first = link(fragment = "First")
    private val second = link(host = CDN_HOST, query = "security=tls&type=ws", fragment = "Second")

    @Test
    fun `links one per line are read in the order they came`() {
        val content = read("$first\r\n\n  $second  \n")

        assertEquals(listOf("First", "Second"), content.names())
        assertEquals(listOf(HOST, CDN_HOST), content.recognized.map { it.draft.endpoint.host })
        assertEquals(0, content.skipped)
        assertFalse(content.truncated)
    }

    @Test
    fun `a Base64 list is the same list`() {
        val plain = "$first\n$second\n".toByteArray()
        val encodings = listOf(
            Base64.getEncoder().encodeToString(plain),
            Base64.getEncoder().withoutPadding().encodeToString(plain),
            Base64.getUrlEncoder().encodeToString(plain),
            Base64.getMimeEncoder().encodeToString(plain) + "\n",
        )

        encodings.forEach { assertEquals(listOf("First", "Second"), read(it).names(), it) }
    }

    @Test
    fun `a link Vazie cannot run is left out and counted`() {
        val content = read(
            listOf(first, link(query = "type=kcp"), "vmess://payload", link(userId = "not-a-uuid"), second)
                .joinToString("\n"),
        )

        assertEquals(listOf("First", "Second"), content.names())
        assertEquals(3, content.skipped)
    }

    @Test
    fun `lines that are not links are not counted as configurations left out`() {
        val content = read("# my servers\n$first\nupload=0; download=0\n")

        assertEquals(listOf("First"), content.names())
        assertEquals(0, content.skipped)
    }

    @Test
    fun `an answer that is not a list holds nothing`() {
        listOf("", "   ", "<html><body>Not found</body></html>", "not base64 at all!").forEach {
            val content = read(it)
            assertTrue(content.recognized.isEmpty(), it)
            assertEquals(0, content.skipped, it)
        }
    }

    @Test
    fun `a list longer than the limit is cut at the limit and says so`() {
        val limit = SubscriptionReader.MAX_CONFIGURATIONS
        val exact = read(List(limit) { link(fragment = "n$it") }.joinToString("\n"))
        val longer = read(List(limit + 5) { link(fragment = "n$it") }.joinToString("\n"))

        assertEquals(limit, exact.recognized.size)
        assertFalse(exact.truncated)
        assertEquals(limit, longer.recognized.size)
        assertTrue(longer.truncated)
    }

    @Test
    fun `what was read keeps its credential`() {
        val outbound = (read(first).recognized.single().draft as ProfileDraft.Xray).outbound

        assertEquals(USER_ID, (outbound as XrayOutbound.Vless).userId.expose())
    }

    private fun read(body: String) = reader.read(Secret.of(body))

    private fun SubscriptionContent.names() = recognized.map { it.draft.suggestedName }
}
