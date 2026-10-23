package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import kotlinx.coroutines.flow.Flow

/** The one way anything above the data layer reaches stored profiles. */
interface VpnProfileRepository {

    /** Every stored profile, newest first, updated whenever the store changes. */
    fun observeSummaries(): Flow<List<ProfileSummary>>

    /** Stores [draft] under a freshly generated id and returns it. */
    suspend fun create(draft: ProfileDraft, name: String, origin: ProfileOrigin): ProfileId


    /** The safe projection of one profile, or `null` when nothing is stored under [id]. */
    /** Change what a configuration is called, and nothing else. */
    suspend fun rename(id: ProfileId, name: String)

    /** Copy a configuration's settings into a new one. */
    suspend fun duplicate(id: ProfileId, name: String): ProfileId?

    /** Record that this configuration just carried traffic. */
    suspend fun markUsed(id: ProfileId)

    suspend fun details(id: ProfileId): ProfileDetails?

    /** The profile's credential, decrypted for the caller and nothing else. */
    suspend fun revealCredential(id: ProfileId): Secret<String>?

    /** The whole profile, secrets included. */
    suspend fun profile(id: ProfileId): VpnProfile?

    /** Removes the profile permanently. Does nothing when [id] is unknown. */
    suspend fun delete(id: ProfileId)
}
