package app.vazie.vpn.feature.home

import app.vazie.vpn.api.TrafficStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The traffic readings Home and Connection details show: units, precision and the unknown counter. */
class TrafficReadingTest {

    @Test
    fun `traffic below a megabyte is reported rather than rounded away`() {
        val reading = assertNotNull(trafficReading(812L * 1024))
        assertEquals("812", reading.amount)
        assertEquals(R.string.home_unit_kilobytes, reading.unitRes)
    }

    @Test
    fun `megabytes and gigabytes carry one decimal`() {
        assertEquals("214.1", assertNotNull(trafficReading(214L * 1024 * 1024 + 200 * 1024)).amount)
        assertEquals(R.string.home_unit_megabytes, assertNotNull(trafficReading(MEGABYTE)).unitRes)
        assertEquals("1.5", assertNotNull(trafficReading(GIGABYTE + GIGABYTE / 2)).amount)
        assertEquals(R.string.home_unit_gigabytes, assertNotNull(trafficReading(GIGABYTE)).unitRes)
    }

    @Test
    fun `bytes are whole`() {
        assertEquals("0", assertNotNull(trafficReading(0)).amount)
        assertEquals(R.string.home_unit_bytes, assertNotNull(trafficReading(0)).unitRes)
    }

    /** The distinction the old whole-megabyte mapping could not make. */
    @Test
    fun `an unavailable counter is not zero`() {
        assertNull(trafficReading(TrafficStats.UNKNOWN))
        assertNotNull(trafficReading(0), "zero bytes is a measurement and must survive")
    }

    /** Rounding down, because a counter that rounds up reports traffic that has not happened. */
    @Test
    fun `amounts round down`() {
        assertEquals("1.9", assertNotNull(trafficReading(MEGABYTE * 2 - 1)).amount)
    }

    private companion object {
        const val MEGABYTE = 1024L * 1024L
        const val GIGABYTE = MEGABYTE * 1024L
    }
}
