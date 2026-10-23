package app.vazie.vpn.connection

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectResult
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.VpnConnectionController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Connect, for the controls that live outside the app. */
@Singleton
class VazieConnectionLauncher @Inject constructor(
    private val controller: VpnConnectionController,
    private val profiles: SelectedProfileStore,
) {

    /** The configuration Connect would use, or `null` when nothing is selected. */
    suspend fun selected(): ProfileId? = profiles.selected()

    /** The same answer, and every change to it. */
    fun observeSelected(): Flow<ProfileId?> = profiles.observeSelected()

    suspend fun connect(): LaunchOutcome {
        val id = selected() ?: return LaunchOutcome.NothingSelected
        return controller.connect(id).toOutcome()
    }

    suspend fun disconnect() = controller.disconnect()
}

/** What a surface with no room to explain can act on. */
sealed interface LaunchOutcome {
    data object Started : LaunchOutcome
    data object PermissionRequired : LaunchOutcome
    data object NothingSelected : LaunchOutcome
    data object Refused : LaunchOutcome
}

/** A rejection is reported as a refusal rather than swallowed as a start. */
private fun ConnectResult.toOutcome(): LaunchOutcome = when (this) {
    ConnectResult.Started -> LaunchOutcome.Started
    ConnectResult.PermissionRequired -> LaunchOutcome.PermissionRequired
    is ConnectResult.Rejected -> LaunchOutcome.Refused
}
