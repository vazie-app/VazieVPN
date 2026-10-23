package app.vazie.vpn.runtime

import android.util.Log

/** The debug build's lifecycle trace. */
internal object VpnTrace {

    fun record(event: VpnTraceEvent, detail: VpnTraceDetail = VpnTraceDetail.None) {
        Log.d(TAG, "${event.name}${detail.render()}")
    }

    private fun VpnTraceDetail.render(): String = when (this) {
        VpnTraceDetail.None -> ""
        is VpnTraceDetail.Flag -> " $value"
        is VpnTraceDetail.Outcome -> " ${value.name}"
        is VpnTraceDetail.Transition -> " ${from.name} -> ${to.name}"
        is VpnTraceDetail.Failure -> " ${value.name}"
    }

    private const val TAG = "VazieVpn"
}
