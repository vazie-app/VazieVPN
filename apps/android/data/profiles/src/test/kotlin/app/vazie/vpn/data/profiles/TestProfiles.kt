package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.crypto.ByteCipher

import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.Endpoint
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.UnknownParameter
import app.vazie.vpn.api.UnknownParameters
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Synthetic drafts, and a cipher the JVM can run. */
internal object TestProfiles {

    const val USER_ID = "00000000-0000-4000-8000-000000000001"
    const val OTHER_USER_ID = "00000000-0000-4000-8000-000000000002"
    const val HOST = "relay.example.net"
    const val PUBLIC_KEY = "not-a-real-reality-public-key-0000000000000"
    const val SHORT_ID = "0011223344556677"

    fun realityDraft(
        userId: String = USER_ID,
        host: String = HOST,
        suggestedName: String? = "Home relay",
    ): ProfileDraft.Xray = ProfileDraft.Xray(
        suggestedName = suggestedName,
        outbound = XrayOutbound.Vless(
            userId = Secret.of(userId),
            endpoint = Endpoint(host = host, port = 443),
            security = XraySecurity.Reality(
                serverName = host,
                fingerprint = "chrome",
                publicKey = Secret.of(PUBLIC_KEY),
                shortId = Secret.of(SHORT_ID),
                spiderX = "/",
            ),
            transport = XrayTransport.Tcp(headerType = null),
            flow = XrayFlow.XTLS_RPRX_VISION,
            unknownParameters = UnknownParameters(
                listOf(UnknownParameter("packetEncoding", Secret.of("xudp"))),
            ),
        ),
    )

    fun plainDraft(suggestedName: String? = null): ProfileDraft.Xray = ProfileDraft.Xray(
        suggestedName = suggestedName,
        outbound = XrayOutbound.Vless(
            userId = Secret.of(OTHER_USER_ID),
            endpoint = Endpoint(host = HOST, port = 8443),
            security = XraySecurity.None,
            transport = XrayTransport.WebSocket(path = "/vazie", host = HOST),
        ),
    )
}

internal class TestProfileCipher : ByteCipher {

    private val key = KeyGenerator.getInstance("AES").apply { init(KEY_BITS) }.generateKey()
        .let { SecretKeySpec(it.encoded, "AES") }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_SIZE),
        )
        return cipher.doFinal(ciphertext, IV_SIZE, ciphertext.size - IV_SIZE)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_SIZE = 12
    }
}
