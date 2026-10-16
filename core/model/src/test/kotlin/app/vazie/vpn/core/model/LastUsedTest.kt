package app.vazie.vpn.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Recently" is a local question with a wrong answer available at every boundary. */
class LastUsedTest {

    @Test
    fun `never used is not just an old date`() {
        assertEquals(LastUsed.Never, LastUsed.of(lastUsedAt = null, now = NOW, zone = MOSCOW))
    }

    @Test
    fun `a connection made minutes ago still counts as current`() {
        assertEquals(
            LastUsed.JustNow,
            LastUsed.of(NOW.minusSeconds(240), NOW, MOSCOW),
        )
    }

    @Test
    fun `the just-now window closes`() {
        val outside = NOW.minus(LastUsed.JUST_NOW).minusSeconds(1)
        assertEquals(
            LastUsed.Today(hour = 14, minute = 54),
            LastUsed.of(outside, NOW, MOSCOW),
            "15:00 Moscow minus five minutes and a second is 14:54 the same day",
        )
    }

    @Test
    fun `today ends at local midnight, not twenty-four hours ago`() {
        // 22:30 UTC on the 24th is 01:30 Moscow on the 25th - today in Moscow, yesterday in UTC. A
        // bucketing that subtracted 24 hours would get both of these wrong in opposite directions.
        val lateNight = Instant.parse("2026-08-24T22:30:00Z")
        assertEquals(LastUsed.Today(hour = 1, minute = 30), LastUsed.of(lateNight, NOW, MOSCOW))
        assertEquals(LastUsed.Yesterday, LastUsed.of(lateNight, NOW, ZoneOffset.UTC))
    }

    @Test
    fun `yesterday is a calendar day, however few hours ago it was`() {
        val justAfterMidnight = Instant.parse("2026-08-25T09:00:00Z")
        assertEquals(
            LastUsed.Yesterday,
            LastUsed.of(
                lastUsedAt = Instant.parse("2026-08-24T23:59:00Z"),
                now = justAfterMidnight,
                zone = ZoneOffset.UTC,
            ),
            "sixty-one minutes earlier, and a different day",
        )
    }

    @Test
    fun `anything older carries its date`() {
        assertEquals(
            LastUsed.Earlier(year = 2026, month = 7, day = 3),
            LastUsed.of(Instant.parse("2026-07-03T08:15:00Z"), NOW, ZoneOffset.UTC),
        )
    }

    @Test
    fun `a clock corrected backwards does not produce a date in the future`() {
        // A clock change can put `lastUsedAt` after `now`; "Just now" is the harmless answer.
        assertEquals(
            LastUsed.JustNow,
            LastUsed.of(NOW.plusSeconds(90_000), NOW, MOSCOW),
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")
        val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")
    }
}
