package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.EngineSupport
import app.vazie.vpn.api.UnsupportedRuntimeFeature
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** What this Xray build will and will not run, decided before a tunnel exists. */
class XraySupportTest {

    @Test
    fun `the combinations the parser accepts are the ones the engine runs`() {
        val supported = listOf(
            XrayTestProfiles.profile(),
            XrayTestProfiles.profile(security = XrayTestProfiles.tls()),
            XrayTestProfiles.profile(security = XrayTestProfiles.reality()),
            XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(),
                transport = XrayTransport.WebSocket(path = "/"),
            ),
            XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(),
                transport = XrayTransport.Grpc(serviceName = "vazie"),
            ),
            XrayTestProfiles.profile(
                security = XrayTestProfiles.reality(),
                transport = XrayTransport.Grpc(serviceName = "vazie"),
            ),
            XrayTestProfiles.profile(
                security = XrayTestProfiles.reality(),
                flow = XrayFlow.XTLS_RPRX_VISION,
            ),
            XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(),
                flow = XrayFlow.XTLS_RPRX_VISION,
            ),
        )

        supported.forEach { profile ->
            assertEquals(
                EngineSupport.Supported,
                XraySupport.supports(profile),
                "$profile was refused",
            )
        }
    }

    @Test
    fun `allowInsecure is refused rather than quietly dropped`() {
        val support = XraySupport.supports(
            XrayTestProfiles.profile(security = XrayTestProfiles.tls(allowInsecure = true)),
        )

        assertEquals(
            EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY),
            support,
            "connecting anyway would verify a certificate the user asked not to verify",
        )
    }

    @Test
    fun `an unknown fingerprint is refused before the tunnel, not by the core after it`() {
        val support = XraySupport.supports(
            XrayTestProfiles.profile(security = XrayTestProfiles.tls(fingerprint = "netscape")),
        )

        assertEquals(EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY), support)
    }

    @Test
    fun `an absent fingerprint is fine, because the core picks one`() {
        val support = XraySupport.supports(
            XrayTestProfiles.profile(security = XrayTestProfiles.tls(fingerprint = null)),
        )

        assertEquals(EngineSupport.Supported, support)
    }

    @Test
    fun `unsafe is refused even though the core knows the name`() {
        val support = XraySupport.supports(
            XrayTestProfiles.profile(security = XrayTestProfiles.tls(fingerprint = "unsafe")),
        )

        assertEquals(EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY), support)
    }

    @Test
    fun `reality over websocket is refused`() {
        val support = XraySupport.supports(
            XrayTestProfiles.profile(
                security = XrayTestProfiles.reality(),
                transport = XrayTransport.WebSocket(path = "/"),
            ),
        )

        assertEquals(EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY), support)
    }

    @Test
    fun `vision needs a raw stream`() {
        val support = XraySupport.supports(
            XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(),
                transport = XrayTransport.Grpc(serviceName = "vazie"),
                flow = XrayFlow.XTLS_RPRX_VISION,
            ),
        )

        assertEquals(EngineSupport.Unsupported(UnsupportedRuntimeFeature.FLOW), support)
    }

    @Test
    fun `vision needs a secured stream`() {
        val support = XraySupport.supports(
            XrayTestProfiles.profile(
                security = XraySecurity.None,
                flow = XrayFlow.XTLS_RPRX_VISION,
            ),
        )

        assertEquals(EngineSupport.Unsupported(UnsupportedRuntimeFeature.FLOW), support)
    }

    @Test
    fun `a refusal names a field and never a value`() {
        // An enum, so no profile string can reach an error message.
        val support = XraySupport.supports(
            XrayTestProfiles.profile(security = XrayTestProfiles.reality()),
        )
        assertIs<EngineSupport.Supported>(support)

        val refusal = XraySupport.supports(
            XrayTestProfiles.profile(security = XrayTestProfiles.tls(allowInsecure = true)),
        ).toString()
        listOf(
            XrayTestProfiles.USER_ID,
            XrayTestProfiles.PUBLIC_KEY,
            XrayTestProfiles.SHORT_ID,
            XrayTestProfiles.HOST,
        ).forEach { secretish ->
            assertEquals(false, refusal.contains(secretish), "$secretish reached a refusal")
        }
    }
}
