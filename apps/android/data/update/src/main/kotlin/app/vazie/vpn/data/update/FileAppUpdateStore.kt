package app.vazie.vpn.data.update

import app.vazie.vpn.core.network.VazieApiClient
import app.vazie.vpn.update.api.AppUpdateStore
import app.vazie.vpn.update.api.StoredUpdateState
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** [AppUpdateStore] in one small JSON file. Plain: nothing in it is private. A policy read back is checked again as
 * if it had just arrived, so a file edited by hand cannot point the update button anywhere but an `https` page. */
internal class FileAppUpdateStore(
    private val file: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppUpdateStore {

    private val mutex = Mutex()

    override fun read(): StoredUpdateState = runCatching {
        if (!file.exists()) return StoredUpdateState()
        val stored = VazieApiClient.json.decodeFromString(StoredDto.serializer(), file.readText(Charsets.UTF_8))
        StoredUpdateState(
            policy = stored.policy?.toPolicy(),
            postponedVersionCode = stored.postponedVersionCode,
            postponedAtMillis = stored.postponedAtMillis,
        )
    }.getOrDefault(StoredUpdateState())

    override suspend fun write(state: StoredUpdateState) = mutex.withLock {
        withContext(dispatcher) {
            runCatching {
                val dto = StoredDto(
                    policy = state.policy?.let {
                        AppVersionDto(it.latestVersionCode, it.latestVersionName, it.minimumSupportedVersionCode, it.updateUrl, it.messages)
                    },
                    postponedVersionCode = state.postponedVersionCode,
                    postponedAtMillis = state.postponedAtMillis,
                )
                file.parentFile?.mkdirs()
                val text = VazieApiClient.json.encodeToString(StoredDto.serializer(), dto)
                val temporary = File(file.parentFile, file.name + TEMPORARY_SUFFIX)
                temporary.writeText(text, Charsets.UTF_8)
                if (!temporary.renameTo(file)) {
                    file.writeText(text, Charsets.UTF_8)
                    temporary.delete()
                }
            }
            // Losing the file costs a remembered requirement until the next successful check, nothing more.
            Unit
        }
    }

    @Serializable
    private data class StoredDto(
        val policy: AppVersionDto? = null,
        val postponedVersionCode: Int? = null,
        val postponedAtMillis: Long? = null,
    )

    private companion object {
        const val TEMPORARY_SUFFIX = ".tmp"
    }
}
