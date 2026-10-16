package app.vazie.vpn.core.crypto

import kotlin.test.Test
import kotlin.test.assertFails
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What can be proved about the Keystore cipher without a Keystore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeystoreCipherTest {

    @Test
    fun `bytes that are not one of our records are refused before a key is touched`() {
        val cipher = KeystoreCipher(alias = "vazie.test.profiles")

        assertFails { cipher.decrypt(byteArrayOf()) }
        assertFails { cipher.decrypt(byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9)) }
        assertFails { cipher.decrypt(byteArrayOf(1, 99, 0, 0)) }
    }
}
