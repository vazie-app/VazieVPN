package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.Endpoint
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.UnknownParameter
import app.vazie.vpn.api.UnknownParameters
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import java.time.Instant
import kotlinx.serialization.Serializable

/** The on-disk shape of the profile store, and the only place `Secret.expose` is called for storage. */
@Serializable
internal data class StoredProfiles(
    val version: Int = CURRENT_VERSION,
    val profiles: List<StoredProfile> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
internal data class StoredProfile(
    val id: String,
    val name: String,
    val engine: String,
    val origin: String,
    val createdAt: String,
    val lastUsedAt: String? = null,
    // A `favourite` key written by earlier versions is skipped on read (`ignoreUnknownKeys`) and
    // dropped on the next write: pinning no longer exists.
    val outbound: StoredOutbound,
)

@Serializable
internal data class StoredOutbound(
    val protocol: String,
    val userId: String,
    val host: String,
    val port: Int,
    val security: StoredSecurity,
    val transport: StoredTransport,
    val flow: String? = null,
    val unknown: List<StoredParameter> = emptyList(),
)

@Serializable
internal data class StoredSecurity(
    val kind: String,
    val serverName: String? = null,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
)

@Serializable
internal data class StoredTransport(
    val kind: String,
    val headerType: String? = null,
    val path: String? = null,
    val host: String? = null,
    val serviceName: String? = null,
    val multiMode: Boolean = false,
)

@Serializable
internal data class StoredParameter(val name: String, val value: String)

private const val ENGINE_XRAY = "xray"
private const val PROTOCOL_VLESS = "vless"
private const val ORIGIN_IMPORTED_LINK = "imported_link"
// `built_in` is retired; named only so the string is never reused.
@Suppress("unused")
private const val ORIGIN_BUILT_IN_RETIRED = "built_in"
private const val SECURITY_NONE = "none"
private const val SECURITY_TLS = "tls"
private const val SECURITY_REALITY = "reality"
private const val TRANSPORT_TCP = "tcp"
private const val TRANSPORT_WEBSOCKET = "ws"
private const val TRANSPORT_GRPC = "grpc"

internal fun VpnProfile.toRecord(): StoredProfile = when (this) {
    is VpnProfile.Xray -> StoredProfile(
        id = id.value,
        name = name,
        engine = ENGINE_XRAY,
        origin = when (origin) {
            ProfileOrigin.IMPORTED_LINK -> ORIGIN_IMPORTED_LINK
            // A profile with no origin is a provisioned one and must never be persisted.
            null -> error("a profile that was never stored cannot be written to the profile store")
        },
        createdAt = createdAt.toString(),
        lastUsedAt = lastUsedAt?.toString(),
        outbound = outbound.toRecord(),
    )
}

private fun XrayOutbound.toRecord(): StoredOutbound = when (this) {
    is XrayOutbound.Vless -> StoredOutbound(
        protocol = PROTOCOL_VLESS,
        userId = userId.expose(),
        host = endpoint.host,
        port = endpoint.port,
        security = security.toRecord(),
        transport = transport.toRecord(),
        flow = flow?.wireName,
        unknown = unknownParameters.entries.map { StoredParameter(it.name, it.value.expose()) },
    )
}

private fun XraySecurity.toRecord(): StoredSecurity = when (this) {
    XraySecurity.None -> StoredSecurity(kind = SECURITY_NONE)
    is XraySecurity.Tls -> StoredSecurity(
        kind = SECURITY_TLS,
        serverName = serverName,
        fingerprint = fingerprint,
        alpn = alpn,
        allowInsecure = allowInsecure,
    )

    is XraySecurity.Reality -> StoredSecurity(
        kind = SECURITY_REALITY,
        serverName = serverName,
        fingerprint = fingerprint,
        publicKey = publicKey.expose(),
        shortId = shortId?.expose(),
        spiderX = spiderX,
    )
}

private fun XrayTransport.toRecord(): StoredTransport = when (this) {
    is XrayTransport.Tcp -> StoredTransport(kind = TRANSPORT_TCP, headerType = headerType)
    is XrayTransport.WebSocket -> StoredTransport(
        kind = TRANSPORT_WEBSOCKET,
        path = path,
        host = host,
    )

    is XrayTransport.Grpc -> StoredTransport(
        kind = TRANSPORT_GRPC,
        serviceName = serviceName,
        multiMode = multiMode,
    )
}

internal fun StoredProfile.toProfile(): VpnProfile {
    require(engine == ENGINE_XRAY) { "unknown engine in the profile store" }
    return VpnProfile.Xray(
        id = ProfileId(id),
        name = name,
        origin = when (origin) {
            ORIGIN_IMPORTED_LINK -> ProfileOrigin.IMPORTED_LINK
            else -> error("unknown profile origin in the profile store")
        },
        createdAt = Instant.parse(createdAt),
        lastUsedAt = lastUsedAt?.let(Instant::parse),
        outbound = outbound.toOutbound(),
    )
}

private fun StoredOutbound.toOutbound(): XrayOutbound {
    require(protocol == PROTOCOL_VLESS) { "unknown outbound protocol in the profile store" }
    return XrayOutbound.Vless(
        userId = Secret.of(userId),
        endpoint = Endpoint(host = host, port = port),
        security = security.toSecurity(),
        transport = transport.toTransport(),
        flow = flow?.let { wire -> XrayFlow.entries.first { it.wireName == wire } },
        unknownParameters = UnknownParameters(
            unknown.map { UnknownParameter(it.name, Secret.of(it.value)) },
        ),
    )
}

private fun StoredSecurity.toSecurity(): XraySecurity = when (kind) {
    SECURITY_NONE -> XraySecurity.None
    SECURITY_TLS -> XraySecurity.Tls(
        serverName = serverName,
        fingerprint = fingerprint,
        alpn = alpn,
        allowInsecure = allowInsecure,
    )

    SECURITY_REALITY -> XraySecurity.Reality(
        serverName = serverName,
        fingerprint = fingerprint,
        publicKey = Secret.of(requireNotNull(publicKey) { "a stored REALITY profile has no key" }),
        shortId = shortId?.let { Secret.of(it) },
        spiderX = spiderX,
    )

    else -> error("unknown security kind in the profile store")
}

private fun StoredTransport.toTransport(): XrayTransport = when (kind) {
    TRANSPORT_TCP -> XrayTransport.Tcp(headerType = headerType)
    TRANSPORT_WEBSOCKET -> XrayTransport.WebSocket(path = path.orEmpty(), host = host)
    TRANSPORT_GRPC -> XrayTransport.Grpc(
        serviceName = serviceName.orEmpty(),
        multiMode = multiMode,
    )

    else -> error("unknown transport kind in the profile store")
}
