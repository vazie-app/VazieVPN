package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.VpnProfileRepository
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Which profile is selected, kept in one small plain file beside the encrypted store. */
internal class FileSelectedProfileStore(
    private val file: File,
    private val profiles: VpnProfileRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SelectedProfileStore {

    private val mutex = Mutex()
    private val cache = MutableStateFlow<String?>(null)
    private var loaded = false

    override fun observeSelected(): Flow<ProfileId?> = combine(
        cache.onStart { mutex.withLock { loadLocked() } },
        profiles.observeSummaries(),
    ) { selected, summaries ->
        // Only a stored profile can stay selected; anything else is forgotten.
        val known = selected?.takeIf { id -> summaries.any { it.id.value == id } }
        if (selected != null && known == null) mutex.withLock { writeLocked(null) }
        known?.let(::ProfileId)
    }.distinctUntilChanged()

    override suspend fun selected(): ProfileId? {
        val stored = mutex.withLock { loadLocked() } ?: return null
        val exists = profiles.details(ProfileId(stored)) != null
        if (!exists) {
            mutex.withLock { writeLocked(null) }
            return null
        }
        return ProfileId(stored)
    }

    override suspend fun select(id: ProfileId) {
        mutex.withLock { writeLocked(id.value) }
    }

    override suspend fun clear() {
        mutex.withLock { writeLocked(null) }
    }

    /** Must be called while holding [mutex]. */
    private suspend fun loadLocked(): String? {
        if (!loaded) {
            cache.value = withContext(dispatcher) {
                runCatching { file.takeIf { it.exists() }?.readText()?.trim() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
            loaded = true
        }
        return cache.value
    }

    /** Must be called while holding [mutex]. */
    private suspend fun writeLocked(id: String?) {
        withContext(dispatcher) {
            runCatching {
                if (id == null) {
                    file.delete()
                } else {
                    file.parentFile?.mkdirs()
                    file.writeText(id)
                }
            }
        }
        loaded = true
        cache.value = id
    }
}
