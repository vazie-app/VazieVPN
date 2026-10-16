package app.vazie.vpn.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Makes the privacy contract executable rather than aspirational. */
class SecretTest {

    private val privateKey = "cGxhaW50ZXh0LXByaXZhdGUta2V5LXNhbXBsZQ=="

    @Test
    fun `toString does not reveal the value`() {
        val secret = Secret.of(privateKey)

        assertEquals(Secret.REDACTED, secret.toString())
        assertFalse(secret.toString().contains(privateKey))
    }

    @Test
    fun `string interpolation does not reveal the value`() {
        val interpolated = "key=${Secret.of(privateKey)}"

        assertEquals("key=${Secret.REDACTED}", interpolated)
        assertFalse(interpolated.contains(privateKey))
    }

    @Test
    fun `data class toString does not reveal a nested secret`() {
        data class Profile(val name: String, val privateKey: Secret<String>)

        val rendered = Profile(name = "Office tunnel", privateKey = Secret.of(privateKey)).toString()

        assertTrue(rendered.contains("Office tunnel"), "non-secret fields should still be visible")
        assertFalse(rendered.contains(privateKey), "secret leaked through data class toString")
    }

    @Test
    fun `exception message does not reveal the value`() {
        val secret = Secret.of(privateKey)

        val message = runCatching { error("could not parse $secret") }.exceptionOrNull()?.message

        assertEquals("could not parse ${Secret.REDACTED}", message)
        assertFalse(message.orEmpty().contains(privateKey))
    }

    @Test
    fun `collection rendering does not reveal the value`() {
        val rendered = listOf(Secret.of(privateKey), Secret.of("uuid-1234")).toString()

        assertFalse(rendered.contains(privateKey))
        assertFalse(rendered.contains("uuid-1234"))
    }

    @Test
    fun `expose returns the original value`() {
        assertEquals(privateKey, Secret.of(privateKey).expose())
    }

    @Test
    fun `exposed plaintext is an ordinary value with no further protection`() {
        // Documents the limit of the abstraction: after expose() the caller holds plaintext, and
        // Secret can no longer help. The KDoc says so; this pins it so nobody assumes otherwise.
        val exposed = Secret.of(privateKey).expose()

        assertTrue(exposed.contains(privateKey))
        assertTrue("leaked=$exposed".contains(privateKey))
    }

    @Test
    fun `secret is not an inline value class so it cannot erase to its payload`() {
        // A value class would erase to String at runtime, letting a Secret<String> reach a String
        // parameter - and a logger - through generic code.
        val boxed: Any = Secret.of(privateKey)

        assertFalse(boxed is String, "Secret must not erase to its underlying type")
        assertEquals("Secret", boxed.javaClass.simpleName)
    }

    @Test
    fun `secret uses identity equality so no constant-time comparison is implied`() {
        // Value-based equals over secret material is a non-constant-time comparison and an API we
        // have no use for. Compare plaintext at the boundary that already handles it.
        val a = Secret.of(privateKey)
        val b = Secret.of(privateKey)

        assertEquals(a, a)
        assertNotEquals(a, b)
    }
}

class ProfileIdTest {

    @Test
    fun `rejects blank ids`() {
        assertTrue(runCatching { ProfileId("") }.isFailure)
        assertTrue(runCatching { ProfileId("  ") }.isFailure)
    }

    @Test
    fun `renders its raw value`() {
        assertEquals("abc-123", ProfileId("abc-123").toString())
    }
}
