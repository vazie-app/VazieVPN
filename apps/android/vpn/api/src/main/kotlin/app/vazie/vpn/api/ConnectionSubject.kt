package app.vazie.vpn.api

/** Whose configuration the connection is to — and in this release there is only one answer. */
data class ConnectionSubject(val profile: ProfileSummary) {

    /** What to call it on screen. */
    val displayName: String get() = profile.name
}
