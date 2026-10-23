package app.vazie.vpn.usage

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** "Last used" is a small fact that is easy to make untrue. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileUsageRecorderTest {

    @Test
    fun `a connection that comes up is recorded`() = runTest {
        val marked = marked { emit(VpnConnectionState.Connected(HOME, NOW)) }

        assertEquals(listOf(HOME), marked)
    }

    @Test
    fun `an attempt that never connects is not`() = runTest {
        val marked = marked {
            emit(VpnConnectionState.Preparing)
            emit(VpnConnectionState.Connecting(HOME))
            emit(VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable))
            emit(VpnConnectionState.Idle)
        }

        assertEquals(emptyList(), marked, "a failure was recorded as a use")
    }

    @Test
    fun `a running tunnel is recorded once, not once per snapshot`() = runTest {
        val marked = marked {
            emit(VpnConnectionState.Connected(HOME, NOW))
            repeat(SIXTY_SECONDS) { second ->
                snapshots.value = VpnConnectionSnapshot(
                    state = VpnConnectionState.Connected(HOME, NOW),
                    subject = ConnectionSubject(
                        ProfileSummary(
                            id = HOME,
                            name = "Home relay $second",
                            engineId = VpnEngineId.XRAY,
                            protocolLabel = "VLESS",
                        ),
                    ),
                )
                runCurrent()
            }
        }

        assertEquals(listOf(HOME), marked)
    }

    @Test
    fun `reconnecting to the same configuration is recorded again`() = runTest {
        // A tunnel taken down and brought back up gets a new timestamp: dedup breaks on the state in between.
        val marked = marked {
            emit(VpnConnectionState.Connected(HOME, NOW))
            emit(VpnConnectionState.Disconnecting)
            emit(VpnConnectionState.Idle)
            emit(VpnConnectionState.Connected(HOME, NOW.plusSeconds(AN_HOUR)))
        }

        assertEquals(listOf(HOME, HOME), marked)
    }

    @Test
    fun `the id comes from the running tunnel, not from the summary beside it`() = runTest {
        // The state, not the summary, is authoritative: it is what the runtime is actually running.
        val marked = marked {
            snapshots.value = VpnConnectionSnapshot(
                state = VpnConnectionState.Connected(HOME, NOW),
                subject = ConnectionSubject(
                    ProfileSummary(
                        id = TRAVEL,
                        name = "Travel relay",
                        engineId = VpnEngineId.XRAY,
                        protocolLabel = "VLESS",
                    ),
                ),
            )
        }

        assertEquals(listOf(HOME), marked, "the displayed summary was recorded instead")
    }

    private val snapshots = MutableStateFlow(VpnConnectionSnapshot())

    /** Collects what `usedProfiles` produces while [emissions] runs. */
    private suspend fun TestScope.marked(emissions: TestScope.() -> Unit): List<ProfileId> {
        val seen = mutableListOf<ProfileId>()
        val collector = launch { usedProfiles(snapshots).toList(seen) }
        runCurrent()
        emissions()
        advanceUntilIdle()
        collector.cancel()
        return seen
    }

    /** `MutableStateFlow` conflates, so `runCurrent()` lets the collector see each state. */
    private fun TestScope.emit(state: VpnConnectionState) {
        snapshots.value = VpnConnectionSnapshot(state = state)
        runCurrent()
    }

    private companion object {
        val HOME = ProfileId("home-relay")
        val TRAVEL = ProfileId("travel-relay")
        val NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")
        const val SIXTY_SECONDS = 60
        const val AN_HOUR = 3_600L
    }
}
