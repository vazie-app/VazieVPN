package app.vazie.vpn.update.api

import java.net.URI

/** What the backend publishes about this app's releases (`GET /v1/apps/vpn/android/version`). Compared by
 * `versionCode` only; [latestVersionName] is for display and never compared. Valid by construction — use
 * [create] for anything read from outside. */
data class AppVersionPolicy(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minimumSupportedVersionCode: Int,
    /** An `https` page to update from; see [UpdateUrl]. */
    val updateUrl: String,
    /** Wording by language (`ru`, `en`); may be empty. */
    val messages: Map<String, String> = emptyMap(),
) {

    init {
        require(latestVersionCode > 0) { "latestVersionCode is positive" }
        require(minimumSupportedVersionCode > 0) { "minimumSupportedVersionCode is positive" }
        require(minimumSupportedVersionCode <= latestVersionCode) { "the minimum is not above the latest" }
        require(latestVersionName.isNotBlank() && latestVersionName.length <= MAX_NAME) { "a version name to show" }
        require(UpdateUrl.isSafe(updateUrl)) { "an https update URL" }
    }

    /**
     * - installed `<` [minimumSupportedVersionCode] — [UpdateStatus.Required];
     * - installed `<` [latestVersionCode] — [UpdateStatus.Optional];
     * - otherwise [UpdateStatus.UpToDate], a build newer than the latest included.
     */
    fun statusFor(installedVersionCode: Int): UpdateStatus = when {
        installedVersionCode < minimumSupportedVersionCode -> UpdateStatus.Required(this)
        installedVersionCode < latestVersionCode -> UpdateStatus.Optional(this)
        else -> UpdateStatus.UpToDate
    }

    /** The backend's wording in [language], if it gave one; the app has its own otherwise. */
    fun message(language: String): String? = messages[language.lowercase()]

    override fun toString(): String =
        "AppVersionPolicy(latest=$latestVersionCode/$latestVersionName, minimum=$minimumSupportedVersionCode)"

    companion object {
        private const val MAX_NAME = 32
        private const val MAX_MESSAGE = 300

        /** A policy from values read off the wire or a file, or `null` when they do not make one: a missing or
         * non-positive code, a minimum above the latest, an update URL that is not a plain `https` page. Messages
         * that are not short plain text are dropped, not trusted. */
        fun create(
            latestVersionCode: Int?,
            latestVersionName: String?,
            minimumSupportedVersionCode: Int?,
            updateUrl: String?,
            messages: Map<String, String>?,
        ): AppVersionPolicy? {
            if (latestVersionCode == null || minimumSupportedVersionCode == null) return null
            if (latestVersionCode <= 0 || minimumSupportedVersionCode <= 0) return null
            if (minimumSupportedVersionCode > latestVersionCode) return null
            val name = latestVersionName?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME } ?: return null
            if (updateUrl == null || !UpdateUrl.isSafe(updateUrl)) return null
            val wording = messages.orEmpty()
                .mapKeys { it.key.lowercase() }
                .filter { (language, text) ->
                    language.length in 2..3 && text.isNotBlank() && text.length <= MAX_MESSAGE && text.none(Char::isISOControl)
                }
            return AppVersionPolicy(latestVersionCode, name, minimumSupportedVersionCode, updateUrl, wording)
        }
    }
}

/** Where this build stands against an [AppVersionPolicy]. */
sealed interface UpdateStatus {

    data object UpToDate : UpdateStatus

    /** A newer build exists; this one is still supported. */
    data class Optional(val policy: AppVersionPolicy) : UpdateStatus

    /** This build is below the minimum supported: the app stops until it is updated. */
    data class Required(val policy: AppVersionPolicy) : UpdateStatus
}

/** The one kind of link an update may send the person to: a plain `https` page with a host. Never `http`, a deep
 * link, an `intent:` URI, a `market:` link or anything else the backend could name — the app opens it in a browser
 * and nowhere else. */
object UpdateUrl {

    private const val MAX_LENGTH = 512

    fun isSafe(url: String): Boolean {
        if (url.length > MAX_LENGTH || url.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.isAbsolute && !uri.isOpaque
    }
}
