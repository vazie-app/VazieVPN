package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The connection contract, checked as a contract. */
class VpnConnectionStateTest {

    private val id = ProfileId("profile-1")

    @Test
    fun `a state holding a tunnel says it is active`() {
        listOf(
            VpnConnectionState.Preparing,
            VpnConnectionState.Connecting(id),
            VpnConnectionState.Connected(id, Instant.EPOCH),
            VpnConnectionState.Disconnecting,
        ).forEach { state ->
            assertTrue(state.isActive, "$state should be active")
        }
    }

    @Test
    fun `a state holding nothing says so`() {
        listOf(
            VpnConnectionState.Idle,
            VpnConnectionState.Failed(ConnectionFailure.Unknown),
            VpnConnectionState.NoInternet,
        ).forEach { state ->
            assertFalse(state.isActive, "$state should not be active")
        }
    }

    @Test
    fun `only the states that name a profile answer with one`() {
        assertEquals(id, VpnConnectionState.Connecting(id).profileId)
        assertEquals(id, VpnConnectionState.Connected(id, Instant.EPOCH).profileId)

        listOf(
            VpnConnectionState.Idle,
            VpnConnectionState.Preparing,
            VpnConnectionState.Disconnecting,
            VpnConnectionState.NoInternet,
            VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable),
        ).forEach { state ->
            assertNull(state.profileId, "$state named a profile it does not have")
        }
    }

    @Test
    fun `a failure can name a field and never its content`() {
        val rendered = listOf(
            ConnectionFailure.ConsentDenied,
            ConnectionFailure.NoInternet,
            ConnectionFailure.ProfileMissing,
            ConnectionFailure.ProfileInvalid(field = "publicKey"),
            ConnectionFailure.UnsupportedConfiguration(UnsupportedRuntimeFeature.SECURITY),
            ConnectionFailure.TunnelSetupFailed,
            ConnectionFailure.EngineFailed(VpnEngineId.XRAY),
            ConnectionFailure.TunnelUnusable,
            ConnectionFailure.ServiceStopped,
            ConnectionFailure.Unknown,
        ).joinToString(" ") { it.toString() }

        // The field name is allowed through; that is the whole point of `ProfileInvalid`.
        assertTrue(rendered.contains("publicKey"))
        listOf(SYNTHETIC_USER_ID, SYNTHETIC_KEY, SYNTHETIC_HOST).forEach { value ->
            assertFalse(rendered.contains(value), "$value reached a failure")
        }
    }

    @Test
    fun `an unconnected snapshot has nothing to show`() {
        val snapshot = VpnConnectionSnapshot()

        assertEquals(VpnConnectionState.Idle, snapshot.state)
        assertNull(snapshot.subject)
        assertNull(snapshot.diagnostics)
    }

    @Test
    fun `traffic the device could not measure is not zero`() {
        assertFalse(
            TrafficStats.UNAVAILABLE.isKnown,
            "a screen showing 0 B after ten minutes of browsing is stating something false",
        )
        assertTrue(TrafficStats.NONE.isKnown)
    }

    @Test
    fun `a diagnostics read-out has no field that could hold a secret`() {
        // Named arguments on purpose: adding a credential parameter breaks compilation.
        val diagnostics = ConnectionDiagnostics(
            engine = "xray",
            protocol = "vless",
            security = "reality",
            transport = "raw",
            flow = "xtls-rprx-vision",
            endpointHost = SYNTHETIC_HOST,
            endpointPort = 443,
            dnsMode = DnsMode.TUNNEL,
            ipv4 = true,
            ipv6 = false,
        )

        assertNull(diagnostics.latencyMs, "nothing measures round-trip time yet")
        assertFalse(diagnostics.toString().contains(SYNTHETIC_KEY))
    }

    @Test
    fun `a secret in a profile stays redacted through the whole tree`() {
        val outbound = XrayOutbound.Vless(
            userId = Secret.of(SYNTHETIC_USER_ID),
            endpoint = Endpoint(SYNTHETIC_HOST, 443),
            security = XraySecurity.Reality(
                serverName = SYNTHETIC_HOST,
                fingerprint = "chrome",
                publicKey = Secret.of(SYNTHETIC_KEY),
                shortId = null,
                spiderX = null,
            ),
            transport = XrayTransport.Tcp(),
        )

        val rendered = outbound.toString()
        assertFalse(rendered.contains(SYNTHETIC_USER_ID))
        assertFalse(rendered.contains(SYNTHETIC_KEY))
    }

    private companion object {
        const val SYNTHETIC_USER_ID = "00000000-0000-4000-8000-000000000000"
        const val SYNTHETIC_KEY = "synthetic-reality-public-key-not-a-real-one"
        const val SYNTHETIC_HOST = "relay.example.net"
    }
}
