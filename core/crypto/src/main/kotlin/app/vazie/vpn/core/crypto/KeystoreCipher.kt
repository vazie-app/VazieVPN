package app.vazie.vpn.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM with the key in the Android Keystore. */
class KeystoreCipher(private val alias: String) : ByteCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plaintext)
        return ByteArray(HEADER_SIZE + iv.size + body.size).also { out ->
            out[0] = FORMAT_VERSION
            out[1] = iv.size.toByte()
            iv.copyInto(out, HEADER_SIZE)
            body.copyInto(out, HEADER_SIZE + iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > HEADER_SIZE) { "profile store is too short to be a record" }
        require(ciphertext[0] == FORMAT_VERSION) { "unknown profile store format" }
        val ivSize = ciphertext[1].toInt()
        require(ivSize in 1..MAX_IV_SIZE && ciphertext.size > HEADER_SIZE + ivSize) {
            "profile store header does not describe this file"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, ciphertext, HEADER_SIZE, ivSize),
        )
        return cipher.doFinal(ciphertext, HEADER_SIZE + ivSize, ciphertext.size - HEADER_SIZE - ivSize)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val MAX_IV_SIZE = 16

        /** One version byte and one IV-length byte before the IV itself. */
        private const val HEADER_SIZE = 2
        private const val FORMAT_VERSION: Byte = 1
    }
}
