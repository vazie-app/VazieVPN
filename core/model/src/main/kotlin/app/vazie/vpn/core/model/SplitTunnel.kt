package app.vazie.vpn.core.model

/** Which apps use the tunnel: all of them, all but [packages], or only [packages]. */
data class SplitTunnel(
    val mode: Mode = Mode.OFF,
    val packages: Set<String> = emptySet(),
) {
    enum class Mode(val id: String) {
        /** Every app goes through the tunnel. */
        OFF("off"),

        /** Every app except [packages]. */
        EXCLUDE("exclude"),

        /** Only [packages] (and Vazie itself, so the readiness check tests the real path). */
        INCLUDE("include"),
        ;

        companion object {
            fun fromId(id: String?): Mode = entries.firstOrNull { it.id == id } ?: OFF
        }
    }

    /** Whether anything is actually split: a mode with no apps chosen changes nothing. */
    val isActive: Boolean get() = mode != Mode.OFF && packages.isNotEmpty()

    companion object {
        /** Package names as Android allows them: dot-separated Java identifiers. */
        private val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

        fun isPackageName(name: String): Boolean = name.length <= MAX_PACKAGE_LENGTH && PACKAGE.matches(name)

        private const val MAX_PACKAGE_LENGTH = 255
    }
}
