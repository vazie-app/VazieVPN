package app.vazie.vpn.core.network

/** What one API call produced. */
sealed interface ApiResult<out T> {

    val requestId: String?

    data class Success<out T>(val value: T, override val requestId: String?) : ApiResult<T>

    data class Failure(val failure: ApiFailure) : ApiResult<Nothing> {
        override val requestId: String? get() = failure.requestId
    }
}

/** Why one API call did not produce a value. */
sealed interface ApiFailure {

    val requestId: String?

    /** The service answered, and the answer was a refusal. */
    data class Http(
        val status: Int,
        val code: String,
        override val requestId: String?,
        val retryAfterSeconds: Long? = null,
    ) : ApiFailure

    /** Nothing answered: no route, no name, no socket, or nothing within the timeout. */
    data class Unreachable(override val requestId: String? = null) : ApiFailure

    /** Something answered and it was not the contract: not JSON, the wrong shape, or larger than this client
     * will read. Distinct from [Unreachable] because retrying fixes one and not the other. */
    data class Unreadable(override val requestId: String?) : ApiFailure

    companion object {
        /** Stands in when a refusal carried no readable `error.code`. */
        const val UNKNOWN_CODE: String = "UNKNOWN"
    }
}
