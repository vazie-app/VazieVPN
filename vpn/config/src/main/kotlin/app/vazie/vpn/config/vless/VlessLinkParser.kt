package app.vazie.vpn.config.vless

import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.Endpoint
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.UnknownParameter
import app.vazie.vpn.api.UnknownParameters
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XraySecurityKind
import app.vazie.vpn.api.XrayTransport
import app.vazie.vpn.api.XrayTransportKind
import app.vazie.vpn.config.ConfigParser
import app.vazie.vpn.config.InvalidReason
import app.vazie.vpn.config.ParseResult
import app.vazie.vpn.config.ProtocolDescriptor
import app.vazie.vpn.config.UnsupportedFeature

/** Reads a `vless://` share link. */
class VlessLinkParser : ConfigParser {

    override val id: String get() = ProtocolDescriptor.VLESS.id

    override fun accepts(raw: String): Boolean =
        raw.regionMatches(0, SCHEME, 0, SCHEME.length, ignoreCase = true)

    override fun parse(raw: String): ParseResult {
        if (!accepts(raw)) return invalid(InvalidReason.UnknownScheme(null))

        var rest = raw.substring(SCHEME.length)

        val hash = rest.indexOf('#')
        val rawFragment = if (hash >= 0) rest.substring(hash + 1) else null
        if (hash >= 0) rest = rest.substring(0, hash)

        val question = rest.indexOf('?')
        val rawQuery = if (question >= 0) rest.substring(question + 1) else ""
        val authority = if (question >= 0) rest.substring(0, question) else rest

        val at = authority.lastIndexOf('@')
        if (at < 0) return invalid(InvalidReason.MissingUserId)
        val rawUserId = authority.substring(0, at)
        if (rawUserId.isEmpty()) return invalid(InvalidReason.MissingUserId)

        val userId = PercentCoding.decodeStrict(rawUserId)
            ?: return invalid(InvalidReason.MalformedEncoding)
        if (!UUID.matches(userId)) return invalid(InvalidReason.MalformedUserId)

        val endpoint = when (val parsed = parseHostPort(authority.substring(at + 1))) {
            is HostPort.Failed -> return invalid(parsed.reason)
            is HostPort.Parsed -> Endpoint(host = parsed.host, port = parsed.port)
        }

        val query = LinkQuery.parse(rawQuery) ?: return invalid(InvalidReason.MalformedEncoding)

        val encryption = query.take("encryption")?.trim().orEmpty()
        if (encryption.isNotEmpty() && !encryption.equals(NO_ENCRYPTION, ignoreCase = true)) {
            return unsupported(UnsupportedFeature.Encryption(encryption))
        }

        val rawSecurity = query.take("security")?.trim().orEmpty().ifEmpty { SECURITY_NONE }
        val securityKind = SECURITY_KINDS[rawSecurity.lowercase()]
            ?: return unsupported(UnsupportedFeature.Security(rawSecurity))

        val rawTransport = query.take("type")?.trim().orEmpty().ifEmpty { TRANSPORT_TCP }
        val transportKind = TRANSPORT_KINDS[rawTransport.lowercase()]
            ?: return unsupported(UnsupportedFeature.Transport(rawTransport))

        val rawFlow = query.take("flow")?.trim().orEmpty()
        val flow = when {
            rawFlow.isEmpty() -> null
            rawFlow.equals(XrayFlow.XTLS_RPRX_VISION.wireName, ignoreCase = true) ->
                XrayFlow.XTLS_RPRX_VISION

            else -> return unsupported(UnsupportedFeature.Flow(rawFlow))
        }

        // REALITY runs over TCP, HTTP/2 and gRPC; there is no WebSocket variant of it, so a link
        // claiming both describes a configuration no server can serve.
        if (securityKind == XraySecurityKind.REALITY && transportKind == XrayTransportKind.WEBSOCKET) {
            return invalid(InvalidReason.ConflictingParameters(listOf("security", "type")))
        }
        if (flow != null && securityKind == XraySecurityKind.NONE) {
            return invalid(InvalidReason.ConflictingParameters(listOf("flow", "security")))
        }
        if (flow != null && transportKind != XrayTransportKind.TCP) {
            return invalid(InvalidReason.ConflictingParameters(listOf("flow", "type")))
        }

        val security = when (securityKind) {
            XraySecurityKind.NONE -> XraySecurity.None

            XraySecurityKind.TLS -> XraySecurity.Tls(
                serverName = query.takeServerName(),
                fingerprint = query.take("fp", "fingerprint")?.trimOrNull(),
                alpn = query.take("alpn").orEmpty().split(',').mapNotNull { it.trimOrNull() },
                allowInsecure = query.take("allowInsecure", "insecure").isTruthy(),
            )

            XraySecurityKind.REALITY -> {
                val publicKey = query.take("pbk", "publicKey")?.trimOrNull()
                    ?: return invalid(InvalidReason.MissingParameter("pbk"))
                XraySecurity.Reality(
                    serverName = query.takeServerName(),
                    fingerprint = query.take("fp", "fingerprint")?.trimOrNull(),
                    publicKey = Secret.of(publicKey),
                    shortId = query.take("sid", "shortId")?.trimOrNull()?.let { Secret.of(it) },
                    spiderX = query.take("spx", "spiderX")?.trimOrNull(),
                )
            }
        }

        val transport = when (transportKind) {
            XrayTransportKind.TCP -> XrayTransport.Tcp(
                headerType = query.take("headerType")?.trimOrNull(),
            )

            XrayTransportKind.WEBSOCKET -> XrayTransport.WebSocket(
                path = query.take("path")?.trimOrNull() ?: DEFAULT_WEBSOCKET_PATH,
                host = query.take("host")?.trimOrNull(),
            )

            XrayTransportKind.GRPC -> XrayTransport.Grpc(
                serviceName = query.take("serviceName")?.trim().orEmpty(),
                multiMode = query.take("mode")?.trim().equals(GRPC_MULTI_MODE, ignoreCase = true),
            )
        }

        val outbound = XrayOutbound.Vless(
            userId = Secret.of(userId),
            endpoint = endpoint,
            security = security,
            transport = transport,
            flow = flow,
            unknownParameters = UnknownParameters(
                query.remaining().map { (name, value) -> UnknownParameter(name, Secret.of(value)) },
            ),
        )

        return ParseResult.Recognized(
            draft = ProfileDraft.Xray(suggestedName = rawFragment.toName(), outbound = outbound),
            descriptor = ProtocolDescriptor.VLESS,
        )
    }

    /** `sni` is the parameter every current client writes; `peer` is what older ones wrote for the same
     * field, and links generated years ago are still in circulation. */
    private fun LinkQuery.takeServerName(): String? = take("sni", "peer")?.trimOrNull()

    private fun parseHostPort(text: String): HostPort {
        if (text.isEmpty()) return HostPort.Failed(InvalidReason.MissingHost)

        val host: String
        val rawPort: String
        if (text.startsWith('[')) {
            val close = text.indexOf(']')
            if (close < 0) return HostPort.Failed(InvalidReason.MalformedUri)
            host = text.substring(1, close)
            val after = text.substring(close + 1)
            when {
                after.isEmpty() -> return HostPort.Failed(InvalidReason.MissingPort)
                !after.startsWith(':') -> return HostPort.Failed(InvalidReason.MalformedUri)
            }
            rawPort = after.substring(1)
            if (host.isEmpty()) return HostPort.Failed(InvalidReason.MissingHost)
            if (!IPV6.matches(host)) return HostPort.Failed(InvalidReason.MalformedHost)
        } else {
            val colon = text.lastIndexOf(':')
            if (colon < 0) return HostPort.Failed(InvalidReason.MissingPort)
            host = text.substring(0, colon)
            rawPort = text.substring(colon + 1)
            if (host.isEmpty()) return HostPort.Failed(InvalidReason.MissingHost)
            if (!HOST.matches(host)) return HostPort.Failed(InvalidReason.MalformedHost)
        }

        if (rawPort.isEmpty()) return HostPort.Failed(InvalidReason.MissingPort)
        val port = rawPort.toIntOrNull()?.takeIf { it in Endpoint.PORT_RANGE }
            ?: return HostPort.Failed(InvalidReason.InvalidPort)
        return HostPort.Parsed(host = host, port = port)
    }

    /** The fragment as a suggested display name. */
    private fun String?.toName(): String? = this
        ?.let(PercentCoding::decodeLenient)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_NAME_LENGTH)

    private fun String.trimOrNull(): String? = trim().takeIf { it.isNotEmpty() }

    private fun String?.isTruthy(): Boolean =
        this != null && (this == "1" || equals("true", ignoreCase = true))

    private fun invalid(reason: InvalidReason) = ParseResult.Invalid(reason)

    private fun unsupported(feature: UnsupportedFeature) =
        ParseResult.Unsupported(descriptor = ProtocolDescriptor.VLESS, feature = feature)

    private sealed interface HostPort {
        data class Parsed(val host: String, val port: Int) : HostPort
        data class Failed(val reason: InvalidReason) : HostPort
    }

    private companion object {
        const val SCHEME = "vless://"
        const val SECURITY_NONE = "none"
        const val TRANSPORT_TCP = "tcp"
        const val NO_ENCRYPTION = "none"
        const val GRPC_MULTI_MODE = "multi"
        const val DEFAULT_WEBSOCKET_PATH = "/"
        const val MAX_NAME_LENGTH = 80

        val SECURITY_KINDS = mapOf(
            "none" to XraySecurityKind.NONE,
            "tls" to XraySecurityKind.TLS,
            "reality" to XraySecurityKind.REALITY,
        )

        /** `websocket` is accepted beside `ws` because both spellings are produced in the wild for the same
         * transport. Everything absent from this map is reported as unsupported by name. */
        val TRANSPORT_KINDS = mapOf(
            "tcp" to XrayTransportKind.TCP,
            "ws" to XrayTransportKind.WEBSOCKET,
            "websocket" to XrayTransportKind.WEBSOCKET,
            "grpc" to XrayTransportKind.GRPC,
        )

        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** A domain name or an IPv4 literal, permissively: hosts are the user's, not ours to judge. */
        val HOST = Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")

        /** Enough to tell an IPv6 literal from a typo; the OS resolves it, not this parser. */
        val IPV6 = Regex("[0-9A-Fa-f:]*:[0-9A-Fa-f:.]*(?:%[A-Za-z0-9._-]+)?")
    }
}
