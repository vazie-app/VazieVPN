package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import java.time.Instant

/** A stored profile as its detail screen sees it: everything except the credential. */
data class ProfileDetails(
    val id: ProfileId,
    val name: String,
    val engineId: VpnEngineId,
    val protocolLabel: String,
    val origin: ProfileOrigin?,
    val security: XraySecurityKind,
    val transport: XrayTransportKind,
    val endpointHost: String,
    val endpointPort: Int,
    val serverName: String?,
    val fingerprint: String?,
    val flow: XrayFlow?,
    /** The WebSocket path or the gRPC service name — whatever the transport made meaningful. */
    val transportDetail: String?,
    val unknownParameterNames: List<String>,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)
