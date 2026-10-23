package app.vazie.vpn.engine.xray

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.Endpoint
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import java.time.Instant

/** Synthetic profiles, assembled from values that cannot be anybody's. */
internal object XrayTestProfiles {

    const val USER_ID = "00000000-0000-4000-8000-000000000000"
    const val PUBLIC_KEY = "synthetic-reality-public-key-not-a-real-one"
    const val SHORT_ID = "0123456789abcdef"
    const val HOST = "relay.example.net"
    const val PORT = 443

    fun profile(
        security: XraySecurity = XraySecurity.None,
        transport: XrayTransport = XrayTransport.Tcp(),
        flow: XrayFlow? = null,
        name: String = "Test relay",
    ): VpnProfile.Xray = VpnProfile.Xray(
        id = ProfileId("test-profile"),
        name = name,
        origin = ProfileOrigin.IMPORTED_LINK,
        createdAt = Instant.EPOCH,
        outbound = XrayOutbound.Vless(
            userId = Secret.of(USER_ID),
            endpoint = Endpoint(HOST, PORT),
            security = security,
            transport = transport,
            flow = flow,
        ),
    )

    fun tls(
        serverName: String? = HOST,
        fingerprint: String? = "chrome",
        alpn: List<String> = emptyList(),
        allowInsecure: Boolean = false,
    ) = XraySecurity.Tls(
        serverName = serverName,
        fingerprint = fingerprint,
        alpn = alpn,
        allowInsecure = allowInsecure,
    )

    fun reality(
        serverName: String? = HOST,
        fingerprint: String? = "chrome",
        shortId: String? = SHORT_ID,
        spiderX: String? = null,
    ) = XraySecurity.Reality(
        serverName = serverName,
        fingerprint = fingerprint,
        publicKey = Secret.of(PUBLIC_KEY),
        shortId = shortId?.let { Secret.of(it) },
        spiderX = spiderX,
    )
}
