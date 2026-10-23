package app.vazie.vpn.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.ServerEndpoint
import app.vazie.vpn.api.ServerLatencyProbe
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.core.model.ProfileId
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored configurations, as they are. */
@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val profiles: VpnProfileRepository,
    private val selected: SelectedProfileStore,
    controller: VpnConnectionController,
    /** Read once per emission, to bucket each profile's last use. */
    private val clock: Clock,
    private val latencyProbe: ServerLatencyProbe = ServerLatencyProbe.None,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<String?>(null)

    val state: StateFlow<ConnectionsUiState> = combine(
        profiles.observeSummaries(),
        selected.observeSelected(),
        combine(controller.snapshot, latencies()) { snapshot, latency -> snapshot to latency },
        // The unanswered delete question, joined into the one state object.
        pendingDelete,
    ) { summaries, selection, (snapshot, latency), deleting ->
        ConnectionsUiState(
            configurations = summaries.toRows(selection, snapshot, clock.instant(), clock.zone).withLatency(latency),
        ).let { state -> state.copy(deleting = state.configurations.firstOrNull { it.id == deleting }) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = ConnectionsUiState(loading = true),
        )

    private val _effects = Channel<ConnectionsEffect>(Channel.BUFFERED)
    val effects: Flow<ConnectionsEffect> = _effects.receiveAsFlow()

    fun onAction(action: ConnectionsAction) {
        when (action) {
            is ConnectionsAction.SelectConfiguration -> viewModelScope.launch {
                selected.select(ProfileId(action.id))
            }

            is ConnectionsAction.OpenConfiguration ->
                emit(ConnectionsEffect.OpenConfiguration(action.id))

            ConnectionsAction.AddConfiguration -> emit(ConnectionsEffect.OpenAddConfiguration)

            // Asking is not doing. The gesture opens the question; only the dialog's confirm deletes, which
            // is the same contract the details screen has always had for the same act.
            is ConnectionsAction.RequestDelete -> pendingDelete.value = action.id
            ConnectionsAction.DismissDelete -> pendingDelete.value = null
            ConnectionsAction.ConfirmDelete -> viewModelScope.launch {
                val id = pendingDelete.value ?: return@launch
                pendingDelete.value = null
                profiles.delete(ProfileId(id))
            }
        }
    }

    /** Each row's latency in ms (null: no answer), re-measured every [LATENCY_REFRESH_MILLIS] while on
     * screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun latencies(): Flow<Map<String, Long?>> {
        if (latencyProbe === ServerLatencyProbe.None) return flowOf(emptyMap())
        return profiles.observeSummaries()
            .map { summaries -> summaries.map { it.id.value } }
            .distinctUntilChanged()
            .flatMapLatest { targets ->
                flow {
                    while (true) {
                        emit(measure(targets))
                        delay(LATENCY_REFRESH_MILLIS)
                    }
                }
            }
            .onStart { emit(emptyMap()) }
    }

    private suspend fun measure(targets: List<String>): Map<String, Long?> = coroutineScope {
        targets.map { id ->
            async {
                val endpoint = profiles.details(ProfileId(id))?.let { ServerEndpoint(it.endpointHost, it.endpointPort) }
                endpoint?.let { id to latencyProbe.measure(it) }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private fun List<ConfigurationRowUi>.withLatency(latency: Map<String, Long?>): ImmutableList<ConfigurationRowUi> =
        map { row ->
            when {
                row.id !in latency -> row
                else -> row.copy(latency = latency[row.id]?.let { LatencyUi.Answered(it) } ?: LatencyUi.NoAnswer)
            }
        }.toImmutableList()

    private fun emit(effect: ConnectionsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }


    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val LATENCY_REFRESH_MILLIS = 30_000L
    }
}
