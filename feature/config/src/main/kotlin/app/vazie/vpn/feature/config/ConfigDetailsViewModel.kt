package app.vazie.vpn.feature.config

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.feature.config.presentation.toUiState
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.config.vless.VlessLinkSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything a person can do to one configuration, and the order the doing happens in. */
@HiltViewModel
class ConfigDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val profiles: VpnProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val profileId = ProfileId(savedStateHandle.toRoute<ConfigDetailsRoute>().configurationId)

    private val _state = MutableStateFlow(ConfigDetailsUiState())
    val state: StateFlow<ConfigDetailsUiState> = _state.asStateFlow()

    private val _effects = Channel<ConfigDetailsEffect>(Channel.BUFFERED)
    val effects: Flow<ConfigDetailsEffect> = _effects.receiveAsFlow()

    init {
        reload()
    }

    fun onAction(action: ConfigDetailsAction) {
        when (action) {
            ConfigDetailsAction.ToggleSecret ->
                if (_state.value.secretRevealed) hideSecret() else revealSecret()

            ConfigDetailsAction.HideSecret -> hideSecret()

            ConfigDetailsAction.RequestRename ->
                _state.update { it.copy(renameDraft = it.name) }

            is ConfigDetailsAction.EditRename ->
                _state.update { it.copy(renameDraft = action.name) }

            ConfigDetailsAction.DismissRename -> _state.update { it.copy(renameDraft = null) }
            ConfigDetailsAction.ConfirmRename -> rename()
            ConfigDetailsAction.Duplicate -> duplicate()

            ConfigDetailsAction.RequestExport -> _state.update { it.copy(confirmingExport = true) }
            ConfigDetailsAction.DismissExport -> _state.update { it.copy(confirmingExport = false) }
            ConfigDetailsAction.ConfirmExport -> export()

            ConfigDetailsAction.RequestDelete -> _state.update { it.copy(confirmingDelete = true) }
            ConfigDetailsAction.DismissDelete -> _state.update { it.copy(confirmingDelete = false) }
            ConfigDetailsAction.ConfirmDelete -> delete()

            ConfigDetailsAction.Back -> emit(ConfigDetailsEffect.Close)
        }
    }

    /** Reads the profile again and rebuilds the screen from it. */
    private fun reload() {
        viewModelScope.launch {
            val details = profiles.details(profileId)
            _state.value = details?.toUiState(now = clock.instant())
                ?: ConfigDetailsUiState(loading = false, missing = true)
        }
    }

    private fun revealSecret() {
        viewModelScope.launch {
            val credential = profiles.revealCredential(profileId) ?: return@launch
            _state.update { it.copy(secret = credential.expose(), secretRevealed = true) }
        }
    }

    private fun hideSecret() = _state.update { it.copy(secret = "", secretRevealed = false) }

    private fun rename() {
        val name = _state.value.renameDraft?.trim().orEmpty()
        if (name.isEmpty()) return
        viewModelScope.launch {
            profiles.rename(profileId, name)
            _state.update { it.copy(renameDraft = null) }
            reload()
        }
    }

    /** A copy, named for what it is. */
    private fun duplicate() {
        val name = _state.value.name
        if (name.isEmpty()) return
        viewModelScope.launch {
            profiles.duplicate(profileId, context.getString(R.string.config_duplicate_name, name))
            _effects.send(ConfigDetailsEffect.Duplicated)
        }
    }

    /** Builds the share link and hands it over, once. */
    private fun export() {
        viewModelScope.launch {
            _state.update { it.copy(confirmingExport = false) }
            val profile = profiles.profile(profileId) as? VpnProfile.Xray ?: return@launch
            val outbound = profile.outbound as? XrayOutbound.Vless ?: return@launch
            _effects.send(
                ConfigDetailsEffect.Share(
                    VlessLinkSerializer.serialize(outbound = outbound, name = profile.name),
                )
            )
        }
    }

    private fun delete() {
        viewModelScope.launch {
            profiles.delete(profileId)
            _effects.send(ConfigDetailsEffect.Close)
        }
    }

    private fun emit(effect: ConfigDetailsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
