package app.vazie.vpn.feature.home

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectResult
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.VpnConnectionState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Home reads the tunnel; it does not perform one. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `connect never reaches connected on its own`() = runTest(dispatcher) {
        val controller = FakeConnectionController()
        val viewModel = viewModel(controller)
        viewModel.state.collectIn(backgroundScope)

        viewModel.onAction(HomeAction.Connect)
        advanceUntilIdle()
        // Ten minutes of scheduler time. If any timer could produce a connection, this would find
        // it.
        advanceTimeBy(TEN_MINUTES_MILLIS)
        advanceUntilIdle()

        assertEquals(listOf(ProfileId(SELECTED)), controller.connectCalls)
        assertEquals(ConnectionUiState.Idle, viewModel.connection())
    }

    @Test
    fun `connected appears only when the controller says so`() = runTest(dispatcher) {
        val controller = FakeConnectionController()
        val viewModel = viewModel(controller)
        viewModel.state.collectIn(backgroundScope)
        advanceUntilIdle()

        controller.emit(
            state = VpnConnectionState.Connected(ProfileId(SELECTED), Instant.EPOCH),
            profile = summary(SELECTED),
        )
        // `runCurrent`, not `advanceUntilIdle`: a connected screen always has a tick scheduled.
        runCurrent()

        assertIs<ConnectionUiState.Connected>(viewModel.connection())
    }

    @Test
    fun `every runtime state reaches the screen`() = runTest(dispatcher) {
        val controller = FakeConnectionController()
        val viewModel = viewModel(controller)
        viewModel.state.collectIn(backgroundScope)
        advanceUntilIdle()

        val expected = mapOf<VpnConnectionState, (ConnectionUiState) -> Boolean>(
            VpnConnectionState.Preparing to { it == ConnectionUiState.Preparing },
            VpnConnectionState.Connecting(ProfileId(SELECTED)) to
                { it == ConnectionUiState.Connecting },
            VpnConnectionState.Disconnecting to { it == ConnectionUiState.Disconnecting },
            VpnConnectionState.NoInternet to { it == ConnectionUiState.NoInternet },
            VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable) to
                { it is ConnectionUiState.Failed },
            VpnConnectionState.Idle to { it == ConnectionUiState.Idle },
        )
        expected.forEach { (state, matches) ->
            controller.emit(state = state, profile = summary(SELECTED))
            advanceUntilIdle()
            assertTrue(matches(viewModel.connection()), "unexpected UI state for $state")
        }
    }

    @Test
    fun `connect asks for permission through an effect rather than pretending`() =
        runTest(dispatcher) {
            val controller = FakeConnectionController(result = ConnectResult.PermissionRequired)
            val viewModel = viewModel(controller)
            viewModel.state.collectIn(backgroundScope)

            viewModel.onAction(HomeAction.Connect)
            advanceUntilIdle()

            assertEquals(HomeEffect.RequestVpnPermission, viewModel.effects.first())
            assertEquals(ConnectionUiState.Idle, viewModel.connection())
        }

    @Test
    fun `granting permission connects again`() = runTest(dispatcher) {
        val controller = FakeConnectionController(result = ConnectResult.PermissionRequired)
        val viewModel = viewModel(controller)
        viewModel.state.collectIn(backgroundScope)

        viewModel.onAction(HomeAction.Connect)
        advanceUntilIdle()
        viewModel.onVpnPermissionGranted()
        advanceUntilIdle()

        assertEquals(2, controller.connectCalls.size)
    }

    @Test
    fun `disconnect goes to the controller`() = runTest(dispatcher) {
        val controller = FakeConnectionController()
        val viewModel = viewModel(controller)
        viewModel.state.collectIn(backgroundScope)

        viewModel.onAction(HomeAction.Disconnect)
        advanceUntilIdle()

        assertEquals(1, controller.disconnectCalls)
    }

    @Test
    fun `nothing selected renders the empty state and connects to nothing`() = runTest(dispatcher) {
        val controller = FakeConnectionController()
        val viewModel = HomeViewModel(
            controller = controller,
            selected = FakeSelectedProfileStore(initial = null),
            profiles = FakeProfileRepository(emptyList()),
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )
        viewModel.state.collectIn(backgroundScope)

        viewModel.onAction(HomeAction.Connect)
        advanceUntilIdle()

        assertEquals(HomeUiState.Empty, viewModel.state.value)
        assertTrue(controller.connectCalls.isEmpty())
    }

    @Test
    fun `a profile this build cannot run disables connect`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeConnectionController(runnable = false))
        viewModel.state.collectIn(backgroundScope)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<HomeUiState.Ready>(state)
        assertEquals(false, state.runnable)
    }

    @Test
    fun `navigation actions leave the state alone and are emitted as effects`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeConnectionController())
            viewModel.state.collectIn(backgroundScope)
            advanceUntilIdle()
            val before = viewModel.state.value

            viewModel.onAction(HomeAction.AddConfiguration)
            advanceUntilIdle()

            assertEquals(before, viewModel.state.value)
            assertEquals(HomeEffect.OpenAddConfiguration, viewModel.effects.first())
        }

    @Test
    fun `the gear opens Settings`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeConnectionController())
            viewModel.state.collectIn(backgroundScope)
            advanceUntilIdle()
            val before = viewModel.state.value

            viewModel.onAction(HomeAction.OpenSettings)
            advanceUntilIdle()
            assertEquals(HomeEffect.OpenSettings, viewModel.effects.first())

            assertEquals(before, viewModel.state.value, "navigation changed the connection state")
        }

    @Test
    fun `the session duration follows the clock while nothing else changes`() = runTest(dispatcher) {
        val clock = MutableClock(Instant.EPOCH)
        val controller = FakeConnectionController()
        val viewModel = viewModel(controller, clock)
        viewModel.state.collectIn(backgroundScope)
        runCurrent()

        controller.emit(
            state = VpnConnectionState.Connected(ProfileId(SELECTED), Instant.EPOCH),
            profile = summary(SELECTED),
        )
        runCurrent()
        assertEquals(0L, viewModel.seconds())

        // No snapshots for five seconds; the duration must still move.
        repeat(5) { tick(clock) }

        assertEquals(5L, viewModel.seconds())
    }

    @Test
    fun `traffic is read on the session tick, with nothing published by the controller`() =
        runTest(dispatcher) {
            val clock = MutableClock(Instant.EPOCH)
            val controller = FakeConnectionController()
            val viewModel = viewModel(controller, clock)
            viewModel.state.collectIn(backgroundScope)
            controller.emit(
                state = VpnConnectionState.Connected(ProfileId(SELECTED), Instant.EPOCH),
                profile = summary(SELECTED),
            )
            runCurrent()

            controller.trafficReading = TrafficStats(rxBytes = 4_096, txBytes = 1_024)
            tick(clock)

            val connection = viewModel.connection()
            assertIs<ConnectionUiState.Connected>(connection)
            assertEquals(4_096, connection.session.rxBytes)
            assertEquals(1_024, connection.session.txBytes)
        }

    @Test
    fun `the duration is read from the connection, not counted up`() = runTest(dispatcher) {
        val clock = MutableClock(Instant.EPOCH)
        val controller = FakeConnectionController()
        val viewModel = viewModel(controller, clock)
        viewModel.state.collectIn(backgroundScope)
        runCurrent()

        // The tunnel has been up for an hour before this screen ever collected anything. A counter
        // would say zero; a subtraction says an hour.
        clock.advance(Duration.ofHours(1))
        controller.emit(
            state = VpnConnectionState.Connected(ProfileId(SELECTED), Instant.EPOCH),
            profile = summary(SELECTED),
        )
        runCurrent()

        assertEquals(SECONDS_PER_HOUR, viewModel.seconds())
    }

    @Test
    fun `disconnecting drops the session and reconnecting starts a new one`() = runTest(dispatcher) {
        val clock = MutableClock(Instant.EPOCH)
        val controller = FakeConnectionController()
        val viewModel = viewModel(controller, clock)
        viewModel.state.collectIn(backgroundScope)
        runCurrent()

        controller.emit(
            state = VpnConnectionState.Connected(ProfileId(SELECTED), Instant.EPOCH),
            profile = summary(SELECTED),
        )
        runCurrent()
        repeat(3) { tick(clock) }
        assertEquals(3L, viewModel.seconds())

        controller.emit(state = VpnConnectionState.Idle, profile = summary(SELECTED))
        advanceUntilIdle()
        assertEquals(ConnectionUiState.Idle, viewModel.connection())

        // A second tunnel, opened now. Its duration starts from its own `since`, not from where the
        // first one left off.
        val secondSince = clock.instant()
        controller.emit(
            state = VpnConnectionState.Connected(ProfileId(SELECTED), secondSince),
            profile = summary(SELECTED),
        )
        runCurrent()

        assertEquals(0L, viewModel.seconds())
    }

    /** One second of wall clock and one second of scheduler time, in that order. */
    private fun TestScope.tick(clock: MutableClock) {
        clock.advance(Duration.ofSeconds(1))
        advanceTimeBy(SECOND_MILLIS)
        runCurrent()
    }

    private fun HomeViewModel.seconds(): Long {
        val connection = connection()
        assertIs<ConnectionUiState.Connected>(connection)
        return connection.session.seconds
    }

    private fun viewModel(
        controller: FakeConnectionController,
        clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
    ) = HomeViewModel(
        controller = controller,
        selected = FakeSelectedProfileStore(ProfileId(SELECTED)),
        // Stored configurations only.
        profiles = FakeProfileRepository(listOf(summary(SELECTED))),
        clock = clock,
    )

    private fun HomeViewModel.connection() = (state.value as HomeUiState.Ready).connection

    private companion object {
        const val SELECTED = "profile-1"
        const val TEN_MINUTES_MILLIS = 600_000L
        const val SECOND_MILLIS = 1_000L
        const val SECONDS_PER_HOUR = 3_600L
    }
}
