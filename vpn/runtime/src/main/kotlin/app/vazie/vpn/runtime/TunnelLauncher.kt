package app.vazie.vpn.runtime

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/** The two Android facts the controller needs, behind a seam it can fake. */
internal interface TunnelLauncher {

    /** Whether Android still has to ask the user. */
    fun consentRequired(): Boolean

    /** Ask Android to start the tunnel service, and say whether it agreed to. */
    fun start(): Boolean
}

internal class AndroidTunnelLauncher(private val context: Context) : TunnelLauncher {

    override fun consentRequired(): Boolean = VpnService.prepare(context) != null

    override fun start(): Boolean = runCatching {
        ContextCompat.startForegroundService(
            context,
            Intent(context, VazieVpnService::class.java).setAction(VazieVpnService.ACTION_CONNECT),
        )
    }.isSuccess
}
