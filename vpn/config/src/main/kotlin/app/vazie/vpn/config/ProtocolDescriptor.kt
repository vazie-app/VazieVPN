package app.vazie.vpn.config

import app.vazie.vpn.api.VpnEngineId

/** What a parser recognized, in terms the UI can render without knowing the protocol. */
data class ProtocolDescriptor(
    /** Stable machine name — `vless`. Never shown to the user. */
    val id: String,
    /** Protocol name as every client spells it — `VLESS`. The same in every language. */
    val label: String,
    /** The two-letter mark the design draws on a configuration row — `VL`. */
    val mark: String,
    val engineId: VpnEngineId,
) {
    companion object {
        val VLESS = ProtocolDescriptor(
            id = "vless",
            label = "VLESS",
            mark = "VL",
            engineId = VpnEngineId.XRAY,
        )
    }
}
