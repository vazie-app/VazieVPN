package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.VpnProfile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The profile store holds what the user owns, and refuses what it does not. */
class EphemeralProfileIsNotStorableTest {

    @Test
    fun `a profile with no origin cannot be turned into a store record`() {
        val ephemeral = VpnProfile.Xray(
            id = ProfileId("ephemeral-profile"),
            name = "Home server",
            origin = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            outbound = TestProfiles.realityDraft().outbound,
        )

        val failure = assertFailsWith<IllegalStateException> { ephemeral.toRecord() }

        assertTrue(failure.message.orEmpty().contains("never stored"))
    }

    @Test
    fun `a stored origin still round-trips`() {
        // The guard must not have cost the origin that is real. There used to be two here; `BUILT_IN`
        // went with the servers Vazie shipped, and `IMPORTED_LINK` is now the only way in.
        listOf(ProfileOrigin.IMPORTED_LINK).forEach { origin ->
            val stored = VpnProfile.Xray(
                id = ProfileId("an-id"),
                name = "A name",
                origin = origin,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                outbound = TestProfiles.realityDraft().outbound,
            )

            assertTrue(stored.toRecord().origin.isNotBlank())
        }
    }
}
