package app.vazie.vpn.config.vless

import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport

/** Turns a stored configuration back into the `vless://` link it came from. */
object VlessLinkSerializer {

    /** [name] becomes the percent-encoded fragment after `#`. */
    fun serialize(outbound: XrayOutbound.Vless, name: String?): Secret<String> {
        val parameters = buildList {
            // `encryption=none` is written even though the parser treats an absent value the same way. Other
            // clients are less forgiving, and an export is a thing people paste elsewhere.
            add("encryption" to ENCRYPTION_NONE)
            add("type" to outbound.transport.wireType())
            add("security" to outbound.security.wireSecurity())
            addAll(outbound.security.parameters())
            addAll(outbound.transport.parameters())
            outbound.flow?.let { add("flow" to it.wireName) }
            // Last, and in stored order: an unknown parameter is somebody else's field, and the
            // only safe thing to do with it is hand it back exactly as it arrived.
            outbound.unknownParameters.entries.forEach { add(it.name to it.value.expose()) }
        }

        val link = buildString {
            append(SCHEME)
            append(PercentCoding.encode(outbound.userId.expose(), PercentCoding.USER_INFO))
            append('@')
            append(outbound.endpoint.host.hostForLink())
            append(':')
            append(outbound.endpoint.port)
            parameters.forEachIndexed { index, (key, value) ->
                append(if (index == 0) '?' else '&')
                append(PercentCoding.encode(key, PercentCoding.QUERY))
                append('=')
                append(PercentCoding.encode(value, PercentCoding.QUERY))
            }
            name?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append('#')
                append(PercentCoding.encode(it, PercentCoding.FRAGMENT))
            }
        }
        return Secret.of(link)
    }

    /** An IPv6 literal has to go back inside brackets, or the port becomes part of the address. */
    private fun String.hostForLink(): String = if (contains(':')) "[$this]" else this

    private fun XrayTransport.wireType(): String = when (this) {
        is XrayTransport.Tcp -> "tcp"
        is XrayTransport.WebSocket -> "ws"
        is XrayTransport.Grpc -> "grpc"
    }

    private fun XraySecurity.wireSecurity(): String = when (this) {
        XraySecurity.None -> "none"
        is XraySecurity.Tls -> "tls"
        is XraySecurity.Reality -> "reality"
    }

    private fun XraySecurity.parameters(): List<Pair<String, String>> = buildList {
        serverName?.let { add("sni" to it) }
        fingerprint?.let { add("fp" to it) }
        when (this@parameters) {
            XraySecurity.None -> Unit
            is XraySecurity.Tls -> {
                if (alpn.isNotEmpty()) add("alpn" to alpn.joinToString(","))
                if (allowInsecure) add("allowInsecure" to "1")
            }
            is XraySecurity.Reality -> {
                add("pbk" to publicKey.expose())
                shortId?.let { add("sid" to it.expose()) }
                spiderX?.let { add("spx" to it) }
            }
        }
    }

    private fun XrayTransport.parameters(): List<Pair<String, String>> = when (this) {
        is XrayTransport.Tcp -> listOfNotNull(headerType?.let { "headerType" to it })
        is XrayTransport.WebSocket -> buildList {
            add("path" to path)
            host?.let { add("host" to it) }
        }

        is XrayTransport.Grpc -> buildList {
            if (serviceName.isNotEmpty()) add("serviceName" to serviceName)
            if (multiMode) add("mode" to "multi")
        }
    }

    /** Written plainly rather than split across a concatenation. */
    private const val SCHEME = "vless://"
    private const val ENCRYPTION_NONE = "none"
}
