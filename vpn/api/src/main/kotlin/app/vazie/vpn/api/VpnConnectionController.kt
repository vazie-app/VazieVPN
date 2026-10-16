package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import kotlinx.coroutines.flow.StateFlow

/** The one way anything starts or stops a tunnel, and the one place its state lives. */
interface VpnConnectionController {

    /** The current connection, and every change to it. Never completes. */
    val snapshot: StateFlow<VpnConnectionSnapshot>

    /** Asks for a tunnel to [profileId]. */
    suspend fun connect(profileId: ProfileId): ConnectResult

    /** Whether an engine in this build could run the stored profile, asked without starting anything. */
    suspend fun canRun(profileId: ProfileId): Boolean

    /** Brings the tunnel down. Safe to call in any state, including in the middle of [connect]: cancelling a
     * connection in progress is the same request as ending a finished one. */
    suspend fun disconnect()

    /** Bytes moved by the current session, read from the OS counters at the moment of asking;
     * [TrafficStats.NONE] when nothing is connected. */
    suspend fun traffic(): TrafficStats
}

/** What [VpnConnectionController.connect] could do about the request right away. */
sealed interface ConnectResult {

    /** Accepted. Watch `snapshot`. */
    data object Started : ConnectResult

    /** VPN permission is missing: run `VpnService.prepare()` and call [VpnConnectionController.connect]
     * again. */
    data object PermissionRequired : ConnectResult

    /** Refused before anything was started. `snapshot` already carries the same failure. */
    data class Rejected(val failure: ConnectionFailure) : ConnectResult
}
