package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Turns a normalized [VpnProfile.Xray] into the JSON Xray-core actually runs. */
internal object XrayConfigFactory {

    /** The resolver handed to the apps on the device; queries are captured by routing. */
    const val DNS_SERVER: String = "1.1.1.1"

    /** The resolvers Xray asks on the device's behalf. DNS-over-HTTPS, so a query is a TCP 443 connection
     * that the routing table proxies like anything else. */
    private val UPSTREAM_RESOLVERS = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/dns-query",
    )

    private const val TAG_TUN_IN = "tun-in"
    private const val TAG_PROXY = "proxy"
    private const val TAG_DNS_OUT = "dns-out"
    private const val TAG_BLOCK = "block"

    private const val ENV_TUN_FD = "xray.tun.fd"

    /** Cosmetic on Android; a constant, never the user's profile name. */
    private const val INTERFACE_NAME = "vazie-tun"

    /** `none` keeps engine output out of logcat entirely; see [XrayEngine]. */
    private const val LOG_LEVEL = "none"

    private val json = Json { prettyPrint = false }

    fun create(
        profile: VpnProfile.Xray,
        mtu: Int,
        tunDescriptor: Int,
        dialAddress: String = profile.outbound.endpoint.host,
    ): String {
        val outbound = when (val candidate = profile.outbound) {
            is XrayOutbound.Vless -> candidate
        }
        val document = buildJsonObject {
            putJsonObject("env") { put(ENV_TUN_FD, tunDescriptor.toString()) }
            putJsonObject("log") { put("loglevel", LOG_LEVEL) }
            put("dns", dns())
            putJsonArray("inbounds") { add(tunInbound(mtu)) }
            putJsonArray("outbounds") {
                add(vlessOutbound(outbound, dialAddress))
                add(buildJsonObject { put("tag", TAG_DNS_OUT); put("protocol", "dns") })
                add(buildJsonObject { put("tag", TAG_BLOCK); put("protocol", "blackhole") })
            }
            put("routing", routing())
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    private fun dns(): JsonObject = buildJsonObject {
        putJsonArray("servers") { UPSTREAM_RESOLVERS.forEach { add(it) } }
        // A records only. An app that never learns an AAAA never tries to reach one, which is what
        // keeps the blackhole rule below a backstop rather than a daily occurrence.
        put("queryStrategy", "UseIPv4")
    }

    private fun tunInbound(mtu: Int): JsonObject = buildJsonObject {
        put("tag", TAG_TUN_IN)
        put("protocol", "tun")
        putJsonObject("settings") {
            // The name is cosmetic on Android — the interface already exists and Xray reads its real name
            // from the descriptor — but leaving it empty makes Xray enumerate interfaces to invent one.
            put("name", INTERFACE_NAME)
            put("mtu", mtu)
        }
        putJsonObject("sniffing") {
            put("enabled", true)
            // Recovering the hostname means the server is asked for a name rather than for an address this
            // device resolved, which is what makes remote resolution real rather than nominal.
            putJsonArray("destOverride") { add("http"); add("tls"); add("quic") }
            put("routeOnly", false)
        }
    }

    private fun routing(): JsonObject = buildJsonObject {
        put("domainStrategy", "AsIs")
        putJsonArray("rules") {
            add(
                buildJsonObject {
                    put("type", "field")
                    put("port", "53")
                    put("outboundTag", TAG_DNS_OUT)
                }
            )
            add(
                buildJsonObject {
                    put("type", "field")
                    putJsonArray("ip") { add("::/0") }
                    put("outboundTag", TAG_BLOCK)
                }
            )
            add(
                buildJsonObject {
                    put("type", "field")
                    put("network", "tcp,udp")
                    put("outboundTag", TAG_PROXY)
                }
            )
        }
    }

    private fun vlessOutbound(
        outbound: XrayOutbound.Vless,
        dialAddress: String,
    ): JsonObject = buildJsonObject {
        put("tag", TAG_PROXY)
        put("protocol", "vless")
        putJsonObject("settings") {
            putJsonArray("vnext") {
                add(
                    buildJsonObject {
                        put("address", dialAddress)
                        put("port", outbound.endpoint.port)
                        putJsonArray("users") {
                            add(
                                buildJsonObject {
                                    put("id", outbound.userId.expose())
                                    put("encryption", "none")
                                    outbound.flow?.let { put("flow", it.wireName) }
                                    put("level", 0)
                                }
                            )
                        }
                    }
                )
            }
        }
        put("streamSettings", streamSettings(outbound, dialAddress))
    }

    private fun streamSettings(
        outbound: XrayOutbound.Vless,
        dialAddress: String,
    ): JsonObject = buildJsonObject {
        val originalHost = outbound.endpoint.host
        val hostWasResolved = dialAddress != originalHost
        put("network", outbound.transport.networkName())
        put("security", outbound.security.securityName())
        when (val security = outbound.security) {
            XraySecurity.None -> Unit
            is XraySecurity.Tls ->
                put("tlsSettings", tlsSettings(security, originalHost.takeIf { hostWasResolved }))
            is XraySecurity.Reality ->
                put("realitySettings", realitySettings(security, originalHost.takeIf { hostWasResolved }))
        }
        when (val transport = outbound.transport) {
            is XrayTransport.Tcp -> put("rawSettings", tcpSettings(transport))
            is XrayTransport.WebSocket -> put(
                "wsSettings",
                webSocketSettings(
                    transport,
                    fallbackHost = originalHost.takeIf {
                        hostWasResolved && outbound.security is XraySecurity.None
                    },
                ),
            )
            is XrayTransport.Grpc -> put(
                "grpcSettings",
                grpcSettings(
                    transport,
                    fallbackAuthority = originalHost.takeIf {
                        hostWasResolved && outbound.security is XraySecurity.None
                    },
                ),
            )
        }
    }

    private fun tlsSettings(
        security: XraySecurity.Tls,
        fallbackServerName: String?,
    ): JsonObject = buildJsonObject {
        (security.serverName ?: fallbackServerName)?.let { put("serverName", it) }
        security.fingerprint?.let { put("fingerprint", preQuantumFingerprint(it)) }
        if (security.alpn.isNotEmpty()) {
            putJsonArray("alpn") { security.alpn.forEach { add(it) } }
        }
        // No `allowInsecure`: `XraySupport` refuses profiles that ask for it.
    }

    private fun realitySettings(
        security: XraySecurity.Reality,
        fallbackServerName: String?,
    ): JsonObject = buildJsonObject {
        (security.serverName ?: fallbackServerName)?.let { put("serverName", it) }
        put("fingerprint", preQuantumFingerprint(security.fingerprint))
        put("publicKey", security.publicKey.expose())
        put("shortId", security.shortId?.expose().orEmpty())
        put("spiderX", security.spiderX ?: DEFAULT_SPIDER_X)
    }

    private fun tcpSettings(transport: XrayTransport.Tcp): JsonObject = buildJsonObject {
        putJsonObject("header") { put("type", transport.headerType ?: "none") }
    }

    private fun webSocketSettings(
        transport: XrayTransport.WebSocket,
        fallbackHost: String?,
    ): JsonObject = buildJsonObject {
        put("path", transport.path.ifEmpty { "/" })
        (transport.host ?: fallbackHost)?.let { put("host", it) }
    }

    private fun grpcSettings(
        transport: XrayTransport.Grpc,
        fallbackAuthority: String?,
    ): JsonObject = buildJsonObject {
        fallbackAuthority?.let { put("authority", it) }
        put("serviceName", transport.serviceName)
        put("multiMode", transport.multiMode)
    }

    /** Xray renamed the TCP stream to `raw` and kept `tcp` as an alias. Vazie writes the current name, so a
     * future release that drops the alias does not take Vazie's tunnels with it. */
    private fun XrayTransport.networkName(): String = when (this) {
        is XrayTransport.Tcp -> "raw"
        is XrayTransport.WebSocket -> "ws"
        is XrayTransport.Grpc -> "grpc"
    }

    private fun XraySecurity.securityName(): String = when (this) {
        XraySecurity.None -> "none"
        is XraySecurity.Tls -> "tls"
        is XraySecurity.Reality -> "reality"
    }

    /** Post-quantum ClientHellos (>1700 bytes) can hang REALITY on lossy paths, so the last pre-quantum
     * build of the requested browser is used. */
    private fun preQuantumFingerprint(requested: String?): String {
        val name = requested?.lowercase() ?: DEFAULT_FINGERPRINT
        return PRE_QUANTUM_BUILDS[name] ?: name
    }

    private const val DEFAULT_FINGERPRINT = "chrome"

    private val PRE_QUANTUM_BUILDS = mapOf(
        "chrome" to "hellochrome_120",
        "firefox" to "hellofirefox_120",
        "edge" to "helloedge_106",
    )

    private const val DEFAULT_SPIDER_X = "/"
}
