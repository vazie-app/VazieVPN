package app.vazie.vpn.api

import app.vazie.vpn.core.model.ProfileId
import java.time.Instant

/** A configuration that has been understood but not yet saved. */
sealed interface ProfileDraft {

    val engineId: VpnEngineId

    /** The name the source suggested — a share link's fragment — or `null` when it had none. The caller
     * decides what to fall back to; a parser has no business choosing product copy. */
    val suggestedName: String?

    /** The endpoint, so a caller can name a profile after its host without unwrapping the outbound. */
    val endpoint: Endpoint

    fun toProfile(
        id: ProfileId,
        name: String,
        origin: ProfileOrigin,
        createdAt: Instant,
    ): VpnProfile

    data class Xray(
        override val suggestedName: String?,
        val outbound: XrayOutbound,
    ) : ProfileDraft {

        override val engineId: VpnEngineId get() = VpnEngineId.XRAY

        override val endpoint: Endpoint get() = outbound.endpoint

        override fun toProfile(
            id: ProfileId,
            name: String,
            origin: ProfileOrigin,
            createdAt: Instant,
        ): VpnProfile = VpnProfile.Xray(
            id = id,
            name = name,
            origin = origin,
            createdAt = createdAt,
            outbound = outbound,
        )
    }
}
