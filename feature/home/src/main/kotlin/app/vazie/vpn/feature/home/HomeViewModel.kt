package app.vazie.vpn.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectResult
import app.vazie.vpn.api.connectionTicks
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Home's state, read from the tunnel rather than performed for it. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val controller: VpnConnectionController,
    private val selected: SelectedProfileStore,
    profiles: VpnProfileRepository,
    /** Injected now, where it used to be a private field. */
    private val clock: Clock,
) : ViewModel() {

    /** Whether the selected profile is runnable; asked once per selection. */
    private val runnable = MutableStateFlow(true)

    /** Why Vazie declined, when it did. */

    // `now` and traffic come from one tick flow, so the timer never freezes. See `connectionTicks`.
    val state: StateFlow<HomeUiState> = combine(
        controller.snapshot,
        selected.observeSelected(),
        profiles.observeSummaries(),
        runnable,
        connectionTicks(controller.snapshot, clock).map { now -> now to controller.traffic() },
    ) { snapshot, selection, summaries, canRun, (now, traffic) ->
        homeState(
            snapshot = snapshot,
            selection = selection,
            summaries = summaries,
            runnable = canRun,
            now = now,
            traffic = traffic,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = HomeUiState.Empty,
    )

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            selected.observeSelected().collect { id ->
                runnable.value = id != null && controller.canRun(id)
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Connect,
            HomeAction.Retry,
            HomeAction.CheckConnection,
            -> connect()
            is HomeAction.ConnectTo -> connect(ProfileId(action.profileId))

            HomeAction.Cancel, HomeAction.Disconnect -> disconnect()
            HomeAction.AddConfiguration -> emit(HomeEffect.OpenAddConfiguration)
            HomeAction.ShowConnectionDetails -> emit(HomeEffect.OpenConnectionDetails)
            HomeAction.OpenSettings -> emit(HomeEffect.OpenSettings)
        }
    }

    /** Called after the system consent dialog came back with a yes. A no needs no call: the runtime never
     * left `Idle`, so there is nothing to undo. */
    fun onVpnPermissionGranted() {
        connect()
    }


    /** Connects to [target], or the selection when null; naming a target (a shortcut) selects it first. */
    private fun connect(target: ProfileId? = null) {
        viewModelScope.launch {
            // A shortcut's target, otherwise the user's last choice.
            val id: ProfileId = target ?: selected.selected() ?: return@launch
            if (target != null) selected.select(target)
            when (controller.connect(id)) {
                ConnectResult.PermissionRequired -> _effects.send(HomeEffect.RequestVpnPermission)
                ConnectResult.Started, is ConnectResult.Rejected -> Unit
            }
        }
    }


    private fun disconnect() {
        viewModelScope.launch {
            controller.disconnect()
        }
    }

    private fun emit(effect: HomeEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
