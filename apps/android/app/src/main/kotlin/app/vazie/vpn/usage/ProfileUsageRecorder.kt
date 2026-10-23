package app.vazie.vpn.usage

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The only thing in Vazie that writes "last used". */
@Singleton
class ProfileUsageRecorder @Inject constructor(
    private val controller: VpnConnectionController,
    private val profiles: VpnProfileRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            usedProfiles(controller.snapshot).collect { id -> profiles.markUsed(id) }
        }
    }
}

/** The profiles that have just come up, one value per connection. */
internal fun usedProfiles(snapshot: Flow<VpnConnectionSnapshot>): Flow<ProfileId> = snapshot
    .map { (it.state as? VpnConnectionState.Connected)?.profileId }
    .distinctUntilChanged()
    .filterNotNull()
