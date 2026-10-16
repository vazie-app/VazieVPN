package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The cadence the session duration is read at, on its own. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionTicksTest {

    @Test
    fun `a disconnected state gets one instant and no timer`() = runTest {
        val clock = MutableClock()
        val snapshots = MutableStateFlow(VpnConnectionSnapshot())
        val seen = mutableListOf<Instant>()

        backgroundScope.launch { connectionTicks(snapshots, clock).toList(seen) }
        runCurrent()
        advanceTimeBy(TEN_SECONDS_MILLIS)
        runCurrent()

        assertEquals(1, seen.size, "an idle screen is being woken up on a timer")
    }

    @Test
    fun `a connected state keeps ticking while nothing else changes`() = runTest {
        val clock = MutableClock()
        val snapshots = MutableStateFlow(connected())
        val seen = mutableListOf<Instant>()

        backgroundScope.launch { connectionTicks(snapshots, clock).toList(seen) }
        runCurrent()
        repeat(5) {
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(SECOND_MILLIS)
            runCurrent()
        }

        assertEquals(6, seen.size, "the timer stopped while the tunnel was up")
        assertEquals(Instant.EPOCH.plusSeconds(5), seen.last())
    }

    @Test
    fun `disconnecting stops the timer`() = runTest {
        val clock = MutableClock()
        val snapshots = MutableStateFlow(connected())
        val seen = mutableListOf<Instant>()

        backgroundScope.launch { connectionTicks(snapshots, clock).toList(seen) }
        runCurrent()
        snapshots.value = VpnConnectionSnapshot(state = VpnConnectionState.Idle)
        runCurrent()
        val afterDisconnect = seen.size

        advanceTimeBy(TEN_SECONDS_MILLIS)
        runCurrent()

        assertEquals(afterDisconnect, seen.size, "a disconnected screen is still on a timer")
    }

    @Test
    fun `the interval is the caller's, so a widget can tick once a minute`() = runTest {
        val clock = MutableClock()
        val snapshots = MutableStateFlow(connected())
        val seen = mutableListOf<Instant>()

        backgroundScope.launch { connectionTicks(snapshots, clock, MINUTE_MILLIS).toList(seen) }
        runCurrent()
        advanceTimeBy(TEN_SECONDS_MILLIS)
        runCurrent()
        assertEquals(1, seen.size, "a minute-interval ticker fired inside ten seconds")

        clock.advance(Duration.ofMinutes(1))
        advanceTimeBy(MINUTE_MILLIS)
        runCurrent()

        assertTrue(seen.size >= 2, "a minute-interval ticker did not fire after a minute")
    }

    private fun connected() = VpnConnectionSnapshot(
        state = VpnConnectionState.Connected(ProfileId("profile-1"), Instant.EPOCH),
    )

    private class MutableClock : Clock() {
        private var current: Instant = Instant.EPOCH
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(by: Duration) {
            current = current.plus(by)
        }
    }

    private companion object {
        const val SECOND_MILLIS = 1_000L
        const val MINUTE_MILLIS = 60_000L
        const val TEN_SECONDS_MILLIS = 10_000L
    }
}
