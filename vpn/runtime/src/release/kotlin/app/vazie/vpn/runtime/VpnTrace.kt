package app.vazie.vpn.runtime

/** The release build's lifecycle trace: nothing. */
internal object VpnTrace {

    fun record(event: VpnTraceEvent, detail: VpnTraceDetail = VpnTraceDetail.None) = Unit
}
