package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import java.time.Instant

/** The engines Vazie can hold a profile for. */
enum class VpnEngineId { XRAY }

/** Where a profile came from. */
enum class ProfileOrigin {
    /** Pasted, scanned or opened as a share link. */
    IMPORTED_LINK,

}

/** A stored VPN configuration, normalized into Vazie's own shape. */
sealed interface VpnProfile {
    val id: ProfileId
    val name: String
    val engineId: VpnEngineId

    /** Where a stored profile came from, and `null` when there is no answer because the profile was never
     * stored. */
    val origin: ProfileOrigin?
    val createdAt: Instant
    val lastUsedAt: Instant?

    data class Xray(
        override val id: ProfileId,
        override val name: String,
        override val origin: ProfileOrigin?,
        override val createdAt: Instant,
        override val lastUsedAt: Instant? = null,
        val outbound: XrayOutbound,
    ) : VpnProfile {
        override val engineId: VpnEngineId get() = VpnEngineId.XRAY
    }
}
