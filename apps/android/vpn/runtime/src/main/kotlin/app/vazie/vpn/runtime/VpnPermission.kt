package app.vazie.vpn.runtime

import android.content.Context
import android.content.Intent
import android.net.VpnService

/** The system VPN consent dialog, for whoever has an `Activity` to show it with. */
object VpnPermission {

    /** The consent `Intent`, or `null` when Android has already agreed. */
    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)
}
