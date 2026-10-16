package app.vazie.vpn.feature.config

import kotlinx.collections.immutable.persistentListOf
import app.vazie.vpn.core.model.LastUsed

/** Synthetic saved configurations, for previews and tests. */
internal object ConfigDetailsFixtures {

    private const val SAMPLE_UUID = "00000000-0000-4000-8000-000000000001"

    val sample = ConfigDetailsUiState(
        name = "Home relay",
        protocolLabel = "VLESS",
        engineLabel = "xray",
        securityLabel = "REALITY",
        transportLabel = "TCP",
        server = "relay.example.net:443",
        endpointHost = "relay.example.net",
        endpointPort = 443,
        serverName = "relay.example.net",
        fingerprint = "chrome",
        flow = "xtls-rprx-vision",
        keptParameters = persistentListOf("packetEncoding"),
        secretLabel = "UUID",
        secret = "",
        lastUsed = LastUsed.Yesterday,
        loading = false,
    )

    val revealed = sample.copy(secret = SAMPLE_UUID, secretRevealed = true)

    val webSocket = ConfigDetailsUiState(
        name = "Travel relay",
        protocolLabel = "VLESS",
        engineLabel = "xray",
        securityLabel = "TLS",
        transportLabel = "WebSocket",
        transportDetail = "/vazie",
        server = "edge.example.net:8443",
        endpointHost = "edge.example.net",
        endpointPort = 8443,
        serverName = "edge.example.net",
        secretLabel = "UUID",
        lastUsed = LastUsed.Never,
        loading = false,
    )

    val missing = ConfigDetailsUiState(loading = false, missing = true, name = "Home relay")
}
