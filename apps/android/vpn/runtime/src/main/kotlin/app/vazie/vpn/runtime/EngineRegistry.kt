package app.vazie.vpn.runtime

import app.vazie.vpn.api.VpnEngine
import app.vazie.vpn.api.VpnProfile

/** The engines this build carries. */
class EngineRegistry(private val engines: List<VpnEngine>) {

    fun forProfile(profile: VpnProfile): VpnEngine? =
        engines.firstOrNull { it.id == profile.engineId }
}
