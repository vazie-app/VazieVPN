package app.vazie.vpn.runtime

import app.vazie.vpn.api.ConnectResult
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.Endpoint
import app.vazie.vpn.api.EngineStartFailure
import app.vazie.vpn.api.EngineStartResult
import app.vazie.vpn.api.EngineSupport
import app.vazie.vpn.api.TrafficStats
import app.vazie.vpn.api.UnsupportedRuntimeFeature
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.XrayFlow
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.core.model.SplitTunnel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** The connection state machine, with the Android parts faked. */
@OptIn(ExperimentalCoroutinesApi::class)
class VazieVpnConnectionControllerTest {

    // -- Connected means connected ---------------------------------------------------------------

    @Test
    fun `the split-tunnel choice reaches the interface the runtime builds`() = runTest {
        val split = SplitTunnel(SplitTunnel.Mode.EXCLUDE, setOf("com.example.bank"))
        val world = World(splitTunnel = split)

        world.connectAndSettle()

        assertEquals(split, world.host.lastSpec?.splitTunnel)
    }

    @Test
    fun `a full path ends at connected`() = runTest {
        val world = World()

        world.connectAndSettle()

        val state = world.controller.snapshot.value.state
        assertIs<VpnConnectionState.Connected>(state)
        assertEquals(ProfileId(PROFILE_ID), state.profileId)
        assertEquals(1, world.probe.calls)
    }

    @Test
    fun `an engine that starts but carries nothing is not connected`() = runTest {
        val world = World(probe = FakeReadinessProbe(passes = false))

        world.connectAndSettle()

        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable),
            world.controller.snapshot.value.state,
            "an engine that started is not an engine that is carrying traffic",
        )
    }

    @Test
    fun `a failed probe tears the tunnel down rather than leaving it up`() = runTest {
        val world = World(probe = FakeReadinessProbe(passes = false))

        world.connectAndSettle()

        assertEquals(0, world.host.openDescriptors.get(), "a tun descriptor was left open")
        assertEquals(1, world.engine.stopCalls)
        assertEquals(1, world.host.shutdownCalls)
    }

    @Test
    fun `an engine that will not start never reaches connected`() = runTest {
        val world = World(
            engine = FakeEngine(
                startResult = EngineStartResult.Failed(EngineStartFailure.RUNTIME),
            ),
        )

        world.connectAndSettle()

        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.EngineFailed(world.engine.id)),
            world.controller.snapshot.value.state,
        )
        assertEquals(0, world.probe.calls, "the probe ran against an engine that had not started")
    }

    // -- refuse before the interface goes up -----------------------------------------------------

    /** Android is allowed to say no, and the connection has to notice. */
    @Test
    fun `a refused foreground start fails instead of waiting for ever`() = runTest {
        val world = World(launcher = FakeTunnelLauncher().apply { startSucceeds = false })

        val result = world.controller.connect(ProfileId(PROFILE_ID))

        assertEquals(ConnectResult.Rejected(ConnectionFailure.ServiceStopped), result)
        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.ServiceStopped),
            world.controller.snapshot.value.state,
            "a refused service start left the connection mid-attempt",
        )
        assertEquals(1, world.launcher.startCalls)
        assertEquals(0, world.probe.calls)
    }

    @Test
    fun `a refused foreground start leaves nothing to clean up`() = runTest {
        val world = World(launcher = FakeTunnelLauncher().apply { startSucceeds = false })

        world.controller.connect(ProfileId(PROFILE_ID))
        // A second attempt has to be able to start from scratch, which it cannot if the first one
        // is still holding a session.
        val second = world.controller.connect(ProfileId(PROFILE_ID))

        assertEquals(ConnectResult.Rejected(ConnectionFailure.ServiceStopped), second)
        assertEquals(0, world.host.openDescriptors.get())
        assertEquals(2, world.launcher.startCalls)
    }

    @Test
    fun `a missing profile is refused without a service`() = runTest {
        val world = World(profiles = FakeProfileRepository(profiles = emptyList()))

        val result = world.controller.connect(ProfileId(PROFILE_ID))

        assertEquals(ConnectResult.Rejected(ConnectionFailure.ProfileMissing), result)
        assertEquals(0, world.launcher.startCalls)
    }

    @Test
    fun `a profile no engine can run is refused without a service`() = runTest {
        val world = World(
            engine = FakeEngine(
                support = EngineSupport.Unsupported(UnsupportedRuntimeFeature.SECURITY),
            ),
        )

        val result = world.controller.connect(ProfileId(PROFILE_ID))

        assertEquals(
            ConnectResult.Rejected(
                ConnectionFailure.UnsupportedConfiguration(UnsupportedRuntimeFeature.SECURITY),
            ),
            result,
        )
        assertEquals(0, world.launcher.startCalls)
    }

    @Test
    fun `being offline is refused without a service and said out loud`() = runTest {
        val world = World(network = FakeNetworkStatus(underlyingAvailable = false))

        val result = world.controller.connect(ProfileId(PROFILE_ID))

        assertEquals(ConnectResult.Rejected(ConnectionFailure.NoInternet), result)
        assertEquals(VpnConnectionState.NoInternet, world.controller.snapshot.value.state)
        assertEquals(0, world.launcher.startCalls)
    }

    // -- permission ------------------------------------------------------------------------------

    @Test
    fun `missing consent asks and claims nothing`() = runTest {
        val world = World()
        world.launcher.consentMissing = true

        val result = world.controller.connect(ProfileId(PROFILE_ID))

        assertEquals(ConnectResult.PermissionRequired, result)
        assertEquals(
            VpnConnectionState.Idle,
            world.controller.snapshot.value.state,
            "a spinner behind the system dialog is a lie if the user says no",
        )
        assertEquals(0, world.launcher.startCalls)
    }

    @Test
    fun `connecting after consent is granted goes through`() = runTest {
        val world = World()
        world.launcher.consentMissing = true
        world.controller.connect(ProfileId(PROFILE_ID))

        world.launcher.consentMissing = false
        world.connectAndSettle()

        assertIs<VpnConnectionState.Connected>(world.controller.snapshot.value.state)
    }

    // -- lifecycle and races ---------------------------------------------------------------------

    @Test
    fun `disconnecting ends at idle and closes everything`() = runTest {
        val world = World()
        world.connectAndSettle()

        world.disconnectAndSettle()

        assertEquals(VpnConnectionState.Idle, world.controller.snapshot.value.state)
        assertEquals(0, world.host.openDescriptors.get())
        assertEquals(1, world.engine.stopCalls)
        assertEquals(1, world.host.shutdownCalls)
    }

    @Test
    fun `disconnecting during connecting cancels it and leaves nothing open`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val world = World()
        world.engine.startGate = gate

        world.connectAndSettle()
        assertIs<VpnConnectionState.Connecting>(world.controller.snapshot.value.state)

        world.disconnectAndSettle()

        assertEquals(VpnConnectionState.Idle, world.controller.snapshot.value.state)
        assertEquals(0, world.host.openDescriptors.get())
        assertEquals(0, world.probe.calls, "a cancelled connection still ran its readiness probe")
    }

    @Test
    fun `a service that dies under a live tunnel is a failure with a cause`() = runTest {
        val world = World()
        world.connectAndSettle()

        world.hostGone()

        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.ServiceStopped),
            world.controller.snapshot.value.state,
        )
        assertEquals(0, world.host.openDescriptors.get())
    }

    @Test
    fun `the death of a service we no longer hold changes nothing`() = runTest {
        // Service death is asynchronous; only this session's service dying may tear the tunnel down.
        val world = World()
        world.connectAndSettle()
        val before = world.controller.snapshot.value

        world.controller.onHostGone(FakeTunnelHost())
        world.settle()

        assertEquals(before, world.controller.snapshot.value)
        assertEquals(1, world.host.openDescriptors.get(), "a live tunnel was torn down")
    }

    @Test
    fun `a service that goes away because we asked is not a failure`() = runTest {
        val world = World()
        world.connectAndSettle()

        world.disconnectAndSettle()
        world.hostGone()

        assertEquals(
            VpnConnectionState.Idle,
            world.controller.snapshot.value.state,
            "disconnecting produced an error toast",
        )
    }

    @Test
    fun `a tunnel that will not open reports it and holds nothing`() = runTest {
        val world = World(host = FakeTunnelHost(establishes = false))

        world.connectAndSettle()

        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.TunnelSetupFailed),
            world.controller.snapshot.value.state,
        )
        assertEquals(0, world.engine.startCalls, "the engine was started without an interface")
    }

    @Test
    fun `connecting to another profile replaces the tunnel rather than adding one`() = runTest {
        val world = World(
            profiles = FakeProfileRepository(
                listOf(testProfile(), testProfile(id = OTHER_PROFILE_ID, name = "Other")),
            ),
        )
        world.connectAndSettle()

        world.connectAndSettle(id = OTHER_PROFILE_ID)

        val state = world.controller.snapshot.value.state
        assertIs<VpnConnectionState.Connected>(state)
        assertEquals(ProfileId(OTHER_PROFILE_ID), state.profileId)
        assertEquals(1, world.host.openDescriptors.get(), "two interfaces were open at once")
        assertEquals(2, world.engine.startCalls)
    }

    @Test
    fun `a host arriving with no session behind it is told to go away`() = runTest {
        val world = World()

        world.hostAvailable()

        assertEquals(1, world.host.shutdownCalls)
        assertEquals(VpnConnectionState.Idle, world.controller.snapshot.value.state)
    }

    @Test
    fun `disconnecting from a failure clears it`() = runTest {
        val world = World(probe = FakeReadinessProbe(passes = false))
        world.connectAndSettle()
        assertIs<VpnConnectionState.Failed>(world.controller.snapshot.value.state)

        world.disconnectAndSettle()

        assertEquals(VpnConnectionState.Idle, world.controller.snapshot.value.state)
    }

    // -- the bug that broke the first device run -------------------------------------------------

    @Test
    fun `a live tunnel stays connected while a network exists under it`() = runTest {
        // Regression: the VPN becoming the default network was read as NoInternet. See `NetworkMonitorTest`.
        val world = World()
        world.connectAndSettle()
        assertIs<VpnConnectionState.Connected>(world.controller.snapshot.value.state)

        world.tick(times = 5)

        assertIs<VpnConnectionState.Connected>(
            world.controller.snapshot.value.state,
            "a healthy tunnel drifted off Connected on its own",
        )
    }

    @Test
    fun `losing the network under a live tunnel is NoInternet`() = runTest {
        val world = World()
        world.connectAndSettle()

        world.network.underlyingAvailable = false
        world.tick()

        assertEquals(VpnConnectionState.NoInternet, world.controller.snapshot.value.state)
    }

    @Test
    fun `the network coming back resumes the same session rather than starting a new one`() =
        runTest {
            val world = World()
            world.connectAndSettle()
            val since = (world.controller.snapshot.value.state as VpnConnectionState.Connected).since

            world.network.underlyingAvailable = false
            world.tick()
            world.network.underlyingAvailable = true
            world.tick()

            val state = world.controller.snapshot.value.state
            assertIs<VpnConnectionState.Connected>(state)
            assertEquals(since, state.since, "a network blip was reported as a new session")
            assertEquals(1, world.engine.startCalls, "the tunnel was rebuilt for a network blip")
        }

    @Test
    fun `a live tunnel reads no traffic counters and publishes nothing on its own`() = runTest {
        val world = World()
        world.connectAndSettle()
        val connected = world.controller.snapshot.value
        val redraws = world.host.published.size

        world.tick(times = 60)

        assertEquals(0, world.traffic.readCalls, "the tunnel polled the counters with nobody asking")
        assertSame(connected, world.controller.snapshot.value, "an idle minute changed the snapshot")
        assertEquals(redraws, world.host.published.size, "an idle minute redrew the notification")
    }

    @Test
    fun `traffic is read on request while connected and is nothing otherwise`() = runTest {
        val world = World()
        assertEquals(TrafficStats.NONE, world.controller.traffic())

        world.connectAndSettle()
        assertEquals(TrafficStats(rxBytes = 1024, txBytes = 512), world.controller.traffic())

        world.disconnectAndSettle()
        assertEquals(TrafficStats.NONE, world.controller.traffic())
    }

    @Test
    fun `a tunnel that carries nothing is not reported as a device with no internet`() = runTest {
        // A failed readiness probe is not NoInternet: the network is still there.
        val world = World(probe = FakeReadinessProbe(passes = false))
        assertTrue(world.network.underlyingAvailable)

        world.connectAndSettle()

        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable),
            world.controller.snapshot.value.state,
        )
        assertNotEquals(
            VpnConnectionState.NoInternet,
            world.controller.snapshot.value.state,
            "a tunnel that carried nothing was blamed on the device being offline",
        )
    }

    @Test
    fun `a tunnel that moves packets but cannot resolve a name is not connected`() = runTest {
        // Dead DNS must fail: `Connected` means a browser would work, not that a packet got out.
        val world = World(probe = FakeReadinessProbe(failAt = ReadinessStage.DNS))

        world.connectAndSettle()

        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable),
            world.controller.snapshot.value.state,
            "a tunnel that cannot resolve a name was reported as connected",
        )
    }

    @Test
    fun `a tunnel that resolves names but cannot carry a TLS session is not connected`() = runTest {
        val world = World(probe = FakeReadinessProbe(failAt = ReadinessStage.DNS))

        world.connectAndSettle()

        assertEquals(
            VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable),
            world.controller.snapshot.value.state,
        )
    }

    @Test
    fun `every readiness stage failure is a tunnel problem and never a network one`() = runTest {
        // Every stage ends in the same state, and none of them is NoInternet.
        ReadinessStage.entries.forEach { stage ->
            val world = World(probe = FakeReadinessProbe(failAt = stage))
            assertTrue(world.network.underlyingAvailable)

            world.connectAndSettle()

            assertEquals(
                VpnConnectionState.Failed(ConnectionFailure.TunnelUnusable),
                world.controller.snapshot.value.state,
                "readiness stage $stage produced the wrong state",
            )
        }
    }

    @Test
    fun `disconnecting while the readiness check is running releases the attempt`() = runTest {
        // The device symptom was a connection stuck in `Connecting` with the readiness check never
        // answering. Whatever the check is doing, the user must be able to walk away from it.
        val world = World()
        world.probe.hangUntil = CompletableDeferred()

        world.connectAndSettle()
        assertIs<VpnConnectionState.Connecting>(world.controller.snapshot.value.state)

        world.disconnectAndSettle()

        assertEquals(VpnConnectionState.Idle, world.controller.snapshot.value.state)
        assertEquals(0, world.host.openDescriptors.get(), "a tun descriptor was left open")
        assertEquals(1, world.engine.stopCalls)
    }

    @Test
    fun `a connection cancelled during the readiness check can be started again`() = runTest {
        val world = World()
        val hang = CompletableDeferred<Unit>()
        world.probe.hangUntil = hang

        world.connectAndSettle()
        world.disconnectAndSettle()

        // The abandoned check finally answers, long after nobody is waiting for it.
        hang.complete(Unit)
        world.settle()

        world.probe.hangUntil = null
        world.connectAndSettle()

        assertIs<VpnConnectionState.Connected>(
            world.controller.snapshot.value.state,
            "a cancelled attempt poisoned the next one",
        )
        assertEquals(1, world.host.openDescriptors.get())
    }

    @Test
    fun `an engine that reports itself running is still not enough for connected`() = runTest {
        val world = World(probe = FakeReadinessProbe(passes = false))

        world.connectAndSettle()

        assertTrue(world.engine.startCalls > 0)
        assertEquals(1, world.probe.calls, "the readiness check did not run against a started engine")
        assertIs<VpnConnectionState.Failed>(
            world.controller.snapshot.value.state,
            "a started engine was taken for a working tunnel",
        )
    }

    @Test
    fun `a connection cancelled mid-flight cannot arrive later`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val world = World()
        world.engine.startGate = gate

        world.connectAndSettle()
        world.disconnectAndSettle()
        assertEquals(VpnConnectionState.Idle, world.controller.snapshot.value.state)

        // The engine finally answers, long after nobody is waiting for it.
        gate.complete(Unit)
        world.settle()
        world.tick(times = 3)

        assertEquals(
            VpnConnectionState.Idle,
            world.controller.snapshot.value.state,
            "a cancelled connection completed behind the user's back",
        )
        assertEquals(0, world.probe.calls)
    }

    // -- what a snapshot carries -----------------------------------------------------------------

    @Test
    fun `a connected snapshot carries a summary and diagnostics and no secret`() = runTest {
        val world = World()

        world.connectAndSettle()

        val snapshot = world.controller.snapshot.value
        assertEquals("Test relay", snapshot.subject?.displayName)
        assertEquals("vless", snapshot.diagnostics?.protocol)
        assertEquals("relay.example.net", snapshot.diagnostics?.endpointHost)
        assertTrue(
            "00000000-0000-4000-8000-000000000000" !in snapshot.toString(),
            "the user id reached a snapshot",
        )
    }

    /** The controller and its doubles, wired like `VpnRuntimeModule`, all on the test scheduler. */
    // A profile that arrives with the request: the same connection lifecycle.

    @Test
    fun `a stored request still resolves through the store`() = runTest {
        // The other half of the change: nothing about the custom path moved.
        val world = World()

        val result = world.controller.connect(ProfileId(PROFILE_ID))
        world.settle()

        assertEquals(ConnectResult.Started, result)
        val custom = assertIs<ConnectionSubject>(world.controller.snapshot.value.subject)
        assertEquals("Test relay", custom.profile.name)
    }

    private class World(
        val profiles: FakeProfileRepository = FakeProfileRepository(),
        val engine: FakeEngine = FakeEngine(),
        val host: FakeTunnelHost = FakeTunnelHost(),
        val launcher: FakeTunnelLauncher = FakeTunnelLauncher(),
        val network: FakeNetworkStatus = FakeNetworkStatus(),
        val probe: FakeReadinessProbe = FakeReadinessProbe(),
        val traffic: FakeTrafficSource = FakeTrafficSource(),
        val splitTunnel: SplitTunnel = SplitTunnel(),
    ) {
        /** The controller's own scheduler, deliberately not the test's. */
        private val scheduler = TestCoroutineScheduler()
        private val dispatcher = UnconfinedTestDispatcher(scheduler)

        val controller = VazieVpnConnectionController(
            profiles = profiles,
            engines = EngineRegistry(listOf(engine)),
            launcher = launcher,
            network = network,
            probe = probe,
            traffic = traffic,
            scope = CoroutineScope(dispatcher),
            ioDispatcher = dispatcher,
            splitTunnel = { splitTunnel },
        )

        init {
            launcher.onStart = { controller.onHostAvailable(host) }
            host.onShutdown = { gone -> controller.onHostGone(gone) }
        }

        /** Runs everything the controller has queued for right now, and nothing later. */
        fun settle() {
            scheduler.runCurrent()
        }

        suspend fun connectAndSettle(id: String = PROFILE_ID) {
            controller.connect(ProfileId(id))
            settle()
        }

        suspend fun disconnectAndSettle() {
            controller.disconnect()
            settle()
        }

        fun hostGone() {
            controller.onHostGone(host)
            settle()
        }

        fun hostAvailable() {
            controller.onHostAvailable(host)
            settle()
        }

        /** Lets [times] seconds pass under the live tunnel, delivering whatever the network did. */
        fun tick(times: Int = 1) {
            repeat(times) {
                scheduler.advanceTimeBy(SECOND_MILLIS)
                scheduler.runCurrent()
            }
        }

        private companion object {
            const val SECOND_MILLIS = 1_000L
        }
    }
}
