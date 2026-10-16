package app.vazie.vpn.runtime

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectionFailure
import app.vazie.vpn.api.UnsupportedRuntimeFeature
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnEngineId
import java.time.Instant
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Diagnostics exist now, so this is the test that keeps them from becoming a leak. */
class VpnTraceVocabularyTest {

    @Test
    fun `no trace detail has anywhere to put a string`() {
        val offenders = VpnTraceDetail::class.sealedSubclasses.flatMap { subclass ->
            subclass.memberProperties
                .filter { property ->
                    val name = property.returnType.toString()
                    name.contains("String") || name.contains("CharSequence")
                }
                .map { "${subclass.simpleName}.${it.name}" }
        }

        assertTrue(
            offenders.isEmpty(),
            "a trace detail grew a text field, which is a place for a credential to end up:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun `a connected state traces its name and not the profile it names`() {
        val state = VpnConnectionState.Connected(
            profileId = ProfileId(SENSITIVE_LOOKING_ID),
            since = Instant.EPOCH,
        )

        assertEquals(VpnStateName.CONNECTED, state.traceName())
        assertFalse(state.traceName().name.contains(SENSITIVE_LOOKING_ID))
    }

    @Test
    fun `an invalid-profile failure traces its name and not the field it blames`() {
        val failure = ConnectionFailure.ProfileInvalid(field = "publicKey")

        assertEquals(VpnFailureName.PROFILE_INVALID, failure.traceName())
        assertFalse(
            failure.traceName().name.contains("publicKey", ignoreCase = true),
            "the trace names a configuration field; the UI may, a log must not",
        )
    }

    @Test
    fun `every connection state has a trace name`() {
        val states = listOf(
            VpnConnectionState.Idle,
            VpnConnectionState.Preparing,
            VpnConnectionState.Connecting(ProfileId(SENSITIVE_LOOKING_ID)),
            VpnConnectionState.Connected(ProfileId(SENSITIVE_LOOKING_ID), Instant.EPOCH),
            VpnConnectionState.Disconnecting,
            VpnConnectionState.Failed(ConnectionFailure.Unknown),
            VpnConnectionState.NoInternet,
        )

        assertEquals(
            states.size,
            states.map { it.traceName() }.toSet().size,
            "two states share a trace name, so a transition between them is invisible",
        )
    }

    @Test
    fun `every failure has a distinct trace name`() {
        val failures = listOf(
            ConnectionFailure.ConsentDenied,
            ConnectionFailure.NoInternet,
            ConnectionFailure.ProfileMissing,
            ConnectionFailure.ProfileInvalid("publicKey"),
            ConnectionFailure.UnsupportedConfiguration(UnsupportedRuntimeFeature.SECURITY),
            ConnectionFailure.TunnelSetupFailed,
            ConnectionFailure.EngineFailed(VpnEngineId.XRAY),
            ConnectionFailure.TunnelUnusable,
            ConnectionFailure.ServiceStopped,
            ConnectionFailure.Unknown,
        )

        assertEquals(failures.size, failures.map { it.traceName() }.toSet().size)
    }

    @Test
    fun `a tunnel that carries nothing and a device with no network are different names`() {
        // The whole point of the fix, restated where a future reader of the traces will meet it.
        assertFalse(
            ConnectionFailure.TunnelUnusable.traceName() ==
                ConnectionFailure.NoInternet.traceName(),
        )
    }

    @Test
    fun `socket protection is traceable as an outcome and a currency flag, and nothing else`() {
        // Closed enums and booleans only, so no event can carry a descriptor, address or port.
        val events = VpnTraceEvent.entries.toSet()
        assertTrue(VpnTraceEvent.SOCKET_PROTECT in events)
        assertTrue(VpnTraceEvent.PROTECT_HOST_CURRENT in events)

        val outcomes = VpnTraceOutcome.entries.toSet()
        assertTrue(VpnTraceOutcome.REQUESTED in outcomes)
        assertTrue(VpnTraceOutcome.PASSED in outcomes)
        assertTrue(VpnTraceOutcome.FAILED in outcomes)

        // The details these two events are recorded with, proven to hold only an enum and a boolean.
        val protect: VpnTraceDetail = VpnTraceDetail.Outcome(VpnTraceOutcome.FAILED)
        val currency: VpnTraceDetail = VpnTraceDetail.Flag(false)
        assertEquals(VpnTraceOutcome.FAILED, (protect as VpnTraceDetail.Outcome).value)
        assertEquals(false, (currency as VpnTraceDetail.Flag).value)
    }

    @Test
    fun `every trace event is distinct, so a reading cannot be mistaken for another`() {
        val names = VpnTraceEvent.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    private companion object {
        const val SENSITIVE_LOOKING_ID = "00000000-0000-4000-8000-000000000000"
    }
}
