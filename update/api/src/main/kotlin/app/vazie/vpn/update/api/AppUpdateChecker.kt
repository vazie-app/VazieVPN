package app.vazie.vpn.update.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one read of the version endpoint said. */
sealed interface VersionAnswer {

    data class Published(val policy: AppVersionPolicy) : VersionAnswer

    /** The backend answered that it publishes nothing for this app (`404 NOT_ENABLED`): nothing is required. */
    data object NotPublished : VersionAnswer

    /** No answer, or not one to believe: offline, a timeout, a server error, an invalid policy. Says nothing
     * about this build either way. */
    data object Failed : VersionAnswer
}

/** Reads the version endpoint. Without a session: an update check is never an account's. */
fun interface AppVersionSource {
    suspend fun fetch(): VersionAnswer
}

/** What is remembered between launches: the last policy the backend published, and when an optional update was
 * last postponed and for which release. */
data class StoredUpdateState(
    val policy: AppVersionPolicy? = null,
    val postponedVersionCode: Int? = null,
    val postponedAtMillis: Long? = null,
)

interface AppUpdateStore {
    /** Read once, at start, so a remembered "required" is in force before the first frame. Small and local. */
    fun read(): StoredUpdateState

    suspend fun write(state: StoredUpdateState)
}

/** What the app shows. */
data class AppUpdateState(
    val status: UpdateStatus,
    /** An optional update that has not been postponed: offer it, once, without getting in the way. */
    val offerOptional: Boolean = false,
    val checking: Boolean = false,
)

/** How a check the person asked for ended. */
sealed interface ManualCheckResult {
    data object UpToDate : ManualCheckResult
    data class UpdateAvailable(val policy: AppVersionPolicy) : ManualCheckResult
    /** [verified] `false` when the backend could not be asked and the remembered requirement stands. */
    data class UpdateRequired(val policy: AppVersionPolicy, val verified: Boolean = true) : ManualCheckResult

    /** The backend could not be asked; say so and offer to try again. */
    data object Failed : ManualCheckResult
}

/**
 * When this build must update, may update, or need not — and what that rests on.
 *
 * Checked at a cold start, on coming back to the foreground at most once per [foregroundIntervalMillis] (sooner,
 * [retryAfterFailureMillis], after a failed check), and whenever the person asks. The rules that keep it from
 * locking people out:
 * - a failed check changes nothing: without a remembered policy that makes this build unsupported, the app is not
 *   blocked because the backend could not be reached;
 * - a remembered "required" stays in force — offline included — until a successful check says this build is
 *   supported (or the backend publishes nothing), and it is recomputed against the installed code, so installing
 *   the update lifts it at once;
 * - an optional update is offered, and "later" postpones it for [postponeMillis], or until a newer release.
 *
 * Nothing here is on the connection path: the tunnel neither waits for a check nor is stopped by one.
 */
class AppUpdateChecker(
    private val installedVersionCode: Int,
    private val source: AppVersionSource,
    private val store: AppUpdateStore,
    private val nowMillis: () -> Long,
    private val foregroundIntervalMillis: Long = HOUR_MILLIS,
    private val retryAfterFailureMillis: Long = RETRY_AFTER_FAILURE_MILLIS,
    private val postponeMillis: Long = POSTPONE_MILLIS,
) {

    private val mutex = Mutex()
    private var stored: StoredUpdateState = store.read()

    /** When the last check finished, and whether it failed. In memory: every cold start checks. */
    private var lastCheck: Pair<Long, Boolean>? = null

    private val _state = MutableStateFlow(stateFrom(stored, checking = false))
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** The app came to the foreground (the first time in a process is the cold start). Checks unless one did
     * recently; never waits behind a check already running. */
    suspend fun onForeground() {
        val last = lastCheck
        if (last != null) {
            val (at, failed) = last
            val interval = if (failed) retryAfterFailureMillis else foregroundIntervalMillis
            if (nowMillis() - at < interval) return
        }
        if (!mutex.tryLock()) return
        try {
            check()
        } finally {
            mutex.unlock()
        }
    }

    /** "Check for updates": always asks the backend, and says how it went. An available update is reported even
     * if it was postponed — the person asked. */
    suspend fun checkNow(): ManualCheckResult = mutex.withLock {
        when (check()) {
            VersionAnswer.Failed -> when (val status = _state.value.status) {
                // Still unable to ask, but a remembered requirement stands.
                is UpdateStatus.Required -> ManualCheckResult.UpdateRequired(status.policy, verified = false)
                else -> ManualCheckResult.Failed
            }
            else -> when (val status = _state.value.status) {
                UpdateStatus.UpToDate -> ManualCheckResult.UpToDate
                is UpdateStatus.Optional -> ManualCheckResult.UpdateAvailable(status.policy)
                is UpdateStatus.Required -> ManualCheckResult.UpdateRequired(status.policy)
            }
        }
    }

    /** "Later" on an optional update: not offered again for a while, unless a newer release appears. */
    suspend fun postpone() = mutex.withLock {
        val status = _state.value.status as? UpdateStatus.Optional ?: return@withLock
        stored = stored.copy(postponedVersionCode = status.policy.latestVersionCode, postponedAtMillis = nowMillis())
        store.write(stored)
        _state.value = stateFrom(stored, checking = false)
    }

    private suspend fun check(): VersionAnswer {
        _state.value = _state.value.copy(checking = true)
        val answer = try {
            source.fetch()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            _state.value = _state.value.copy(checking = false)
            throw cancellation
        } catch (_: Exception) {
            VersionAnswer.Failed
        }
        when (answer) {
            is VersionAnswer.Published -> remember(stored.copy(policy = answer.policy))
            VersionAnswer.NotPublished -> remember(stored.copy(policy = null))
            // Nothing learned: what was remembered, a requirement included, stays as it is.
            VersionAnswer.Failed -> Unit
        }
        lastCheck = nowMillis() to (answer == VersionAnswer.Failed)
        _state.value = stateFrom(stored, checking = false)
        return answer
    }

    private suspend fun remember(next: StoredUpdateState) {
        if (next == stored) return
        stored = next
        store.write(next)
    }

    private fun stateFrom(stored: StoredUpdateState, checking: Boolean): AppUpdateState {
        val status = stored.policy?.statusFor(installedVersionCode) ?: UpdateStatus.UpToDate
        val offer = status is UpdateStatus.Optional && !isPostponed(stored, status.policy)
        return AppUpdateState(status = status, offerOptional = offer, checking = checking)
    }

    private fun isPostponed(stored: StoredUpdateState, policy: AppVersionPolicy): Boolean {
        val code = stored.postponedVersionCode ?: return false
        val at = stored.postponedAtMillis ?: return false
        // A newer release than the one postponed is news; the same one waits out the postponement. A clock set
        // back before the postponement counts as not waited out, rather than as for ever.
        val elapsed = nowMillis() - at
        return code >= policy.latestVersionCode && elapsed in 0 until postponeMillis
    }

    companion object {
        const val HOUR_MILLIS: Long = 60 * 60 * 1000L
        const val RETRY_AFTER_FAILURE_MILLIS: Long = 5 * 60 * 1000L
        const val POSTPONE_MILLIS: Long = 3 * 24 * HOUR_MILLIS
    }
}
