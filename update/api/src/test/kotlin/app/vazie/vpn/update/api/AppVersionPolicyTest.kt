package app.vazie.vpn.update.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** installed `<` minimum is required; `<` latest is optional; otherwise current. Integers only, at every boundary. */
class AppVersionPolicyTest {

    private val policy = policy(latest = 42, minimum = 39)

    @Test
    fun `below the minimum the update is required`() {
        assertIs<UpdateStatus.Required>(policy.statusFor(1))
        assertIs<UpdateStatus.Required>(policy.statusFor(38))
    }

    @Test
    fun `the minimum itself is still supported, and is only an optional update`() {
        assertIs<UpdateStatus.Optional>(policy.statusFor(39))
        assertIs<UpdateStatus.Optional>(policy.statusFor(40))
        assertIs<UpdateStatus.Optional>(policy.statusFor(41))
    }

    @Test
    fun `the latest itself, and anything newer, is up to date`() {
        assertEquals(UpdateStatus.UpToDate, policy.statusFor(42))
        assertEquals(UpdateStatus.UpToDate, policy.statusFor(43))
        assertEquals(UpdateStatus.UpToDate, policy.statusFor(Int.MAX_VALUE))
    }

    @Test
    fun `minimum equal to latest makes every older build update`() {
        val strict = policy(latest = 42, minimum = 42)
        assertIs<UpdateStatus.Required>(strict.statusFor(41))
        assertEquals(UpdateStatus.UpToDate, strict.statusFor(42))
    }

    @Test
    fun `version codes are compared as integers, not as strings`() {
        // "9" > "10" as text; 9 < 10 as versions.
        val p = policy(latest = 10, minimum = 10)
        assertIs<UpdateStatus.Required>(p.statusFor(9))
        assertEquals(UpdateStatus.UpToDate, p.statusFor(10))
        // The name is for people: it never decides anything.
        assertEquals(UpdateStatus.UpToDate, policy(latest = 5, minimum = 5, name = "0.0.1").statusFor(5))
    }

    @Test
    fun `the status carries the policy it came from`() {
        assertEquals(policy, (policy.statusFor(1) as UpdateStatus.Required).policy)
        assertEquals(policy, (policy.statusFor(40) as UpdateStatus.Optional).policy)
    }

    @Test
    fun `an invalid policy cannot be built and is refused by create`() {
        assertNull(create(latest = 0))
        assertNull(create(minimum = 0))
        assertNull(create(latest = -1))
        assertNull(create(latest = 10, minimum = 11), "minimum above latest")
        assertNull(create(latest = null))
        assertNull(create(minimum = null))
        assertNull(create(name = null))
        assertNull(create(name = "  "))
        assertNull(create(name = "x".repeat(33)))
        assertNull(create(url = null))
        assertFailsWith<IllegalArgumentException> { AppVersionPolicy(0, "1", 0, URL) }
        assertFailsWith<IllegalArgumentException> { AppVersionPolicy(5, "1", 6, URL) }
    }

    @Test
    fun `only a plain https page is an update address`() {
        val unsafe = listOf(
            "http://vazie.app/vpn",
            "intent://update#Intent;scheme=market;package=app.vazie.vpn;end",
            "market://details?id=app.vazie.vpn",
            "vazie-vpn://settings",
            "javascript:alert(1)",
            "file:///sdcard/update.apk",
            "content://app/update",
            "https://",
            "https:///vpn",
            "https://user:pass@vazie.app/vpn",
            "https://vazie.app/vpn update",
            "https://vazie.app/\nvpn",
            "vazie.app/vpn",
            "//vazie.app/vpn",
            "",
            "https://" + "a".repeat(600) + ".example",
        )
        for (url in unsafe) {
            assertFalse(UpdateUrl.isSafe(url), "accepted $url")
            assertNull(create(url = url), "built a policy on $url")
        }
        assertTrue(UpdateUrl.isSafe("https://vazie.app/vpn"))
        assertTrue(UpdateUrl.isSafe("https://vazie.app:8443/vpn?from=app#top"))
    }

    @Test
    fun `wording is kept by language, and anything that is not short plain text is dropped`() {
        val p = create(
            messages = mapOf(
                "RU" to "Обновите Vazie VPN",
                "en" to "Update",
                "xx-invalid-key" to "ignored",
                "de" to "",
                "fr" to "x".repeat(301),
                "es" to "line\nbreak",
            ),
        )!!

        assertEquals(mapOf("ru" to "Обновите Vazie VPN", "en" to "Update"), p.messages)
        assertEquals("Обновите Vazie VPN", p.message("RU"))
        assertNull(p.message("fr"))
    }

    private fun policy(latest: Int, minimum: Int, name: String = "1.4.0") =
        AppVersionPolicy(latest, name, minimum, URL)

    private fun create(
        latest: Int? = 42,
        name: String? = "1.4.0",
        minimum: Int? = 39,
        url: String? = URL,
        messages: Map<String, String>? = null,
    ) = AppVersionPolicy.create(latest, name, minimum, url, messages)

    private companion object {
        const val URL = "https://vazie.app/vpn"
    }
}
