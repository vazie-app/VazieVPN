package app.vazie.vpn.api

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/** The clock the session duration is read from, and the reason the old one stood still. */
@OptIn(ExperimentalCoroutinesApi::class)
fun connectionTicks(
    snapshot: Flow<VpnConnectionSnapshot>,
    clock: Clock,
    intervalMillis: Long = SECOND_MILLIS,
): Flow<Instant> = snapshot
    .map { it.state is VpnConnectionState.Connected }
    .distinctUntilChanged()
    .transformLatest { connected ->
        // One instant is emitted whatever the state, so a screen that is not connected still gets a
        // `now` to build its state with and does not wait for a tick that will never come.
        emit(clock.instant())
        while (connected) {
            delay(millisUntilNextTick(clock.instant(), intervalMillis))
            emit(clock.instant())
        }
    }

private fun millisUntilNextTick(now: Instant, intervalMillis: Long): Long {
    val elapsed = Math.floorMod(now.toEpochMilli(), intervalMillis)
    return intervalMillis - elapsed
}

private const val SECOND_MILLIS = 1_000L
