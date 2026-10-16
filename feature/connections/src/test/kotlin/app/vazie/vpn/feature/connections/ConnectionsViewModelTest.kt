package app.vazie.vpn.feature.connections

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.ProfileDetails
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.ConnectResult
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.XraySecurityKind
import app.vazie.vpn.api.XrayTransportKind
import app.vazie.vpn.api.ServerLatencyProbe
import app.vazie.vpn.api.ServerEndpoint
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The list over a repository double. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val summaries = MutableStateFlow(emptyList<ProfileSummary>())
    private val selection = FakeSelectedProfileStore()
    private val controller = FakeConnectionController()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an empty store is an empty list, not a fixture`() = runTest(dispatcher) {
        val viewModel = viewModel()

        subscribe(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.configurations.isEmpty())
        assertTrue(viewModel.state.value.isEmpty)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `before the store has answered, the screen does not claim to be empty`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            assertTrue(viewModel.state.value.loading)
            assertFalse(viewModel.state.value.isEmpty, "the empty state was shown while loading")
        }

    @Test
    fun `a stored profile becomes a row with its mark and an idle state`() = runTest(dispatcher) {
        val viewModel = viewModel()
        subscribe(viewModel)

        summaries.value = listOf(summary("stored-0", "Home relay"))
        advanceUntilIdle()

        val row = viewModel.state.value.configurations.single()
        assertEquals("stored-0", row.id)
        assertEquals("Home relay", row.name)
        assertEquals("VL", row.mark)
        assertEquals("VLESS", row.protocolLabel)
        assertEquals(
            ConfigurationStatusUi.IDLE,
            row.status,
            "a profile that is neither selected nor connected is idle",
        )
    }

    @Test
    fun `the selected profile is marked, and only that one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        subscribe(viewModel)
        summaries.value = listOf(summary("stored-0", "Home relay"), summary("stored-1", "Travel"))

        selection.select(ProfileId("stored-1"))
        advanceUntilIdle()

        assertEquals(
            listOf(ConfigurationStatusUi.IDLE, ConfigurationStatusUi.SELECTED),
            viewModel.state.value.configurations.map { it.status },
        )
    }

    @Test
    fun `tapping a row selects it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        subscribe(viewModel)
        summaries.value = listOf(summary("stored-0", "Home relay"))
        advanceUntilIdle()

        viewModel.onAction(ConnectionsAction.SelectConfiguration("stored-0"))
        advanceUntilIdle()

        assertEquals(ProfileId("stored-0"), selection.selected())
        assertEquals(
            ConfigurationStatusUi.SELECTED,
            viewModel.state.value.configurations.single().status,
        )
    }

    @Test
    fun `the connected profile outranks the selected one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        subscribe(viewModel)
        summaries.value = listOf(summary("stored-0", "Home relay"), summary("stored-1", "Travel"))
        selection.select(ProfileId("stored-1"))
        controller.emit(
            VpnConnectionState.Connected(ProfileId("stored-0"), Instant.EPOCH)
        )
        advanceUntilIdle()

        assertEquals(
            listOf(ConfigurationStatusUi.CONNECTED, ConfigurationStatusUi.SELECTED),
            viewModel.state.value.configurations.map { it.status },
        )
    }

    @Test
    fun `a tunnel that is merely connecting marks nothing as connected`() = runTest(dispatcher) {
        val viewModel = viewModel()
        subscribe(viewModel)
        summaries.value = listOf(summary("stored-0", "Home relay"))
        controller.emit(VpnConnectionState.Connecting(ProfileId("stored-0")))
        advanceUntilIdle()

        assertEquals(
            ConfigurationStatusUi.IDLE,
            viewModel.state.value.configurations.single().status,
        )
    }

    @Test
    fun `saving another profile updates the list without anyone asking`() = runTest(dispatcher) {
        val viewModel = viewModel()
        subscribe(viewModel)
        summaries.value = listOf(summary("stored-0", "Home relay"))
        advanceUntilIdle()

        summaries.value = summaries.value + summary("stored-1", "Travel relay")
        advanceUntilIdle()

        assertEquals(
            listOf("Home relay", "Travel relay"),
            viewModel.state.value.configurations.map { it.name },
        )
    }

    @Test
    fun `opening a row carries its local id and nothing else`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(ConnectionsAction.OpenConfiguration("stored-0"))

        assertEquals(
            ConnectionsEffect.OpenConfiguration("stored-0"),
            viewModel.effects.first(),
        )
    }

    @Test
    fun `a row carries no field that could hold a secret`() = runTest(dispatcher) {
        val viewModel = viewModel()
        subscribe(viewModel)
        summaries.value = listOf(summary("stored-0", "Home relay"))
        advanceUntilIdle()

        val rendered = viewModel.state.value.configurations.single().toString()
        assertFalse(rendered.contains("example.net"), "an endpoint reached a list row")
    }

    @Test
    fun `a configuration shows how fast its server answered, or that it did not`() = runTest(dispatcher) {
        summaries.value = listOf(summary("fast", "Fast"), summary("silent", "Silent"))
        val answers = mapOf("203.0.113.10" to 84L, "203.0.113.11" to null)
        val viewModel = ConnectionsViewModel(
            profiles = FakeProfileRepository(
                summaries,
                endpoints = mapOf("fast" to ServerEndpoint("203.0.113.10", 443), "silent" to ServerEndpoint("203.0.113.11", 443)),
            ),
            selected = selection,
            controller = controller,
            clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC),
            latencyProbe = ServerLatencyProbe { answers[it.host] },
        )
        subscribe(viewModel)
        // Not advanceUntilIdle: the check repeats on a timer, and virtual time would run it
        // forever.
        runCurrent()

        val latency = viewModel.state.value.configurations.associate { it.id to it.latency }
        assertEquals(LatencyUi.Answered(84), latency.getValue("fast"))
        assertEquals(4, (latency.getValue("fast") as LatencyUi.Answered).bars)
        assertEquals(LatencyUi.NoAnswer, latency.getValue("silent"))
    }

    /** `WhileSubscribed` means the list is only alive while a screen is looking at it, so a test that wants
     * values has to look too. */
    private fun TestScope.subscribe(viewModel: ConnectionsViewModel) {
        backgroundScope.launch { viewModel.state.collect { } }
    }

    private fun viewModel() = ConnectionsViewModel(
        profiles = FakeProfileRepository(summaries),
        selected = selection,
        controller = controller,
        // Fixed, and in UTC. The list buckets each profile's last use against this, and a bucket
        // decided by the machine the test runs on is a test that fails once a day somewhere.
        clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC),
    )

    private fun summary(id: String, name: String) = ProfileSummary(
        id = ProfileId(id),
        name = name,
        engineId = VpnEngineId.XRAY,
        protocolLabel = "VLESS",
    )
}

/** A repository that only answers the one question the list asks. Everything else fails loudly: if the list
 * ever starts reading a profile or a credential, this is where it is noticed. */
private class FakeProfileRepository(
    private val summaries: MutableStateFlow<List<ProfileSummary>>,
    /** Where each profile's server listens, for the latency check; the only detail the list reads. */
    private val endpoints: Map<String, ServerEndpoint> = emptyMap(),
) : VpnProfileRepository {

    override fun observeSummaries(): Flow<List<ProfileSummary>> = summaries

    override suspend fun create(draft: ProfileDraft, name: String, origin: ProfileOrigin): ProfileId =
        error("the configuration list does not create profiles")

    override suspend fun details(id: ProfileId): ProfileDetails? = endpoints[id.value]?.let { endpoint ->
        ProfileDetails(
            id = id,
            name = id.value,
            engineId = VpnEngineId.XRAY,
            protocolLabel = "VLESS",
            origin = ProfileOrigin.IMPORTED_LINK,
            security = XraySecurityKind.REALITY,
            transport = XrayTransportKind.TCP,
            endpointHost = endpoint.host,
            endpointPort = endpoint.port,
            serverName = null,
            fingerprint = null,
            flow = null,
            transportDetail = null,
            unknownParameterNames = emptyList(),
            createdAt = Instant.EPOCH,
            lastUsedAt = null,
        )
    }

    override suspend fun revealCredential(id: ProfileId): Secret<String>? =
        error("the configuration list does not read credentials")

    override suspend fun profile(id: ProfileId): VpnProfile? =
        error("the configuration list does not read whole profiles")

    override suspend fun delete(id: ProfileId): Unit =
        error("the configuration list does not delete profiles")

    override suspend fun rename(id: ProfileId, name: String): Unit =
        error("the configuration list does not rename profiles")

    override suspend fun duplicate(id: ProfileId, name: String): ProfileId? =
        error("the configuration list does not duplicate profiles")


    override suspend fun markUsed(id: ProfileId): Unit =
        error("only a successful connection marks a profile as used")
}

/** Selection, in memory. */
private class FakeSelectedProfileStore : SelectedProfileStore {

    private val state = MutableStateFlow<ProfileId?>(null)

    override fun observeSelected(): Flow<ProfileId?> = state

    override suspend fun selected(): ProfileId? = state.value

    override suspend fun select(id: ProfileId) {
        state.value = id
    }

    override suspend fun clear() {
        state.value = null
    }
}

/** A read-only controller: the list must never start or stop a tunnel. */
private class FakeConnectionController : VpnConnectionController {

    private val _snapshot = MutableStateFlow(VpnConnectionSnapshot())
    override val snapshot: StateFlow<VpnConnectionSnapshot> = _snapshot.asStateFlow()

    fun emit(state: VpnConnectionState) {
        _snapshot.value = VpnConnectionSnapshot(state = state)
    }

    override suspend fun connect(profileId: ProfileId): ConnectResult =
        error("the configuration list does not connect")

    override suspend fun canRun(profileId: ProfileId): Boolean = true

    override suspend fun disconnect(): Unit =
        error("the configuration list does not disconnect")

    override suspend fun traffic(): TrafficStats =
        error("the configuration list shows no traffic")
}
