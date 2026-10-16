package app.vazie.vpn.core.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** How long ago a configuration last carried traffic, as something a screen can say out loud. */
sealed interface LastUsed {

    /** Never connected. */
    data object Never : LastUsed

    /** Within the last few minutes. */
    data object JustNow : LastUsed

    /** Earlier today, at [hour]:[minute]. */
    data class Today(val hour: Int, val minute: Int) : LastUsed

    /** Some time yesterday. */
    data object Yesterday : LastUsed

    /** Longer ago than that, on [year]-[month]-[day]. */
    data class Earlier(val year: Int, val month: Int, val day: Int) : LastUsed

    companion object {
        val JUST_NOW: Duration = Duration.ofMinutes(5)

        /** [zone] is passed in because "today" is local, and tests choose their own. */
        fun of(lastUsedAt: Instant?, now: Instant, zone: ZoneId): LastUsed {
            if (lastUsedAt == null) return Never
            // A clock that moved backwards - a manual change, an NTP correction - would otherwise
            // produce a "last used" in the future, which has no bucket and no meaning.
            if (lastUsedAt.isAfter(now)) return JustNow
            if (Duration.between(lastUsedAt, now) < JUST_NOW) return JustNow

            val then = lastUsedAt.atZone(zone).toLocalDateTime()
            val today = now.atZone(zone).toLocalDate()
            return when (then.toLocalDate()) {
                today -> Today(hour = then.hour, minute = then.minute)
                today.minusDays(1) -> Yesterday
                else -> Earlier(
                    year = then.year,
                    month = then.monthValue,
                    day = then.dayOfMonth,
                )
            }
        }
    }
}
