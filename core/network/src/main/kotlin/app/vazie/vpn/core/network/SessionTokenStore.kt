package app.vazie.vpn.core.network

import app.vazie.vpn.core.model.Secret

/** The one place a Vazie session token is kept, and the only thing that can hand one out. */
interface SessionTokenStore : SessionTokenProvider {

    /** Replaces whatever was there. */
    suspend fun store(token: Secret<String>)

    /** Forgets the token. */
    suspend fun clear()
}
