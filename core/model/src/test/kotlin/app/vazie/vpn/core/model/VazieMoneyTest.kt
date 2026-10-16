package app.vazie.vpn.core.model

import app.vazie.vpn.core.model.VazieMoney.Companion.dividedEvenly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Money is an integer count of the smallest unit, and the arithmetic on it is exact. */
class VazieMoneyTest {

    @Test
    fun `an amount is a count of minor units`() {
        assertEquals(29_900L, VazieMoney.ofMajorUnits(299).minorUnits)
        assertEquals(299L, VazieMoney.ofMajorUnits(299).majorUnits)
        assertEquals(0L, VazieMoney.Zero.minorUnits)
    }

    /** The major-unit view rounds half-up, because it is what gets printed. */
    @Test
    fun `major units round half-up`() {
        assertEquals(4L, VazieMoney(449).majorUnits)
        assertEquals(5L, VazieMoney(450).majorUnits)
        assertEquals(5L, VazieMoney(451).majorUnits)
    }

    /** Multiplication is exact, which is the whole reason this is not a `Double`. */
    @Test
    fun `multiplication is exact`() {
        assertEquals(VazieMoney.ofMajorUnits(897), VazieMoney.ofMajorUnits(299) * 3)
        assertEquals(VazieMoney.ofMajorUnits(5_988), VazieMoney.ofMajorUnits(499) * 12)
    }

    @Test
    fun `subtraction is exact`() {
        assertEquals(
            VazieMoney.ofMajorUnits(1_089),
            VazieMoney.ofMajorUnits(3_588) - VazieMoney.ofMajorUnits(2_499),
        )
    }

    /** Dividing into a per-period figure rounds half-up to the whole major unit. */
    @Test
    fun `dividing evenly rounds half-up to a whole major unit`() {
        assertEquals(266L, VazieMoney.ofMajorUnits(799).dividedEvenly(3).majorUnits)
        assertEquals(250L, VazieMoney.ofMajorUnits(1_499).dividedEvenly(6).majorUnits)
        assertEquals(208L, VazieMoney.ofMajorUnits(2_499).dividedEvenly(12).majorUnits)
        assertEquals(450L, VazieMoney.ofMajorUnits(1_349).dividedEvenly(3).majorUnits)
        // 2 499 / 6 is 416.5 exactly — the halfway case, and it must go up.
        assertEquals(417L, VazieMoney.ofMajorUnits(2_499).dividedEvenly(6).majorUnits)
        assertEquals(333L, VazieMoney.ofMajorUnits(3_999).dividedEvenly(12).majorUnits)
    }

    @Test
    fun `dividing by one is the amount itself`() {
        val amount = VazieMoney.ofMajorUnits(499)
        assertEquals(amount, amount.dividedEvenly(1))
    }

    @Test
    fun `amounts compare by value`() {
        assertTrue(VazieMoney.ofMajorUnits(208) < VazieMoney.ofMajorUnits(299))
        assertEquals(VazieMoney.ofMajorUnits(299), VazieMoney(29_900))
    }

    /** A negative amount and a division into no parts are both rejected at construction. */
    @Test
    fun `impossible amounts are rejected`() {
        assertFailsWith<IllegalArgumentException> { VazieMoney(-1) }
        assertFailsWith<IllegalArgumentException> { VazieMoney.ofMajorUnits(299).dividedEvenly(0) }
    }
}
