package app.vazie.vpn.feature.home

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectionDiagnostics
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** The runtime's state as Home's state. Pure, so the whole vocabulary is one JVM test away. */
internal fun homeState(
    snapshot: VpnConnectionSnapshot,
    selection: ProfileId?,
    summaries: List<ProfileSummary>,
    runnable: Boolean,
    now: Instant,
    traffic: TrafficStats = TrafficStats.NONE,
): HomeUiState {
    // The tunnel's profile outranks the selection: the screen describes what exists before it
    // describes what was chosen.
    val subject = snapshot.subject
        ?: selection
            ?.let { id -> summaries.firstOrNull { it.id == id } }
            ?.let(::ConnectionSubject)
        ?: return if (summaries.isEmpty()) HomeUiState.Empty else HomeUiState.Unselected

    return HomeUiState.Ready(
        // The list's summary when it has one: it is the stored, current copy.
        configuration = (summaries.firstOrNull { it.id == subject.profile.id } ?: subject.profile)
            .toConfigurationUi(),
        // Straight from the runtime: the one account of what happened.
        connection = snapshot.toConnectionUi(now, traffic),
        runnable = runnable,
    )
}

/** What the screen says it is connected to. */
internal fun ConnectionSubject.toConfigurationUi(): HomeConfigurationUi =
    profile.toConfigurationUi()

internal fun ProfileSummary.toConfigurationUi(): HomeConfigurationUi = HomeConfigurationUi(
    name = name,
    mark = (countryCode ?: protocolLabel).take(MARK_LENGTH).uppercase(),
    protocolLabel = protocolLabel,
    engineLabel = engineId.name.lowercase(Locale.ROOT),
)

private fun VpnConnectionSnapshot.toConnectionUi(now: Instant, traffic: TrafficStats): ConnectionUiState =
    when (val current = state) {
        VpnConnectionState.Idle -> ConnectionUiState.Idle
        VpnConnectionState.Preparing -> ConnectionUiState.Preparing
        is VpnConnectionState.Connecting -> ConnectionUiState.Connecting
        VpnConnectionState.Disconnecting -> ConnectionUiState.Disconnecting
        VpnConnectionState.NoInternet -> ConnectionUiState.NoInternet
        is VpnConnectionState.Failed -> ConnectionUiState.Failed(current.failure.toUi())
        is VpnConnectionState.Connected -> ConnectionUiState.Connected(
            SessionUi(
                seconds = Duration.between(current.since, now).seconds.coerceAtLeast(0),
                rxBytes = traffic.rxBytes,
                txBytes = traffic.txBytes,
                diagnostics = diagnostics?.toUi() ?: UNKNOWN_DIAGNOSTICS,
            )
        )
    }

private fun ConnectionDiagnostics.toUi(): DiagnosticsUi = DiagnosticsUi(
    engine = engine,
    protocol = protocol,
    security = security,
    transport = transport,
    flow = flow,
    endpointHost = endpointHost,
    endpointPort = endpointPort,
    latencyMs = latencyMs,
    dnsMode = dnsMode.name.lowercase(),
    ipv4 = ipv4,
    ipv6 = ipv6,
)

/** A closed set to a closed set. Several runtime failures share one sentence because they share one remedy —
 * the panel exists to tell the user what to do, not to enumerate what went wrong inside. */

internal fun ConnectionFailure.toUi(): ConnectionFailureUi = when (this) {
    ConnectionFailure.ConsentDenied -> ConnectionFailureUi.PERMISSION
    ConnectionFailure.NoInternet -> ConnectionFailureUi.NO_INTERNET
    ConnectionFailure.ProfileMissing -> ConnectionFailureUi.PROFILE
    is ConnectionFailure.ProfileInvalid -> ConnectionFailureUi.PROFILE
    is ConnectionFailure.UnsupportedConfiguration -> ConnectionFailureUi.UNSUPPORTED
    ConnectionFailure.TunnelSetupFailed -> ConnectionFailureUi.TUNNEL
    is ConnectionFailure.EngineFailed -> ConnectionFailureUi.HANDSHAKE
    ConnectionFailure.TunnelUnusable -> ConnectionFailureUi.TUNNEL_UNUSABLE
    ConnectionFailure.ServiceStopped -> ConnectionFailureUi.TUNNEL
    ConnectionFailure.Unknown -> ConnectionFailureUi.UNKNOWN
}

private val UNKNOWN_DIAGNOSTICS = DiagnosticsUi(
    engine = "",
    protocol = "",
    security = null,
    transport = null,
    flow = null,
    endpointHost = "",
    endpointPort = 0,
    latencyMs = null,
    dnsMode = "",
    ipv4 = false,
    ipv6 = false,
)

private const val MARK_LENGTH = 2
