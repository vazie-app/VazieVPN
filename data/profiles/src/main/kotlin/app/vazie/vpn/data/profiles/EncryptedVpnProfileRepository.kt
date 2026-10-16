package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.ProfileDetails
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XrayTransport
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The stored profiles, decrypted once into memory and written back whole on every change. */
internal class EncryptedVpnProfileRepository(
    private val store: ProfileStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : VpnProfileRepository {

    private val mutex = Mutex()
    private val cache = MutableStateFlow<List<VpnProfile>?>(null)

    override fun observeSummaries(): Flow<List<ProfileSummary>> = cache
        .onStart { mutex.withLock { loadLocked() } }
        .filterNotNull()
        .map { profiles -> profiles.sortedByDescending { it.createdAt }.map(VpnProfile::toSummary) }
        .distinctUntilChanged()

    override suspend fun create(
        draft: ProfileDraft,
        name: String,
        origin: ProfileOrigin,
    ): ProfileId {
        require(name.isNotBlank()) { "a profile name must not be blank" }
        val profile = draft.toProfile(
            id = ProfileId(newId()),
            name = name.trim(),
            origin = origin,
            createdAt = clock.instant(),
        )
        mutex.withLock {
            val updated = loadLocked() + profile
            withContext(dispatcher) { store.write(updated) }
            cache.value = updated
        }
        return profile.id
    }

    override suspend fun rename(id: ProfileId, name: String) {
        require(name.isNotBlank()) { "a profile name must not be blank" }
        // A copy with one field changed, written back under the same id. Nothing else in the record is
        // rebuilt, so a rename cannot lose an unknown parameter or reset a timestamp by accident.
        edit(id) { profile ->
            when (profile) {
                is VpnProfile.Xray -> profile.copy(name = name.trim())
            }
        }
    }

    override suspend fun markUsed(id: ProfileId) {
        val now = clock.instant()
        edit(id) { profile ->
            when (profile) {
                is VpnProfile.Xray -> profile.copy(lastUsedAt = now)
            }
        }
    }

    override suspend fun duplicate(id: ProfileId, name: String): ProfileId? {
        require(name.isNotBlank()) { "a profile name must not be blank" }
        return mutex.withLock {
            val source = loadLocked().firstOrNull { it.id == id } ?: return@withLock null
            val copy = when (source) {
                is VpnProfile.Xray -> source.copy(
                    id = ProfileId(newId()),
                    name = name.trim(),
                    createdAt = clock.instant(),
                    // Deliberately dropped: both describe what happened to the original.
                    lastUsedAt = null,
                )
            }
            val updated = loadLocked() + copy
            withContext(dispatcher) { store.write(updated) }
            cache.value = updated
            copy.id
        }
    }

    /** Read one profile, change it, write the list back — under the same id, every time. */
    private suspend fun edit(id: ProfileId, transform: (VpnProfile) -> VpnProfile) {
        mutex.withLock {
            val current = loadLocked()
            val existing = current.firstOrNull { it.id == id } ?: return@withLock
            val updated = transform(existing)
            if (updated == existing) return@withLock
            val list = current.map { if (it.id == id) updated else it }
            withContext(dispatcher) { store.write(list) }
            cache.value = list
        }
    }

    override suspend fun details(id: ProfileId): ProfileDetails? = profile(id)?.toDetails()

    override suspend fun revealCredential(id: ProfileId): Secret<String>? =
        when (val profile = profile(id)) {
            null -> null
            is VpnProfile.Xray -> when (val outbound = profile.outbound) {
                is XrayOutbound.Vless -> outbound.userId
            }
        }

    override suspend fun profile(id: ProfileId): VpnProfile? =
        mutex.withLock { loadLocked() }.firstOrNull { it.id == id }

    override suspend fun delete(id: ProfileId) {
        mutex.withLock {
            val current = loadLocked()
            val updated = current.filterNot { it.id == id }
            if (updated.size == current.size) return@withLock
            withContext(dispatcher) { store.write(updated) }
            cache.value = updated
        }
    }

    /** The profiles, reading the file on first use. Must be called while holding [mutex]. */
    private suspend fun loadLocked(): List<VpnProfile> =
        cache.value ?: withContext(dispatcher) { store.read() }.orEmpty().also { cache.value = it }

}

private fun VpnProfile.toSummary(): ProfileSummary = ProfileSummary(
    id = id,
    name = name,
    engineId = engineId,
    origin = origin,
    lastUsedAt = lastUsedAt,
    protocolLabel = when (this) {
        is VpnProfile.Xray -> when (outbound) {
            is XrayOutbound.Vless -> "VLESS"
        }
    },
)

private fun VpnProfile.toDetails(): ProfileDetails = when (this) {
    is VpnProfile.Xray -> when (val outbound = outbound) {
        is XrayOutbound.Vless -> ProfileDetails(
            id = id,
            name = name,
            engineId = engineId,
            origin = origin,
            protocolLabel = "VLESS",
            security = outbound.security.kind,
            transport = outbound.transport.kind,
            endpointHost = outbound.endpoint.host,
            endpointPort = outbound.endpoint.port,
            serverName = outbound.security.serverName,
            fingerprint = outbound.security.fingerprint,
            flow = outbound.flow,
            transportDetail = when (val transport = outbound.transport) {
                is XrayTransport.Tcp -> transport.headerType
                is XrayTransport.WebSocket -> transport.path
                is XrayTransport.Grpc -> transport.serviceName.ifEmpty { null }
            },
            unknownParameterNames = outbound.unknownParameters.names,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
                )
    }
}
