package app.vazie.vpn.core.crypto

/** Encryption at rest, for any store that needs it. */
interface ByteCipher {

    fun encrypt(plaintext: ByteArray): ByteArray

    /** @throws GeneralSecurityException-shaped failures when the input is not what this cipher wrote. */
    fun decrypt(ciphertext: ByteArray): ByteArray
}
