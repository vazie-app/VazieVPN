package app.vazie.vpn.feature.home

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.ConnectResult
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ProfileDetails
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Doubles for the three things Home now reads. */
internal class FakeConnectionController(
    private val runnable: Boolean = true,
    private val result: ConnectResult = ConnectResult.Started,
) : VpnConnectionController {

    private val _snapshot = MutableStateFlow(VpnConnectionSnapshot())
    override val snapshot: StateFlow<VpnConnectionSnapshot> = _snapshot.asStateFlow()

    var connectCalls: MutableList<ProfileId> = mutableListOf()
        private set
    var disconnectCalls: Int = 0
        private set

    fun emit(snapshot: VpnConnectionSnapshot) {
        _snapshot.value = snapshot
    }

    fun emit(state: VpnConnectionState, profile: ProfileSummary? = null) {
        _snapshot.value = VpnConnectionSnapshot(
            state = state,
            subject = profile?.let(::ConnectionSubject),
        )
    }

    override suspend fun connect(profileId: ProfileId): ConnectResult {
        connectCalls += profileId
        return result
    }

    override suspend fun canRun(profileId: ProfileId): Boolean = runnable

    override suspend fun disconnect() {
        disconnectCalls++
    }

    /** What [traffic] answers. */
    var trafficReading: TrafficStats = TrafficStats.NONE

    override suspend fun traffic(): TrafficStats = trafficReading
}

internal class FakeSelectedProfileStore(initial: ProfileId? = null) : SelectedProfileStore {

    private val state = MutableStateFlow(initial)

    override fun observeSelected(): Flow<ProfileId?> = state

    override suspend fun selected(): ProfileId? = state.value

    override suspend fun select(id: ProfileId) {
        state.value = id
    }

    override suspend fun clear() {
        state.value = null
    }
}

internal class FakeProfileRepository(summaries: List<ProfileSummary> = emptyList()) :
    VpnProfileRepository {

    private val state = MutableStateFlow(summaries)

    override fun observeSummaries(): Flow<List<ProfileSummary>> = state

    override suspend fun create(
        draft: ProfileDraft,
        name: String,
        origin: ProfileOrigin,
    ): ProfileId = error("not used by Home")

    override suspend fun details(id: ProfileId): ProfileDetails? = null

    override suspend fun revealCredential(id: ProfileId): Secret<String>? = null

    override suspend fun profile(id: ProfileId): VpnProfile? = null

    override suspend fun delete(id: ProfileId) = Unit

    override suspend fun rename(id: ProfileId, name: String) {
        state.value = state.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun duplicate(id: ProfileId, name: String): ProfileId? =
        error("Home does not duplicate profiles")


    /** Recorded, because this is the one of the four Home is expected to cause. */
    override suspend fun markUsed(id: ProfileId) {
        marked += id
    }

    val marked = mutableListOf<ProfileId>()
}

/** A clock a test can push forward by hand. */
internal class MutableClock(private var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current

    fun advance(by: Duration) {
        current = current.plus(by)
    }
}

internal fun summary(id: String, name: String = "Relay"): ProfileSummary = ProfileSummary(
    id = ProfileId(id),
    name = name,
    engineId = VpnEngineId.XRAY,
    protocolLabel = "VLESS",
)

