package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieFaq
import app.vazie.vpn.core.model.VazieFaqItem
import app.vazie.vpn.core.model.VazieFaqText
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
 * The FAQ screen's questions: the last list the backend published, kept on disk so a cold start offline shows it too.
 * The app has no questions of its own: [faq] is `null` until the backend has answered once.
 *
 * [refresh] asks at most once per [minIntervalMillis] once something is cached — with nothing cached every call asks,
 * so "try again" works — and never twice at the same time; a failure keeps what is there. [loading] is true while it
 * asks.
 */
class FaqRepository internal constructor(
    private val source: FaqSource,
    private val file: File,
    private val nowMillis: () -> Long,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val mutex = Mutex()
    private var lastAskedAtMillis: Long? = null
    private val state = MutableStateFlow<VazieFaq?>(null)
    /** True from the start: until the first [refresh] has finished, nothing is known to be missing. */
    private val asking = MutableStateFlow(true)
    private var loaded = false

    val faq: StateFlow<VazieFaq?> = state.asStateFlow()
    val loading: StateFlow<Boolean> = asking.asStateFlow()

    /** Reads the stored list, once, and asks the backend if it has not been asked recently. */
    suspend fun refresh() = mutex.withLock {
        if (!loaded) {
            loaded = true
            state.value = withContext(dispatcher) { read() }
        }
        val now = nowMillis()
        val last = lastAskedAtMillis
        if (state.value != null && last != null && now - last < minIntervalMillis) return@withLock
        lastAskedAtMillis = now
        asking.value = true
        val fetched = try {
            source.fetch()
        } finally {
            asking.value = false
        } ?: return@withLock
        if (fetched == state.value) return@withLock
        state.value = fetched
        withContext(dispatcher) { write(fetched) }
    }

    private fun read(): VazieFaq? = runCatching {
        if (!file.exists()) return null
        VazieApiClient.json.decodeFromString(FaqDto.serializer(), file.readText(Charsets.UTF_8)).toFaq()
    }.getOrNull()

    private fun write(faq: VazieFaq) {
        runCatching {
            file.parentFile?.mkdirs()
            val text = VazieApiClient.json.encodeToString(FaqDto.serializer(), faq.toDto())
            val temporary = File(file.parentFile, file.name + TEMPORARY_SUFFIX)
            temporary.writeText(text, Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temporary.delete()
            }
        }
        // Losing the file costs the questions on the next cold start offline, until the backend is reachable again.
    }

    private companion object {
        const val TEMPORARY_SUFFIX = ".tmp"

        /** The backend caches its answer for five minutes; asking more often than this learns nothing. */
        const val MIN_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}

/** On disk in the wire's own shape, read back through the same checks: an edited file opens nothing new. */
private fun VazieFaq.toDto() = FaqDto(items.map { it.toDto() })

private fun VazieFaqItem.toDto() = FaqItemDto(
    key = key,
    question = question.toDto(),
    answer = answer.toDto(),
    link = link?.let { FaqLinkDto(it.url, it.label.toDto()) },
)

private fun VazieFaqText.toDto() = FaqTextDto(ru, en)
