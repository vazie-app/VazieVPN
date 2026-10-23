package app.vazie.vpn.update.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** When the app is blocked, when it only hears about an update, and — above all — when it is not blocked at all. */
class AppUpdateCheckerTest {

    private var now = 1_000_000_000L
    private var answer: VersionAnswer = VersionAnswer.Published(POLICY)
    private var fetches = 0
    private val store = MemoryStore()

    private fun checker(installed: Int) = AppUpdateChecker(
        installedVersionCode = installed,
        source = AppVersionSource {
            fetches++
            answer
        },
        store = store,
        nowMillis = { now },
    )

    // --- the three states ---------------------------------------------------------------------------------

    @Test
    fun `an up to date build sees nothing`() = runTest {
        val checker = checker(installed = 42)
        checker.onForeground()

        assertEquals(UpdateStatus.UpToDate, checker.state.value.status)
        assertFalse(checker.state.value.offerOptional)
    }

    @Test
    fun `an older but supported build is offered the update`() = runTest {
        val checker = checker(installed = 40)
        checker.onForeground()

        assertIs<UpdateStatus.Optional>(checker.state.value.status)
        assertTrue(checker.state.value.offerOptional)
    }

    @Test
    fun `a build below the minimum is required to update`() = runTest {
        val checker = checker(installed = 38)
        checker.onForeground()

        assertIs<UpdateStatus.Required>(checker.state.value.status)
        assertFalse(checker.state.value.offerOptional, "a required update is not a polite offer")
    }

    // --- the backend being down never locks anyone out ----------------------------------------------------

    @Test
    fun `no answer and nothing remembered blocks nobody`() = runTest {
        answer = VersionAnswer.Failed
        val checker = checker(installed = 1)
        checker.onForeground()

        assertEquals(UpdateStatus.UpToDate, checker.state.value.status)
    }

    @Test
    fun `an error from the source itself blocks nobody`() = runTest {
        val checker = AppUpdateChecker(1, { throw IllegalStateException("boom") }, store, { now })
        checker.onForeground()

        assertEquals(UpdateStatus.UpToDate, checker.state.value.status)
    }

    @Test
    fun `nothing published means nothing required`() = runTest {
        answer = VersionAnswer.NotPublished
        val checker = checker(installed = 1)
        checker.onForeground()

        assertEquals(UpdateStatus.UpToDate, checker.state.value.status)
    }

    // --- a remembered requirement ------------------------------------------------------------------------

    @Test
    fun `a required status is remembered and blocks from the first frame of the next launch`() = runTest {
        checker(installed = 38).onForeground()
        assertEquals(POLICY, store.stored.policy)

        // A new process, offline: the file alone decides.
        answer = VersionAnswer.Failed
        val restarted = checker(installed = 38)
        assertIs<UpdateStatus.Required>(restarted.state.value.status, "not blocked before the first check")
        restarted.onForeground()
        assertIs<UpdateStatus.Required>(restarted.state.value.status, "a failed check lifted the block")
    }

    @Test
    fun `the block stays until a successful check says the build is supported`() = runTest {
        val checker = checker(installed = 38)
        checker.onForeground()

        answer = VersionAnswer.Failed
        assertEquals(ManualCheckResult.UpdateRequired(POLICY, verified = false), checker.checkNow())
        assertIs<UpdateStatus.Required>(checker.state.value.status)

        // The backend now supports this build (the minimum came down).
        answer = VersionAnswer.Published(POLICY.copy(minimumSupportedVersionCode = 30))
        assertIs<ManualCheckResult.UpdateAvailable>(checker.checkNow())
        assertIs<UpdateStatus.Optional>(checker.state.value.status)
    }

    @Test
    fun `nothing published lifts a remembered block`() = runTest {
        val checker = checker(installed = 38)
        checker.onForeground()
        answer = VersionAnswer.NotPublished

        assertEquals(ManualCheckResult.UpToDate, checker.checkNow())
        assertEquals(UpdateStatus.UpToDate, checker.state.value.status)
        assertNull(store.stored.policy)
    }

    @Test
    fun `installing the update lifts the block at once, with no network`() = runTest {
        checker(installed = 38).onForeground()
        answer = VersionAnswer.Failed

        val updated = checker(installed = 42)

        assertEquals(UpdateStatus.UpToDate, updated.state.value.status)
    }

    // --- when a check runs -------------------------------------------------------------------------------

    @Test
    fun `coming back to the foreground checks at most once an hour`() = runTest {
        val checker = checker(installed = 42)
        checker.onForeground()
        assertEquals(1, fetches)

        now += 59 * MINUTE
        checker.onForeground()
        assertEquals(1, fetches, "checked again within the hour")

        now += 2 * MINUTE
        checker.onForeground()
        assertEquals(2, fetches)
    }

    @Test
    fun `a failed check is retried sooner than an hour, but not at once`() = runTest {
        answer = VersionAnswer.Failed
        val checker = checker(installed = 42)
        checker.onForeground()

        now += 2 * MINUTE
        checker.onForeground()
        assertEquals(1, fetches)

        now += 4 * MINUTE
        checker.onForeground()
        assertEquals(2, fetches)
    }

    @Test
    fun `a cold start always checks`() = runTest {
        checker(installed = 42).onForeground()
        checker(installed = 42).onForeground()

        assertEquals(2, fetches)
    }

    // --- the manual check --------------------------------------------------------------------------------

    @Test
    fun `asking always asks, and an up to date build is told so`() = runTest {
        val checker = checker(installed = 42)
        checker.onForeground()

        assertEquals(ManualCheckResult.UpToDate, checker.checkNow())
        assertEquals(2, fetches, "the hourly limit applied to a check the person asked for")
    }

    @Test
    fun `asking reports an available update, required or not`() = runTest {
        assertEquals(ManualCheckResult.UpdateAvailable(POLICY), checker(installed = 40).checkNow())
        assertEquals(ManualCheckResult.UpdateRequired(POLICY), checker(installed = 38).checkNow())
    }

    @Test
    fun `asking with no network says so, and nothing is required because of it`() = runTest {
        answer = VersionAnswer.Failed
        val checker = checker(installed = 1)

        assertEquals(ManualCheckResult.Failed, checker.checkNow())
        assertEquals(UpdateStatus.UpToDate, checker.state.value.status)
        assertFalse(checker.state.value.checking)
    }

    // --- postponing --------------------------------------------------------------------------------------

    @Test
    fun `later postpones the offer and remembers it across launches`() = runTest {
        val checker = checker(installed = 40)
        checker.onForeground()
        checker.postpone()

        assertFalse(checker.state.value.offerOptional)
        assertIs<UpdateStatus.Optional>(checker.state.value.status, "postponing must not hide the status")

        val restarted = checker(installed = 40)
        assertFalse(restarted.state.value.offerOptional, "the postponement was forgotten with the process")
    }

    @Test
    fun `the offer returns after the postponement, and at once for a newer release`() = runTest {
        val checker = checker(installed = 40)
        checker.onForeground()
        checker.postpone()

        now += 2 * DAY
        assertFalse(checker(installed = 40).state.value.offerOptional)

        now += 2 * DAY
        assertTrue(checker(installed = 40).state.value.offerOptional, "still hidden after the postponement")

        // A newer release is news even inside the window.
        now -= 2 * DAY
        answer = VersionAnswer.Published(POLICY.copy(latestVersionCode = 43, latestVersionName = "1.5.0"))
        val again = checker(installed = 40)
        again.checkNow()
        assertTrue(again.state.value.offerOptional)
    }

    @Test
    fun `a manual check still reports a postponed update`() = runTest {
        val checker = checker(installed = 40)
        checker.onForeground()
        checker.postpone()

        assertEquals(ManualCheckResult.UpdateAvailable(POLICY), checker.checkNow())
    }

    @Test
    fun `a clock set back does not hide the offer for ever`() = runTest {
        val checker = checker(installed = 40)
        checker.onForeground()
        checker.postpone()

        now -= 30 * DAY
        assertTrue(checker(installed = 40).state.value.offerOptional)
    }

    @Test
    fun `postponing when nothing is on offer changes nothing`() = runTest {
        val checker = checker(installed = 42)
        checker.onForeground()
        checker.postpone()

        assertNull(store.stored.postponedVersionCode)
    }

    private class MemoryStore : AppUpdateStore {
        var stored = StoredUpdateState()
        override fun read() = stored
        override suspend fun write(state: StoredUpdateState) {
            stored = state
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
        val POLICY = AppVersionPolicy(42, "1.4.0", 39, "https://vazie.app/vpn", mapOf("en" to "Update Vazie VPN to continue."))
    }
}
