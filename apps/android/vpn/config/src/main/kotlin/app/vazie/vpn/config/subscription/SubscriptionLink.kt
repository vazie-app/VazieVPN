package app.vazie.vpn.config.subscription

import app.vazie.vpn.core.model.Secret

/** A link to a list of configurations rather than to one: an `https` address whose answer is share links. */
class SubscriptionLink private constructor(
    /** The whole address. It carries the subscriber's token, so it is a credential like any share link. */
    val url: Secret<String>,
    /** The server that will be asked - the only part of the address a screen may show. */
    val host: String,
) {
    companion object {

        /** [raw] as a subscription link, or `null` when it is anything else. Only the shape is judged: what
         * the address answers with is found out by asking it. */
        fun parse(raw: String): SubscriptionLink? {
            val text = raw.trim()
            if (!text.startsWith(SCHEME, ignoreCase = true)) return null
            // One address and nothing around it: text with a link somewhere inside is not a link.
            if (text.any { it.isWhitespace() }) return null

            val authority = text.substring(SCHEME.length).takeWhile { it != '/' && it != '?' && it != '#' }
            // `user:password@host` is how a link dresses one server up as another.
            if ('@' in authority) return null
            val host = when {
                authority.startsWith('[') -> authority.substringBefore(']', missingDelimiterValue = "") + "]"
                else -> authority.substringBeforeLast(':', missingDelimiterValue = authority)
            }
            if (!HOST.matches(host) && !IPV6.matches(host)) return null
            if (!PORT.matches(authority.substring(host.length))) return null
            return SubscriptionLink(url = Secret.of(text), host = host.lowercase())
        }

        /** Whether [raw] is a web address without TLS. A subscription is a list of credentials, so it is
         * never asked for over a connection anyone on the path can read. */
        fun isInsecure(raw: String): Boolean = raw.trim().startsWith(INSECURE_SCHEME, ignoreCase = true)

        private const val SCHEME = "https://"
        private const val INSECURE_SCHEME = "http://"

        private val HOST = Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")
        private val IPV6 = Regex("\\[[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*]")
        private val PORT = Regex("(?::[0-9]{1,5})?")
    }
}
