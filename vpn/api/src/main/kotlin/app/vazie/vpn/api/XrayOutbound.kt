package app.vazie.vpn.api

import app.vazie.vpn.core.model.Secret

/** Where a profile connects to. Hostname or literal address plus port, exactly as the user's own
 * configuration gave it. */
data class Endpoint(val host: String, val port: Int) {
    init {
        require(host.isNotBlank()) { "Endpoint host must not be blank" }
        require(port in PORT_RANGE) { "Endpoint port must be in $PORT_RANGE" }
    }

    companion object {
        val PORT_RANGE = 1..65535
    }
}

/** The transport-layer security a profile uses. */
enum class XraySecurityKind { NONE, TLS, REALITY }

/** The stream transport a profile uses. */
enum class XrayTransportKind { TCP, WEBSOCKET, GRPC }

/** The XTLS flows Vazie understands. */
enum class XrayFlow(val wireName: String) {
    XTLS_RPRX_VISION("xtls-rprx-vision"),
}

/** Security settings, one case per [XraySecurityKind]. */
sealed interface XraySecurity {

    val kind: XraySecurityKind

    /** The TLS server name, when this security has one. On the interface rather than on each case so that a
     * detail screen can read it without a `when` over something it does not otherwise care about. */
    val serverName: String? get() = null

    /** The TLS fingerprint profile (`chrome`, `firefox`), when this security has one. */
    val fingerprint: String? get() = null

    data object None : XraySecurity {
        override val kind: XraySecurityKind get() = XraySecurityKind.NONE
    }

    data class Tls(
        override val serverName: String?,
        override val fingerprint: String?,
        val alpn: List<String> = emptyList(),
        val allowInsecure: Boolean = false,
    ) : XraySecurity {
        override val kind: XraySecurityKind get() = XraySecurityKind.TLS
    }

    data class Reality(
        override val serverName: String?,
        override val fingerprint: String?,
        val publicKey: Secret<String>,
        val shortId: Secret<String>?,
        val spiderX: String?,
    ) : XraySecurity {
        override val kind: XraySecurityKind get() = XraySecurityKind.REALITY
    }
}

/** Stream transport settings, one case per [XrayTransportKind]. */
sealed interface XrayTransport {

    val kind: XrayTransportKind

    /** @param headerType `none` or `http`, as the `headerType` parameter spells it. */
    data class Tcp(val headerType: String? = null) : XrayTransport {
        override val kind: XrayTransportKind get() = XrayTransportKind.TCP
    }

    data class WebSocket(val path: String, val host: String? = null) : XrayTransport {
        override val kind: XrayTransportKind get() = XrayTransportKind.WEBSOCKET
    }

    data class Grpc(val serviceName: String, val multiMode: Boolean = false) : XrayTransport {
        override val kind: XrayTransportKind get() = XrayTransportKind.GRPC
    }
}

/** One query parameter the parser recognized as present but did not map onto a field of this model. */
data class UnknownParameter(val name: String, val value: Secret<String>)

/** Everything the parser saw and could not place, in the order it appeared. */
@JvmInline
value class UnknownParameters(val entries: List<UnknownParameter>) {

    val names: List<String> get() = entries.map { it.name }

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        val EMPTY = UnknownParameters(emptyList())
    }
}

/** An Xray outbound, normalized. */
sealed interface XrayOutbound {

    val endpoint: Endpoint
    val security: XraySecurity
    val transport: XrayTransport
    val unknownParameters: UnknownParameters

    data class Vless(
        val userId: Secret<String>,
        override val endpoint: Endpoint,
        override val security: XraySecurity,
        override val transport: XrayTransport,
        val flow: XrayFlow? = null,
        override val unknownParameters: UnknownParameters = UnknownParameters.EMPTY,
    ) : XrayOutbound
}
