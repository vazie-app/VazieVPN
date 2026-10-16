package app.vazie.vpn.core.network

import kotlinx.serialization.Serializable

/** The one shape every failing backend response has. */
@Serializable
internal data class ApiErrorEnvelope(val error: ApiErrorBody)

@Serializable
internal data class ApiErrorBody(val code: String, val requestId: String? = null)
