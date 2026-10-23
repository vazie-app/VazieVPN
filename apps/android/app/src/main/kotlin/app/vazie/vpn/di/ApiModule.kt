package app.vazie.vpn.di

import app.vazie.vpn.BuildConfig
import app.vazie.vpn.core.network.ApiBaseUrl

/** The shared Vazie API: the public version and links endpoints, read without a session. */
internal object VazieApi {
    val SHARED_BASE_URL = ApiBaseUrl(BuildConfig.SHARED_API_URL)
}
