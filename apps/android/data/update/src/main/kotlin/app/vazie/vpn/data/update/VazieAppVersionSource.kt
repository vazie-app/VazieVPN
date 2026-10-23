package app.vazie.vpn.data.update

import app.vazie.vpn.core.network.ApiFailure
import app.vazie.vpn.core.network.ApiResult
import app.vazie.vpn.core.network.VazieApiClient
import app.vazie.vpn.update.api.AppVersionPolicy
import app.vazie.vpn.update.api.AppVersionSource
import app.vazie.vpn.update.api.VersionAnswer
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** `GET /apps/vpn/android/version` on the shared API, through a client that carries no session. Anything short of
 * a valid policy — a timeout, an error page, a minimum above the latest, an `http` or deep-link URL — is
 * [VersionAnswer.Failed], which blocks nobody. Only the backend's own `NOT_ENABLED` means "nothing published". */
internal class VazieAppVersionSource(
    private val api: VazieApiClient,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : AppVersionSource {

    override suspend fun fetch(): VersionAnswer {
        val result = withTimeoutOrNull(timeoutMillis) { api.get(PATH, AppVersionDto.serializer()) }
            ?: return VersionAnswer.Failed
        return when (result) {
            is ApiResult.Success -> result.value.toPolicy()?.let(VersionAnswer::Published) ?: VersionAnswer.Failed
            is ApiResult.Failure -> {
                val failure = result.failure
                if (failure is ApiFailure.Http && failure.status == STATUS_NOT_FOUND && failure.code == CODE_NOT_ENABLED) {
                    VersionAnswer.NotPublished
                } else {
                    VersionAnswer.Failed
                }
            }
        }
    }

    private companion object {
        const val PATH = "/apps/vpn/android/version"
        const val STATUS_NOT_FOUND = 404
        const val CODE_NOT_ENABLED = "NOT_ENABLED"

        /** Short: a check is background work, and a slow backend must not keep a "checking" row spinning. */
        const val TIMEOUT_MILLIS = 8_000L
    }
}

/** The backend's `AppVersionResponse`, every field optional so a bad answer is refused by [toPolicy], not thrown. */
@Serializable
internal data class AppVersionDto(
    val latestVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val minimumSupportedVersionCode: Int? = null,
    val updateUrl: String? = null,
    val message: Map<String, String>? = null,
) {
    fun toPolicy(): AppVersionPolicy? = AppVersionPolicy.create(
        latestVersionCode = latestVersionCode,
        latestVersionName = latestVersionName,
        minimumSupportedVersionCode = minimumSupportedVersionCode,
        updateUrl = updateUrl,
        messages = message,
    )
}
