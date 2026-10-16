package app.vazie.vpn.config

/** The building blocks every parser test is made of, and the only place a link-shaped string is assembled in
 * this repository. */
internal object SyntheticLinks {

    const val SCHEME = "vless://"

    const val USER_ID = "00000000-0000-4000-8000-000000000001"
    const val HOST = "relay.example.net"
    const val CDN_HOST = "cdn.example.net"
    const val PORT = 443

    /** RFC 5737 TEST-NET-1: an address that cannot route anywhere. */
    const val IPV4 = "192.0.2.10"

    /** RFC 3849 documentation prefix. */
    const val IPV6 = "2001:db8::1"

    const val PUBLIC_KEY = "not-a-real-reality-public-key-0000000000000"
    const val SHORT_ID = "0011223344556677"

    fun link(
        userId: String = USER_ID,
        host: String = HOST,
        port: String = PORT.toString(),
        query: String = "",
        fragment: String? = null,
    ): String = buildString {
        append(SCHEME)
        append(userId)
        append('@')
        append(host)
        if (port.isNotEmpty()) {
            append(':')
            append(port)
        }
        if (query.isNotEmpty()) {
            append('?')
            append(query)
        }
        if (fragment != null) {
            append('#')
            append(fragment)
        }
    }
}
