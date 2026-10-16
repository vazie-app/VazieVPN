package app.vazie.vpn.core.network

import app.vazie.vpn.core.model.Secret

/** Where the client gets the bearer token, asked once per request. */
fun interface SessionTokenProvider {

    suspend fun token(): Secret<String>?
}
