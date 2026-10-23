package app.vazie.vpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.SplitTunnel
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.core.model.VazieGuideId
import app.vazie.vpn.data.AppPreferences
import app.vazie.vpn.data.AppPreferencesState
import app.vazie.vpn.data.TourState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The choices the shell has to know before it can draw anything: the theme, the mode, and whether onboarding
 * is done. */
@HiltViewModel
class VazieAppViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : ViewModel() {

    val state: StateFlow<VazieAppUiState> = preferences.observe()
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferences.snapshot().toUiState(),
        )

    /** Called when onboarding is left in either of its two ways — finished or skipped. */
    fun onOnboardingFinished() {
        viewModelScope.launch { preferences.setOnboardingCompleted(true) }
    }

    fun onAppearanceSelected(appearance: Appearance) {
        viewModelScope.launch { preferences.setAppearance(appearance) }
    }

    /** Persist the choice, and let the reconciler in `VazieStartup` apply it. */
    fun onAppIconSelected(icon: VazieAppIcon) {
        viewModelScope.launch {
            preferences.setAppIcon(icon)
            preferences.setAppIconPlate(icon.signaturePlate)
        }
    }

    /** Store the plate; applying it is the same reconciler's job, so there is one answer to "which alias is
     * enabled". */
    fun onAppIconPlateSelected(plate: VazieAppIconPlate) {
        viewModelScope.launch { preferences.setAppIconPlate(plate) }
    }

    fun onSplitTunnelChanged(split: SplitTunnel) {
        viewModelScope.launch { preferences.setSplitTunnel(split) }
    }

    fun onGuideAcknowledged(guide: VazieGuideId) {
        viewModelScope.launch { preferences.acknowledgeGuide(guide) }
    }

    /** Vazie has now explained the status notification, whichever button was pressed. */
    fun onNotificationExplained() {
        viewModelScope.launch { preferences.setNotificationExplained(true) }
    }

    /** The automatic tour is over, whether it was finished or skipped. */
    fun onTourFinished() {
        viewModelScope.launch { preferences.setTourDone() }
    }

    /** The configuration coach mark has been shown, whether it was read or dismissed. */
    fun onConfigurationGuidanceSeen() {
        viewModelScope.launch { preferences.setConfigurationGuidanceSeen() }
    }

    fun onSettingsGuidanceSeen() {
        viewModelScope.launch { preferences.setSettingsGuidanceSeen() }
    }
}

/** What the shell reads. Deliberately tiny — this is not a place for screen state. */
data class VazieAppUiState(
    val onboardingCompleted: Boolean,
    val appIcon: VazieAppIcon,
    val appIconPlate: VazieAppIconPlate,
    val appearance: Appearance,
    /** Everything a person has said they are done being nudged about. */
    val acknowledgedGuides: Set<VazieGuideId>,
    /** Whether the status notification has ever been explained. Read by the gate in front of Home's connect
     * controls; see `NotificationReadiness`. */
    val notificationExplained: Boolean,
    /** Whether the first-run tour is still owed. See `AppPreferencesState.tourState`, where the default
     * carries the whole migration. */
    val tourState: TourState,
    val configurationGuidanceSeen: Boolean,
    val settingsGuidanceSeen: Boolean,
    /** Which apps use the tunnel. */
    val splitTunnel: SplitTunnel = SplitTunnel(),
)

private fun AppPreferencesState.toUiState() = VazieAppUiState(
    onboardingCompleted = onboardingCompleted,
    appIcon = appIcon,
    appIconPlate = appIconPlate,
    appearance = appearance,
    splitTunnel = splitTunnel,
    acknowledgedGuides = acknowledgedGuides,
    notificationExplained = notificationExplained,
    tourState = tourState,
    configurationGuidanceSeen = configurationGuidanceSeen,
    settingsGuidanceSeen = settingsGuidanceSeen,
)
