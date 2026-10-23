package app.vazie.vpn.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VazieLinksTest {

    @Test
    fun `only an https page with a host or one mailto address opens`() {
        for (url in listOf(
            "https://t.me/vazieapp",
            "https://www.behance.net/gallery/256711563/Vazie-VPN-Native-Android-VPN-Client",
            "https://vazie.app/?a=1#b",
            "mailto:support@vazie.app",
        )) {
            assertTrue(VazieLinks.isOpenable(url), url)
        }
        for (url in listOf(
            "http://vazie.app",
            "https://",
            "https://user@vazie.app",
            "https://vazie.app/a b",
            "javascript:alert(1)",
            "intent://scan#Intent;end",
            "tg://resolve?domain=vazieapp",
            "vazie-vpn://settings",
            "mailto:nobody",
            "mailto:a@b.c?subject=x",
            "https://vazie.app/" + "a".repeat(600),
        )) {
            assertFalse(VazieLinks.isOpenable(url), url)
        }
    }

    @Test
    fun `a text is Russian on a Russian device and English everywhere else`() {
        val text = VazieLocalizedText(ru = "Канал", en = "Channel")
        assertEquals("Канал", text.resolve("ru"))
        assertEquals("Channel", text.resolve("en"))
        assertEquals("Channel", text.resolve("uk"))
    }
}
