package app.vazie.vpn.data

import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.SplitTunnel
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.core.model.VazieGuideId
import kotlinx.coroutines.flow.Flow

/** The handful of choices Vazie has to remember between launches. */
interface AppPreferences {

    /** The stored choices, right now, without suspending. */
    fun snapshot(): AppPreferencesState

    /** The stored choices, and every change to them. */
    fun observe(): Flow<AppPreferencesState>

    /** Record that onboarding is behind us, and that the tour is owed. */
    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Record what became of the first-run tour: finished, or skipped. */
    suspend fun setTourDone()

    /** Record that the configuration coach mark has been shown. */
    suspend fun setConfigurationGuidanceSeen()

    /** The Settings tour has been shown. */
    suspend fun setSettingsGuidanceSeen()

    suspend fun setAppIcon(icon: VazieAppIcon)

    /** Remember which field the launcher mark stands on. Stored separately from the mark because it is a
     * separate choice; a plate the mark does not offer resolves to its signature on read. */
    suspend fun setAppIconPlate(plate: VazieAppIconPlate)

    suspend fun setAppearance(appearance: Appearance)

    /** Stop proactively nudging about a guide. */
    suspend fun acknowledgeGuide(guide: VazieGuideId)

    /** Remember that Vazie has explained the status notification, whatever came of it. */
    suspend fun setNotificationExplained(explained: Boolean)

    /** Which apps use the tunnel from the next connection on. */
    suspend fun setSplitTunnel(split: SplitTunnel)

}

/** Everything [AppPreferences] remembers, as one value. */
data class AppPreferencesState(
    val onboardingCompleted: Boolean = false,
    val appIcon: VazieAppIcon = VazieAppIcon.Default,
    val appIconPlate: VazieAppIconPlate = VazieAppIconPlate.Default,
    val appearance: Appearance = Appearance.Default,
    /** The guides a person has said they are done being nudged about. */
    val acknowledgedGuides: Set<VazieGuideId> = emptySet(),
    /** Whether Vazie has ever explained the status notification. See
     * [AppPreferences.setNotificationExplained] for why this is one boolean and not four states. */
    val notificationExplained: Boolean = false,
    /** Whether the first-run tour is still owed. */
    val tourState: TourState = TourState.DONE,
    /** Whether the configuration coach mark has been shown. See
     * [AppPreferences.setConfigurationGuidanceSeen]. */
    val configurationGuidanceSeen: Boolean = false,
    /** Whether the Settings tour has been shown. */
    val settingsGuidanceSeen: Boolean = false,
    /** Which apps use the tunnel. */
    val splitTunnel: SplitTunnel = SplitTunnel(),
) {
    fun isAcknowledged(guide: VazieGuideId): Boolean = guide in acknowledgedGuides
}

enum class TourState { PENDING, DONE }
