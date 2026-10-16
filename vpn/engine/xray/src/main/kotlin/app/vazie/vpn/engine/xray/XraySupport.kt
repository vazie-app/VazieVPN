package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.EngineSupport
import app.vazie.vpn.api.UnsupportedRuntimeFeature
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport

/** What the bundled Xray build can actually execute, asked before a tunnel is built. */
internal object XraySupport {

    fun supports(profile: VpnProfile): EngineSupport {
        val xray = profile as? VpnProfile.Xray
            ?: return EngineSupport.Unsupported(UnsupportedRuntimeFeature.ENGINE)
        return when (val outbound = xray.outbound) {
            is XrayOutbound.Vless -> supportsVless(outbound)
        }
    }

    private fun supportsVless(outbound: XrayOutbound.Vless): EngineSupport {
        val security = outbound.security
        if (security is XraySecurity.Tls && security.allowInsecure) {
            return EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY)
        }
        if (!isKnownFingerprint(security.fingerprint)) {
            return EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY)
        }
        if (security is XraySecurity.Reality && outbound.transport is XrayTransport.WebSocket) {
            return EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY)
        }
        // Vision needs TLS or REALITY over RAW/TCP; the engine checks it itself.
        if (outbound.flow == XrayFlow.XTLS_RPRX_VISION) {
            if (outbound.transport !is XrayTransport.Tcp) {
                return EngineSupport.Unsupported(UnsupportedRuntimeFeature.FLOW)
            }
            if (security is XraySecurity.None) {
                return EngineSupport.Unsupported(UnsupportedRuntimeFeature.FLOW)
            }
        }
        return EngineSupport.Supported
    }

    /** `null` and empty let Xray choose; anything else must be known, and `unsafe` is refused. */
    private fun isKnownFingerprint(fingerprint: String?): Boolean {
        val name = fingerprint?.trim()?.lowercase().orEmpty()
        if (name.isEmpty()) return true
        if (name == UNSAFE_FINGERPRINT) return false
        return name in PRESET_FINGERPRINTS || name.startsWith(EXPLICIT_FINGERPRINT_PREFIX)
    }

    private const val UNSAFE_FINGERPRINT = "unsafe"

    /** Matches Xray's `hello…` fingerprint family by prefix instead of copying its tables. */
    private const val EXPLICIT_FINGERPRINT_PREFIX = "hello"

    private val PRESET_FINGERPRINTS = setOf(
        "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq",
        "random", "randomized", "randomizednoalpn",
    )
}
