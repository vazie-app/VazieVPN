package app.vazie.vpn.core.model

/** An amount of money, in **minor units**. */
@JvmInline
value class VazieMoney(val minorUnits: Long) : Comparable<VazieMoney> {

    init {
        require(minorUnits >= 0) { "money cannot be negative: $minorUnits" }
    }

    /** The whole-unit part, rounded half-up. */
    val majorUnits: Long get() = (minorUnits + MINOR_UNITS_PER_MAJOR / 2) / MINOR_UNITS_PER_MAJOR

    operator fun times(count: Int): VazieMoney = VazieMoney(minorUnits * count)

    operator fun minus(other: VazieMoney): VazieMoney = VazieMoney(minorUnits - other.minorUnits)

    override fun compareTo(other: VazieMoney): Int = minorUnits.compareTo(other.minorUnits)

    companion object {

        const val MINOR_UNITS_PER_MAJOR: Long = 100

        /** The zero amount — `Vazie Free`, and the only price that is not a decision. */
        val Zero = VazieMoney(0)

        fun ofMajorUnits(majorUnits: Long) = VazieMoney(majorUnits * MINOR_UNITS_PER_MAJOR)

        /** This amount split evenly across [parts], rounded half-up to the nearest **whole major unit**. */
        fun VazieMoney.dividedEvenly(parts: Int): VazieMoney {
            require(parts > 0) { "cannot divide money into $parts parts" }
            // Minor units in one major unit of the result, so the halfway case — which `2 499 ₽ /
            // 6` hits exactly — rounds up rather than down.
            val divisor = parts * MINOR_UNITS_PER_MAJOR
            return ofMajorUnits((minorUnits + divisor / 2) / divisor)
        }
    }
}
