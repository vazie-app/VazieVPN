package app.vazie.vpn.feature.home

/** Synthetic presentation data for previews and tests. */
internal object HomeFixtures {

    val configuration = HomeConfigurationUi(
        name = "Home relay",
        mark = "VL",
        protocolLabel = "VLESS",
        engineLabel = "xray",
    )

    val diagnostics = DiagnosticsUi(
        engine = "xray",
        protocol = "vless",
        security = "reality",
        transport = "tcp",
        flow = "xtls-vision",
        endpointHost = "relay.example.net",
        endpointPort = 443,
        latencyMs = 32,
        dnsMode = "tunnel",
        ipv4 = true,
        ipv6 = false,
    )

    val session = SessionUi(
        seconds = 252,
        rxBytes = 214L * 1024 * 1024 + 200 * 1024,
        txBytes = 38L * 1024 * 1024 + 800 * 1024,
        diagnostics = diagnostics,
    )

    val ready = HomeUiState.Ready(configuration, ConnectionUiState.Idle)

    val connected = HomeUiState.Ready(configuration, ConnectionUiState.Connected(session))

    /** A configuration this build cannot run: `Idle` with a disabled button. Not in [allStates]. */
    val unsupported = HomeUiState.Ready(configuration, ConnectionUiState.Idle, runnable = false)

    /** The busiest a real Home gets: every diagnostics field populated and a long session. */
    val maximal = HomeUiState.Ready(
        configuration = HomeConfigurationUi(
            name = "Home relay — backup",
            mark = "VL",
            protocolLabel = "VLESS",
            engineLabel = "xray",
        ),
        connection = ConnectionUiState.Connected(
            SessionUi(
                seconds = 4 * 3600 + 37 * 60 + 9,
                rxBytes = 12L * 1024 * 1024 * 1024 + 400L * 1024 * 1024,
                txBytes = 3L * 1024 * 1024 * 1024 + 900L * 1024 * 1024,
                diagnostics = DiagnosticsUi(
                    engine = "xray",
                    protocol = "vless",
                    security = "reality",
                    transport = "grpc",
                    flow = "xtls-rprx-vision",
                    endpointHost = "ams-backup-01.relay.example.net",
                    endpointPort = 8443,
                    latencyMs = null,
                    dnsMode = "tunnel",
                    ipv4 = true,
                    ipv6 = false,
                ),
            ),
        ),
    )

    /** Every state Home can be in, for the previews that draw them. */
    val allStates: List<HomeUiState> = listOf(
        HomeUiState.Empty,
        ready,
        HomeUiState.Ready(configuration, ConnectionUiState.Preparing),
        HomeUiState.Ready(configuration, ConnectionUiState.Connecting),
        connected,
        HomeUiState.Ready(configuration, ConnectionUiState.Disconnecting),
        HomeUiState.Ready(configuration, ConnectionUiState.Failed(ConnectionFailureUi.TUNNEL_UNUSABLE)),
        HomeUiState.Ready(configuration, ConnectionUiState.NoInternet),
    )
}
