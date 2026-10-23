package app.vazie.vpn.feature.home

import androidx.annotation.StringRes
import app.vazie.vpn.api.TrafficStats

/** A byte count as a number and the unit it is in, or `null` when the device will not say. */
internal data class TrafficReading(val amount: String, @param:StringRes val unitRes: Int)

internal fun trafficReading(bytes: Long): TrafficReading? = when {
    bytes < 0 || bytes == TrafficStats.UNKNOWN -> null
    bytes < KILOBYTE -> TrafficReading(bytes.toString(), R.string.home_unit_bytes)
    bytes < MEGABYTE -> TrafficReading((bytes / KILOBYTE).toString(), R.string.home_unit_kilobytes)
    bytes < GIGABYTE -> TrafficReading(oneDecimal(bytes, MEGABYTE), R.string.home_unit_megabytes)
    else -> TrafficReading(oneDecimal(bytes, GIGABYTE), R.string.home_unit_gigabytes)
}

/** Rounded down, never up, and formatted with `Locale.ROOT`. */
private fun oneDecimal(bytes: Long, unit: Long): String {
    val tenths = bytes * TENTHS / unit
    return "${tenths / TENTHS}.${tenths % TENTHS}"
}

private const val TENTHS = 10L
private const val KILOBYTE = 1024L
private const val MEGABYTE = KILOBYTE * 1024L
private const val GIGABYTE = MEGABYTE * 1024L
