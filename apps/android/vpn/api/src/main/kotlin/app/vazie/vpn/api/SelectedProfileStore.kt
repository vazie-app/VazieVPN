package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import kotlinx.coroutines.flow.Flow

/** Which stored profile the Connect button would connect to. */
interface SelectedProfileStore {

    /** The selected profile id, or `null` when nothing is selected or the selection is stale. */
    fun observeSelected(): Flow<ProfileId?>

    /** The selected profile id right now. */
    suspend fun selected(): ProfileId?

    /** Makes [id] the selection. */
    suspend fun select(id: ProfileId)

    /** Leaves nothing selected. */
    suspend fun clear()
}
