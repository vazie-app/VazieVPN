package app.vazie.vpn.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vazie.vpn.update.api.AppUpdateChecker
import app.vazie.vpn.update.api.AppVersionPolicy
import app.vazie.vpn.update.api.ManualCheckResult
import app.vazie.vpn.update.api.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the update layer wants on screen. */
data class AppUpdateUiState(
    /** [UpdateStatus.Required] blocks the app behind [UpdateRequiredScreen]. */
    val status: UpdateStatus = UpdateStatus.UpToDate,
    /** A non-blocking message to show over the app, if any. Never set while the update is required. */
    val prompt: UpdatePrompt? = null,
    /** A check is under way (automatic or asked for). */
    val checking: Boolean = false,
    /** On the required screen: the last "check again" could not reach the backend, so the requirement is only
     * remembered, not re-confirmed. */
    val couldNotVerify: Boolean = false,
)

sealed interface UpdatePrompt {
    /** A newer build exists. Can be postponed. */
    data class Available(val policy: AppVersionPolicy) : UpdatePrompt

    /** The person asked, and this is the newest supported build. */
    data object UpToDate : UpdatePrompt

    /** The person asked, and the backend could not be reached. */
    data object CheckFailed : UpdatePrompt
}

/** Runs the update checks and turns their results into what [UpdateGate] shows. Checks happen off the main
 * thread and never delay a frame, the tunnel or a connect. */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checker: AppUpdateChecker,
) : ViewModel() {

    private val manual = MutableStateFlow<UpdatePrompt?>(null)
    private val couldNotVerify = MutableStateFlow(false)

    val state: StateFlow<AppUpdateUiState> = combine(checker.state, manual, couldNotVerify) { update, manualPrompt, unverified ->
        val required = update.status is UpdateStatus.Required
        val prompt = when {
            required -> null
            manualPrompt != null -> manualPrompt
            update.offerOptional -> (update.status as? UpdateStatus.Optional)?.let { UpdatePrompt.Available(it.policy) }
            else -> null
        }
        AppUpdateUiState(update.status, prompt, update.checking, couldNotVerify = required && unverified)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUpdateUiState(checker.state.value.status))

    /** The app is in front: a cold start, or coming back. The checker decides whether a check is due. */
    fun onForeground() {
        viewModelScope.launch { checker.onForeground() }
    }

    /** Settings' "Check for updates", and the required screen's "Check again". */
    fun onCheckNow() {
        viewModelScope.launch {
            manual.value = null
            couldNotVerify.value = false
            when (val result = checker.checkNow()) {
                ManualCheckResult.UpToDate -> manual.value = UpdatePrompt.UpToDate
                ManualCheckResult.Failed -> manual.value = UpdatePrompt.CheckFailed
                is ManualCheckResult.UpdateAvailable -> manual.value = UpdatePrompt.Available(result.policy)
                is ManualCheckResult.UpdateRequired -> couldNotVerify.value = !result.verified
            }
        }
    }

    /** "Later", or dismissing the prompt: an available update is not offered again for a while. */
    fun onPostpone() {
        val prompt = manual.value
        manual.value = null
        if (prompt !is UpdatePrompt.UpToDate && prompt !is UpdatePrompt.CheckFailed) {
            viewModelScope.launch { checker.postpone() }
        }
    }

    /** Closes a prompt that is only information. */
    fun onDismissPrompt() {
        manual.update { null }
    }
}
