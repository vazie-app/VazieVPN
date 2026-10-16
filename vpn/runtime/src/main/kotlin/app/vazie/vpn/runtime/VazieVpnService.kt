package app.vazie.vpn.runtime

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import app.vazie.vpn.api.TunSpec
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.core.model.SplitTunnel
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** The Android half of the tunnel: it owns the interface, the notification and its own lifetime, and it owns
 * no state. */
@AndroidEntryPoint
class VazieVpnService : VpnService() {

    @Inject
    lateinit var controller: VazieVpnConnectionController

    @Inject
    lateinit var notifications: VpnNotifications

    private val host = ServiceTunnelHost()
    private val goneReported = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            VpnNotifications.NOTIFICATION_ID,
            notifications.build(snapshot = null),
        )
        when (intent?.action) {
            ACTION_DISCONNECT -> controller.onStopRequested()
            else -> controller.onHostAvailable(host)
        }
        // NOT_STICKY: never resurrect a tunnel behind the user's back.
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        reportGone()
        super.onRevoke()
    }

    override fun onDestroy() {
        reportGone()
        host.release()
        super.onDestroy()
    }

    private fun reportGone() {
        if (goneReported.compareAndSet(false, true)) controller.onHostGone(host)
    }

    private inner class ServiceTunnelHost : TunnelHost {

        private var handle: ParcelTunnelHandle? = null
        // Lazy: the base context is attached after construction; an eager lookup crashed every connection.
        private val bootstrapResolver by lazy {
            UnderlyingNetworkDnsResolver(this@VazieVpnService)
        }

        override fun establish(spec: TunSpec): TunnelHandle? {
            val builder = Builder()
                .setSession(spec.sessionName)
                .setMtu(spec.mtu)
                // Blocking mode: the engine's stack polls the descriptor and chooses its own mode.
                .setBlocking(true)
            spec.addresses.forEach { builder.addAddress(it.address, it.prefixLength) }
            spec.routes.forEach { builder.addRoute(it.address, it.prefixLength) }
            spec.dnsServers.forEach { builder.addDnsServer(it) }
            applySplitTunnel(builder, spec.splitTunnel)
            // Vazie stays inside its own tunnel; only `protect` decides what leaves it.
            val opened = runCatching { builder.establish() }.getOrNull() ?: return null
            return ParcelTunnelHandle(opened).also { handle = it }
        }

        /** Apps the person left out, or the only ones let in. Vazie itself is never left out, so the readiness
         * check tests the real path; an app since uninstalled is skipped. */
        private fun applySplitTunnel(builder: Builder, split: SplitTunnel) {
            if (!split.isActive) return
            val own = this@VazieVpnService.packageName
            when (split.mode) {
                SplitTunnel.Mode.EXCLUDE -> split.packages.filter { it != own }
                    .forEach { runCatching { builder.addDisallowedApplication(it) } }
                SplitTunnel.Mode.INCLUDE -> (split.packages + own)
                    .forEach { runCatching { builder.addAllowedApplication(it) } }
                SplitTunnel.Mode.OFF -> Unit
            }
        }

        /** Qualified on purpose: an unqualified `protect(...)` inside this inner class resolves to this very
         * method. */
        override fun protect(socketFd: Int): Boolean =
            this@VazieVpnService.protect(socketFd)

        override fun resolveIpv4(hostname: String): String? =
            bootstrapResolver.resolveIpv4(hostname)

        override fun publish(snapshot: VpnConnectionSnapshot) {
            notifications.update(this@VazieVpnService, snapshot)
        }

        override fun shutdown() {
            release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        fun release() {
            handle?.close()
            handle = null
        }
    }

    private class ParcelTunnelHandle(
        private val parcel: ParcelFileDescriptor,
    ) : TunnelHandle {

        private val closed = AtomicBoolean(false)

        override val descriptor: Int get() = parcel.fd

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { parcel.close() }
        }
    }

    companion object {

        /** The two things this service can be asked to do, named under the application's own id. */
        const val ACTION_CONNECT: String = "app.vazie.vpn.action.CONNECT"
        const val ACTION_DISCONNECT: String = "app.vazie.vpn.action.DISCONNECT"
    }
}
