package app.vazie.vpn.core.model

/** What a guide is *about*, and nothing about how it behaves. */
enum class VazieGuideId(val id: String, val kind: VazieGuideKind) {

    /** Vazie can live in the quick-settings panel. */
    QUICK_SETTINGS("quick_settings", VazieGuideKind.SETUP),

    /** Holding the launcher icon offers the configurations directly. Nothing to install. */
    LAUNCHER_SHORTCUTS("launcher_shortcuts", VazieGuideKind.USAGE),

    /** Vazie has home-screen widgets. */
    WIDGETS("widgets", VazieGuideKind.SETUP),

    /** The tunnel can be visible in the status bar, with a Disconnect button on it. */
    NOTIFICATION("notification", VazieGuideKind.SETUP),
    ;

    companion object {
        /** Unknown ids are ignored: a preference file is not a trusted enum. */
        fun fromId(id: String): VazieGuideId? = entries.firstOrNull { it.id == id }
    }
}

/** See [VazieGuideId] for the distinction and for what it deliberately does not govern. */
enum class VazieGuideKind {
    SETUP,
    USAGE,
    ;

    /** Whether a guide of this kind may offer a button that asks Android for something. */
    val allowsPlatformAction: Boolean get() = this == SETUP
}
