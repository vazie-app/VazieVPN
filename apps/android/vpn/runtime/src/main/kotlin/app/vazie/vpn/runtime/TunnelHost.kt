package app.vazie.vpn.runtime

import app.vazie.vpn.api.TunSpec
import app.vazie.vpn.api.VpnConnectionSnapshot

/** What the controller can ask of the Android service, and nothing more. */
internal interface TunnelHost {

    /** Opens the interface described by [spec]. */
    fun establish(spec: TunSpec): TunnelHandle?

    /** Keeps [socketFd] outside the tunnel. `VpnService.protect`. */
    fun protect(socketFd: Int): Boolean

    /** Resolves a server hostname through encrypted DNS on a validated non-VPN network. */
    fun resolveIpv4(hostname: String): String?

    /** Redraws the foreground notification for [snapshot]. */
    fun publish(snapshot: VpnConnectionSnapshot)

    /** Ends the service. The controller calls this; nothing else does. */
    fun shutdown()
}

/** An open TUN interface. */
internal interface TunnelHandle {

    val descriptor: Int

    fun close()
}
