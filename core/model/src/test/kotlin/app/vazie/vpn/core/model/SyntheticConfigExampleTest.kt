package app.vazie.vpn.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The example Vazie shows people has to be provably not a configuration. */
class SyntheticConfigExampleTest {

    @Test
    fun `the host is a documentation domain that resolves nowhere`() {
        assertTrue(
            SyntheticConfigExample.HOST.endsWith(".example.com"),
            "the example points at a host somebody may own: ${SyntheticConfigExample.HOST}",
        )
    }

    @Test
    fun `the id is a valid UUID shape and obviously nobody's`() {
        // Both halves matter. The shape has to be right or the example teaches a person to look for
        // the wrong thing; the value has to be unmistakably synthetic or it is somebody's.
        assertTrue(
            UUID_SHAPE.matches(SyntheticConfigExample.USER_ID),
            "the example id is not a UUID, so it teaches the wrong shape",
        )
        assertTrue(
            SyntheticConfigExample.USER_ID.startsWith(SYNTHETIC_PREFIX),
            "the example id is not recognisably synthetic: ${SyntheticConfigExample.USER_ID}",
        )
    }

    @Test
    fun `the link is built from the synthetic parts and nothing else`() {
        val link = SyntheticConfigExample.VLESS_LINK

        assertTrue(link.contains(SyntheticConfigExample.USER_ID))
        assertTrue(link.contains(SyntheticConfigExample.HOST))
        assertEquals(
            1,
            link.count { it == '@' },
            "a link with a second authority is not the shape this teaches",
        )
    }

    /** Nothing after the `?`, ever. */
    @Test
    fun `the query is elided rather than shown`() {
        val query = SyntheticConfigExample.VLESS_LINK.substringAfter('?', missingDelimiterValue = "")

        assertEquals("...", query, "the example started showing query parameters")
    }

    @Test
    fun `no key-shaped material appears anywhere in it`() {
        val link = SyntheticConfigExample.VLESS_LINK

        listOf("pbk=", "sid=", "PrivateKey", "PublicKey", "PresharedKey").forEach { marker ->
            assertFalse(
                link.contains(marker, ignoreCase = true),
                "the example carries credential material: $marker",
            )
        }
        // A long run of key-alphabet characters looks like a real key; the UUID is removed first.
        val withoutId = link.replace(SyntheticConfigExample.USER_ID, "")
        assertFalse(
            KEY_SHAPE.containsMatchIn(withoutId),
            "the example contains something shaped like a key",
        )
    }

    private companion object {
        const val SYNTHETIC_PREFIX = "00000000-0000-"
        val UUID_SHAPE =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** Base64/base64url runs of the length real key material has. */
        val KEY_SHAPE = Regex("[A-Za-z0-9_-]{24,}")
    }
}
