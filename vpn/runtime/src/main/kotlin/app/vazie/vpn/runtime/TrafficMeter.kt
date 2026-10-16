package app.vazie.vpn.runtime

import android.net.TrafficStats
import android.os.Process
import javax.inject.Inject
import javax.inject.Singleton
import app.vazie.vpn.api.TrafficStats as VazieTrafficStats

/** How much has moved through the tunnel, taken from the OS rather than invented. */
@Singleton
class TrafficMeter @Inject constructor() : TrafficSource {

    @Volatile
    private var baselineRx: Long = 0
    @Volatile
    private var baselineTx: Long = 0
    @Volatile
    private var available: Boolean = false

    /** Remembers where the counters stood, so a session reports its own bytes and not the day's. */
    override fun start() {
        val uid = Process.myUid()
        baselineRx = TrafficStats.getUidRxBytes(uid)
        baselineTx = TrafficStats.getUidTxBytes(uid)
        available = baselineRx != TrafficStats.UNSUPPORTED.toLong() &&
            baselineTx != TrafficStats.UNSUPPORTED.toLong()
    }

    override fun read(): VazieTrafficStats {
        if (!available) return VazieTrafficStats.UNAVAILABLE
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return VazieTrafficStats.UNAVAILABLE
        }
        return VazieTrafficStats(
            rxBytes = (rx - baselineRx).coerceAtLeast(0),
            txBytes = (tx - baselineTx).coerceAtLeast(0),
        )
    }
}
