package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieLink
import app.vazie.vpn.core.model.VazieLinks
import app.vazie.vpn.core.model.VazieLocalizedText
import app.vazie.vpn.core.network.VazieApiClient
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * The About screen's links: the last list the backend published, kept on disk so a cold start offline shows it
 * too. `null` until the backend has answered once — the screen then shows the links the app shipped with.
 *
 * [refresh] asks at most once per [minIntervalMillis] and never twice at the same time; a failure keeps what is
 * there.
 */
class AppLinksRepository internal constructor(
    private val source: LinksSource,
    private val file: File,
    private val nowMillis: () -> Long,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val mutex = Mutex()
    private var lastAskedAtMillis: Long? = null
    private val state = MutableStateFlow<VazieLinks?>(null)
    private var loaded = false

    val links: StateFlow<VazieLinks?> = state.asStateFlow()

    /** Reads the stored list, once, and asks the backend if it has not been asked recently. */
    suspend fun refresh() = mutex.withLock {
        if (!loaded) {
            loaded = true
            state.value = withContext(dispatcher) { read() }
        }
        val now = nowMillis()
        val last = lastAskedAtMillis
        if (last != null && now - last < minIntervalMillis) return@withLock
        lastAskedAtMillis = now
        val fetched = source.fetch() ?: return@withLock
        if (fetched == state.value) return@withLock
        state.value = fetched
        withContext(dispatcher) { write(fetched) }
    }

    private fun read(): VazieLinks? = runCatching {
        if (!file.exists()) return null
        VazieApiClient.json.decodeFromString(StoredDto.serializer(), file.readText(Charsets.UTF_8)).toLinks()
    }.getOrNull()

    private fun write(links: VazieLinks) {
        runCatching {
            file.parentFile?.mkdirs()
            val text = VazieApiClient.json.encodeToString(StoredDto.serializer(), StoredDto.of(links))
            val temporary = File(file.parentFile, file.name + TEMPORARY_SUFFIX)
            temporary.writeText(text, Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temporary.delete()
            }
        }
        // Losing the file costs the shipped links on the next cold start offline, nothing more.
    }

    /** On disk in the wire's own shape, and read back through the same checks: an edited file opens nothing new. */
    @Serializable
    private data class StoredDto(val links: List<LinkDto>) {
        fun toLinks(): VazieLinks? = LinksDto(links).toLinks()

        companion object {
            fun of(links: VazieLinks) = StoredDto(links.links.map { it.toDto() })

            private fun VazieLink.toDto() = LinkDto(key, section.name, url, title.toDto(), subtitle?.toDto(), action?.toDto())

            private fun VazieLocalizedText.toDto() = TextDto(ru, en)
        }
    }

    private companion object {
        const val TEMPORARY_SUFFIX = ".tmp"

        /** The backend caches its answer for five minutes; asking more often than this learns nothing. */
        const val MIN_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}
