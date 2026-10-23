package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.EngineHost
import app.vazie.vpn.api.EngineStartFailure
import app.vazie.vpn.api.EngineStartResult
import app.vazie.vpn.api.TunOptions
import app.vazie.vpn.api.TunnelAttachment
import app.vazie.vpn.api.TunnelStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The engine's own decisions, against a runtime that is not there. */
class XrayEngineTest {

    @Test
    fun `the engine owns the tun, so no bridge is in the path`() {
        val engine = XrayEngine(FakeXrayRuntime())

        assertEquals(
            TunnelStrategy.EngineOwnsTun,
            engine.tunnelStrategy(XrayTestProfiles.profile()),
        )
    }

    @Test
    fun `the interface captures IPv6 instead of leaving it outside the tunnel`() {
        val spec = XrayEngine(FakeXrayRuntime())
            .tunSpec(XrayTestProfiles.profile(), TunOptions(sessionName = "Vazie"))

        assertTrue(
            spec.routes.any { it.address == "::" && it.prefixLength == 0 },
            "without a ::/0 route every IPv6-capable app talks past the tunnel",
        )
        assertTrue(spec.routes.any { it.address == "0.0.0.0" && it.prefixLength == 0 })
        assertEquals(listOf(XrayConfigFactory.DNS_SERVER), spec.dnsServers)
    }

    @Test
    fun `the session name is the one the runtime supplied, never the profile's`() {
        val spec = XrayEngine(FakeXrayRuntime()).tunSpec(
            XrayTestProfiles.profile(name = "Alice's work relay"),
            TunOptions(sessionName = "Vazie"),
        )

        assertEquals("Vazie", spec.sessionName)
    }

    @Test
    fun `an unsupported profile is refused without touching the runtime`() = runTest {
        val runtime = FakeXrayRuntime()
        val engine = XrayEngine(runtime)

        val result = engine.start(
            profile = XrayTestProfiles.profile(
                security = XrayTestProfiles.tls(allowInsecure = true),
            ),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = resolvingHost(),
        )

        assertEquals(EngineStartResult.Failed(EngineStartFailure.CONFIGURATION), result)
        assertEquals(0, runtime.runCalls)
        assertFalse(runtime.attached)
    }

    @Test
    fun `the host is attached before the core is started`() = runTest {
        val runtime = FakeXrayRuntime()

        XrayEngine(runtime).start(
            profile = XrayTestProfiles.profile(security = XrayTestProfiles.reality()),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = resolvingHost(),
        )

        assertEquals(
            listOf("attach", "run", "isRunning"),
            runtime.calls,
            "a socket opened before protect() is registered goes back into the tunnel it feeds",
        )
    }

    @Test
    fun `a core that refuses the config is stopped rather than left loaded`() = runTest {
        val runtime = FakeXrayRuntime(
            result = XrayInvocation.Failed(detail = "invalid publicKey: ${XrayTestProfiles.PUBLIC_KEY}"),
        )

        val result = XrayEngine(runtime).start(
            profile = XrayTestProfiles.profile(security = XrayTestProfiles.reality()),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = resolvingHost(),
        )

        assertEquals(EngineStartResult.Failed(EngineStartFailure.RUNTIME), result)
        assertEquals(1, runtime.stopCalls)
    }

    @Test
    fun `the core's own message never reaches the result`() = runTest {
        // Xray quotes the offending value back in its errors, so the message it produced for a bad
        // REALITY key contains that key. The engine drops it and reports a fixed reason.
        val runtime = FakeXrayRuntime(
            result = XrayInvocation.Failed(detail = "invalid publicKey: ${XrayTestProfiles.PUBLIC_KEY}"),
        )

        val rendered = XrayEngine(runtime).start(
            profile = XrayTestProfiles.profile(security = XrayTestProfiles.reality()),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = resolvingHost(),
        ).toString()

        assertFalse(rendered.contains(XrayTestProfiles.PUBLIC_KEY))
        assertFalse(rendered.contains(XrayTestProfiles.USER_ID))
    }

    @Test
    fun `a server name that cannot be resolved over encrypted DNS never starts the core`() = runTest {
        val runtime = FakeXrayRuntime()

        val result = XrayEngine(runtime).start(
            profile = XrayTestProfiles.profile(),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = object : EngineHost {
                override fun protect(socketFd: Int): Boolean = true
                override fun resolveIpv4(hostname: String): String? = null
            },
        )

        assertEquals(EngineStartResult.Failed(EngineStartFailure.RUNTIME), result)
        assertEquals(0, runtime.runCalls, "the core was started without a resolved server address")
    }

    @Test
    fun `a core that answers yes but is not running counts as a failure`() = runTest {
        val runtime = FakeXrayRuntime(running = false)

        val result = XrayEngine(runtime).start(
            profile = XrayTestProfiles.profile(),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = resolvingHost(),
        )

        assertEquals(EngineStartResult.Failed(EngineStartFailure.RUNTIME), result)
        assertEquals(1, runtime.stopCalls)
    }

    @Test
    fun `a missing native library is its own reason`() = runTest {
        val runtime = FakeXrayRuntime(throwOnRun = UnsatisfiedLinkError("libgojni.so"))

        val result = XrayEngine(runtime).start(
            profile = XrayTestProfiles.profile(),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = resolvingHost(),
        )

        assertEquals(EngineStartResult.Failed(EngineStartFailure.NATIVE_LIBRARY), result)
    }

    @Test
    fun `a good start reports started and leaves the core running`() = runTest {
        val runtime = FakeXrayRuntime()

        val result = XrayEngine(runtime).start(
            profile = XrayTestProfiles.profile(security = XrayTestProfiles.reality()),
            attachment = TunnelAttachment.Tun(descriptor = 7),
            host = resolvingHost(),
        )

        assertEquals(EngineStartResult.Started, result)
        assertEquals(0, runtime.stopCalls)
        assertTrue(runtime.lastConfig!!.contains("\"xray.tun.fd\":\"7\""))
    }
}

private fun resolvingHost(address: String = "203.0.113.7"): EngineHost = object : EngineHost {
    override fun protect(socketFd: Int): Boolean = true
    override fun resolveIpv4(hostname: String): String = address
}

private class FakeXrayRuntime(
    private val result: XrayInvocation = XrayInvocation.Succeeded,
    private val running: Boolean = true,
    private val throwOnRun: Throwable? = null,
) : XrayRuntime {

    val calls: MutableList<String> = mutableListOf()
    var attached: Boolean = false
        private set
    var runCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set
    var lastConfig: String? = null
        private set

    override fun attach(host: EngineHost): Boolean {
        calls += "attach"
        attached = true
        return true
    }

    override fun run(configJson: String): XrayInvocation {
        calls += "run"
        runCalls++
        lastConfig = configJson
        throwOnRun?.let { throw it }
        return result
    }

    override fun isRunning(): Boolean {
        calls += "isRunning"
        return running
    }

    override fun stop() {
        calls += "stop"
        stopCalls++
    }
}
