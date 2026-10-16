package app.vazie.vpn.feature.config.presentation

import app.vazie.vpn.feature.config.ConfigDetailsUiState
import app.vazie.vpn.feature.config.ConfigPreviewUi
import app.vazie.vpn.feature.config.DetectionUiState
import app.vazie.vpn.feature.config.R
import app.vazie.vpn.api.ProfileDetails
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurityKind
import app.vazie.vpn.api.XrayTransportKind
import app.vazie.vpn.config.ParseResult
import app.vazie.vpn.config.ProtocolDescriptor
import kotlinx.collections.immutable.toImmutableList
import app.vazie.vpn.core.model.LastUsed
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** A parse result as the wizard shows it. */
internal fun ParseResult.toDetection(): DetectionUiState = when (this) {
    is ParseResult.Recognized -> DetectionUiState.Recognized(draft.toPreview(descriptor))
    is ParseResult.Unsupported ->
        DetectionUiState.Unsupported(protocolLabel = descriptor.label, feature = feature)

    is ParseResult.Invalid -> DetectionUiState.Invalid(reason)
}

internal fun ProfileDraft.toPreview(descriptor: ProtocolDescriptor): ConfigPreviewUi {
    val outbound = (this as ProfileDraft.Xray).outbound as XrayOutbound.Vless
    return ConfigPreviewUi(
        protocolLabel = descriptor.label,
        securityLabel = outbound.security.kind.label(),
        transportLabel = outbound.transport.kind.label(),
        server = "${endpoint.host}:${endpoint.port}",
        endpointHost = endpoint.host,
        endpointPort = endpoint.port,
        serverName = outbound.security.serverName,
        fingerprint = outbound.security.fingerprint,
        flow = outbound.flow?.wireName,
        // A link with no fragment still has to be called something, and the host is what the user
        // would call it themselves. Product copy stays here rather than in the parser.
        suggestedName = suggestedName ?: endpoint.host,
        mark = descriptor.mark,
        keptParameters = outbound.unknownParameters.names.toImmutableList(),
    )
}

internal fun XraySecurityKind.label(): String? = when (this) {
    XraySecurityKind.NONE -> null
    XraySecurityKind.TLS -> "TLS"
    XraySecurityKind.REALITY -> "REALITY"
}

internal fun XrayTransportKind.label(): String = when (this) {
    XrayTransportKind.TCP -> "TCP"
    XrayTransportKind.WEBSOCKET -> "WebSocket"
    XrayTransportKind.GRPC -> "gRPC"
}

/** A stored profile as its detail screen shows it. */
internal fun ProfileDetails.toUiState(
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): ConfigDetailsUiState = ConfigDetailsUiState(
    name = name,
    protocolLabel = protocolLabel,
    securityLabel = security.label(),
    transportLabel = transport.label(),
    server = "$endpointHost:$endpointPort",
    endpointHost = endpointHost,
    endpointPort = endpointPort,
    serverName = serverName,
    fingerprint = fingerprint,
    flow = flow?.wireName,
    transportDetail = transportDetail,
    keptParameters = unknownParameterNames.toImmutableList(),
    // VLESS calls its credential a user id and every client labels it UUID; the label is protocol
    // vocabulary, like the protocol name itself, and stays untranslated.
    secretLabel = CREDENTIAL_LABEL,
    lastUsed = LastUsed.of(lastUsedAt = lastUsedAt, now = now, zone = zone),
    engineLabel = engineId.name.lowercase(Locale.ROOT),
    loading = false,
)

private const val CREDENTIAL_LABEL = "UUID"
