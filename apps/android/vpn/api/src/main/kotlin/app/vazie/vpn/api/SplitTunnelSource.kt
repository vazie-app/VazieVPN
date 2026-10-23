package app.vazie.vpn.api

import app.vazie.vpn.core.model.SplitTunnel

/** The person's split-tunnel choice, read by the runtime each time it builds a tunnel. */
fun interface SplitTunnelSource {
    fun current(): SplitTunnel

    companion object {
        /** Every app through the tunnel: tests, previews, a build without the setting. */
        val Off: SplitTunnelSource = SplitTunnelSource { SplitTunnel() }
    }
}
