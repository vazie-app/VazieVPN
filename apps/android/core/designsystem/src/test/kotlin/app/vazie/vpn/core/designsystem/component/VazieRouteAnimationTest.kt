package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The route's story: connecting is played to its end, a connection is held until it has been, and a
 * disconnection draws the line back rather than dropping it. */
class VazieRouteAnimationTest {

    @Test
    fun `connecting grows the line once and then fills the server`() = scenario(VazieRouteState.Disconnected) {
        play(VazieRouteState.Connecting)
        advance(HALF_LINE)
        val halfway = animation.line.value
        assertTrue(halfway in 0.2f..0.99f, "line at $halfway halfway through")
        assertEquals(0f, animation.fill.value, "the node fills only once the line has arrived")

        advance(LONG)
        assertEquals(1f, animation.line.value)
        assertTrue(animation.fill.value > 0.5f, "the node did not fill while waiting")
        assertTrue(animation.fill.value < 1f, "the node filled to the top before the connection")
        assertEquals(VazieRouteState.Connecting, animation.shown)
    }

    @Test
    fun `an early connection is held until the story has played`() = scenario(VazieRouteState.Disconnected) {
        play(VazieRouteState.Connecting)
        advance(FRAMES_3)
        play(VazieRouteState.Connected)
        advance(FRAMES_3)

        assertTrue(animation.isCatchingUpTo(VazieRouteState.Connected), "the connection was shown before the route arrived")
        assertEquals(VazieRouteState.Connecting, animation.shown)
        assertTrue(animation.line.value < 1f, "the line jumped to the server")

        advance(LONG)
        assertFalse(animation.isCatchingUpTo(VazieRouteState.Connected))
        assertEquals(VazieRouteState.Connected, animation.shown)
        assertEquals(1f, animation.line.value)
        assertEquals(1f, animation.fill.value)
    }

    @Test
    fun `opening on a connection shows it at once`() = scenario(VazieRouteState.Connected) {
        play(VazieRouteState.Connected)
        advance(FRAMES_3)
        assertEquals(VazieRouteState.Connected, animation.shown)
        assertFalse(animation.isCatchingUpTo(VazieRouteState.Connected))
    }

    @Test
    fun `disconnecting drains the node and draws the line back`() = scenario(VazieRouteState.Connected) {
        play(VazieRouteState.Disconnected)
        advance(FRAMES_3)
        assertEquals(VazieRouteState.Disconnected, animation.shown, "a disconnection is never held")
        assertTrue(animation.line.value > 0.9f, "the line was dropped instead of drawn back")
        assertTrue(animation.fill.value < 1f, "the node did not start draining")

        advance(LONG)
        assertEquals(0f, animation.line.value)
        assertEquals(0f, animation.fill.value)
    }

    @Test
    fun `a failure is shown at once`() = scenario(VazieRouteState.Disconnected) {
        play(VazieRouteState.Connecting)
        advance(HALF_LINE)
        play(VazieRouteState.Failed)
        advance(FRAMES_3)
        assertEquals(VazieRouteState.Failed, animation.shown)
        assertFalse(animation.isCatchingUpTo(VazieRouteState.Failed))
    }

    @Test
    fun `with animations off every state is drawn at once`() = scenario(VazieRouteState.Disconnected, still = true) {
        play(VazieRouteState.Connected)
        advance(FRAMES_3)
        assertEquals(VazieRouteState.Connected, animation.shown)
        assertFalse(animation.isCatchingUpTo(VazieRouteState.Connected))
    }

    /** One `LaunchedEffect` per state, as `rememberVazieRouteAnimation` runs them. */
    private class Scenario(val scope: CoroutineScope, val animation: VazieRouteAnimation, val still: Boolean) {
        private var effect: Job? = null

        fun play(state: VazieRouteState) {
            effect?.cancel()
            effect = scope.launch { animation.playTo(state, still) }
        }

        suspend fun advance(millis: Long) {
            repeat((millis / FRAME_MILLIS).toInt()) { yield() }
        }

        fun finish() {
            effect?.cancel()
        }
    }

    private fun scenario(
        initial: VazieRouteState,
        still: Boolean = false,
        block: suspend Scenario.() -> Unit,
    ) = runBlocking(VirtualFrames()) {
        val scenario = Scenario(this, VazieRouteAnimation(initial, still), still)
        scenario.block()
        scenario.finish()
    }

    /** A frame clock that hands out one 16 ms frame each time the test yields. */
    private class VirtualFrames : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            nanos += FRAME_MILLIS * NANOS_PER_MILLI
            return onFrame(nanos)
        }
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
        const val FRAMES_3 = FRAME_MILLIS * 3
        const val HALF_LINE = 500L
        const val LONG = 6_000L
    }
}
