package app.vazie.vpn.core.network

/** Where the Vazie backend answers, for this build. */
@JvmInline
value class ApiBaseUrl(val value: String) {

    init {
        require(value.startsWith(SCHEME)) { "the Vazie API base URL must start with $SCHEME" }
        require(value.length > SCHEME.length) { "the Vazie API base URL must name a host" }
        require(!value.endsWith("/")) { "the Vazie API base URL must not end in a slash" }
    }

    /** The absolute URL for one API path. */
    internal fun resolve(path: String): String {
        require(path.startsWith("/")) { "an API path must start with a slash" }
        return value + path
    }

    private companion object {
        const val SCHEME = "https://"
    }
}
