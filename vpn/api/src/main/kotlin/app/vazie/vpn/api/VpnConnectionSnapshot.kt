package app.vazie.vpn.api

/** Everything a surface may know about the current connection, in one value. */
data class VpnConnectionSnapshot(
    val state: VpnConnectionState = VpnConnectionState.Idle,
    /** Whose server this connection is to, and `null` when there is no connection to describe. */
    val subject: ConnectionSubject? = null,
    /** Console Mode's payload. Null unless a tunnel is up. */
    val diagnostics: ConnectionDiagnostics? = null,
)

/** Bytes moved since the tunnel came up. */
data class TrafficStats(val rxBytes: Long, val txBytes: Long) {

    val isKnown: Boolean get() = rxBytes >= 0 && txBytes >= 0

    companion object {
        const val UNKNOWN: Long = -1L
        val NONE = TrafficStats(rxBytes = 0, txBytes = 0)
        val UNAVAILABLE = TrafficStats(rxBytes = UNKNOWN, txBytes = UNKNOWN)
    }
}

/** The closed set Console Mode may render. */
data class ConnectionDiagnostics(
    val engine: String,
    val protocol: String,
    val security: String?,
    val transport: String?,
    val flow: String?,
    val endpointHost: String,
    val endpointPort: Int,
    val latencyMs: Long? = null,
    val dnsMode: DnsMode,
    val ipv4: Boolean,
    val ipv6: Boolean,
)

/** Where DNS queries from the device go while a tunnel is up. */
enum class DnsMode {
    /** Resolved through the tunnel by the engine's own resolver. */
    TUNNEL,
}
