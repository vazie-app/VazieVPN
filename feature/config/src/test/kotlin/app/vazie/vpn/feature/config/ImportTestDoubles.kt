package app.vazie.vpn.feature.config

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.ProfileDetails
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XrayTransport
import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.config.ConfigSource
import app.vazie.vpn.config.ParseResult
import app.vazie.vpn.config.subscription.SubscriptionFailure
import app.vazie.vpn.config.subscription.SubscriptionFetch
import app.vazie.vpn.config.subscription.SubscriptionLink
import app.vazie.vpn.config.subscription.SubscriptionSource
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Link inputs, assembled rather than written. */
internal object ImportTestLinks {

    const val USER_ID = "00000000-0000-4000-8000-000000000001"
    const val HOST = "relay.example.net"
    const val PUBLIC_KEY = "not-a-real-reality-public-key-0000000000000"

    private val scheme = "vless" + "://"

    fun link(
        userId: String = USER_ID,
        host: String = HOST,
        port: String = "443",
        query: String = "",
        fragment: String? = null,
    ): String = scheme + userId + "@" + host + ":" + port +
        (if (query.isEmpty()) "" else "?$query") +
        (if (fragment == null) "" else "#$fragment")

    /** REALITY over TCP with Vision, plus one parameter Vazie is not expected to understand. */
    fun reality(fragment: String? = "Home relay"): String = link(
        query = "security=reality&type=tcp&flow=xtls-rprx-vision" +
            "&sni=$HOST&fp=chrome&pbk=$PUBLIC_KEY&packetEncoding=xudp",
        fragment = fragment,
    )
}

/** A subscription server that answers what a test tells it to, and remembers what it was asked. */
internal class FakeSubscriptionSource : SubscriptionSource {

    private var answer: CompletableDeferred<SubscriptionFetch> =
        CompletableDeferred(SubscriptionFetch.Failed(SubscriptionFailure.UNREACHABLE))

    val asked = mutableListOf<String>()

    fun answer(body: String) = answer(CompletableDeferred(SubscriptionFetch.Fetched(Secret.of(body))))

    fun fail(reason: SubscriptionFailure) = answer(CompletableDeferred(SubscriptionFetch.Failed(reason)))

    /** An answer that arrives when the test completes it. */
    fun answer(pending: CompletableDeferred<SubscriptionFetch>) {
        answer = pending
    }

    override suspend fun fetch(link: SubscriptionLink): SubscriptionFetch {
        asked += link.url.expose()
        return answer.await()
    }
}

/** A profile store that keeps everything in memory. */
/** The selection, in memory, so a test can assert that saving arms the configuration it just added. */
internal class FakeSelectedProfileStore : SelectedProfileStore {

    private val state = MutableStateFlow<ProfileId?>(null)

    override fun observeSelected(): Flow<ProfileId?> = state

    override suspend fun selected(): ProfileId? = state.value

    override suspend fun select(id: ProfileId) {
        state.value = id
    }

    override suspend fun clear() {
        state.value = null
    }
}

internal class FakeProfileRepository : VpnProfileRepository {

    private val stored = MutableStateFlow<List<VpnProfile>>(emptyList())
    private var next = 0

    val profiles: List<VpnProfile> get() = stored.value

    override fun observeSummaries(): Flow<List<ProfileSummary>> = stored.map { profiles ->
        profiles.map {
            ProfileSummary(
                id = it.id,
                name = it.name,
                engineId = VpnEngineId.XRAY,
                protocolLabel = "VLESS",
                origin = it.origin,
                lastUsedAt = it.lastUsedAt,
            )
        }
    }

    override suspend fun create(
        draft: ProfileDraft,
        name: String,
        origin: ProfileOrigin,
    ): ProfileId {
        require(name.isNotBlank()) { "a profile name must not be blank" }
        val profile = draft.toProfile(
            id = ProfileId("stored-${next++}"),
            name = name,
            origin = origin,
            createdAt = CREATED_AT,
        )
        stored.value = stored.value + profile
        return profile.id
    }

    override suspend fun details(id: ProfileId): ProfileDetails? {
        val profile = stored.value.firstOrNull { it.id == id } as? VpnProfile.Xray ?: return null
        val outbound = profile.outbound as XrayOutbound.Vless
        return ProfileDetails(
            id = profile.id,
            name = profile.name,
            engineId = VpnEngineId.XRAY,
            protocolLabel = "VLESS",
            origin = profile.origin,
            security = outbound.security.kind,
            transport = outbound.transport.kind,
            endpointHost = outbound.endpoint.host,
            endpointPort = outbound.endpoint.port,
            serverName = outbound.security.serverName,
            fingerprint = outbound.security.fingerprint,
            flow = outbound.flow,
            transportDetail = (outbound.transport as? XrayTransport.WebSocket)?.path,
            unknownParameterNames = outbound.unknownParameters.names,
            createdAt = profile.createdAt,
            lastUsedAt = profile.lastUsedAt,
        )
    }

    override suspend fun revealCredential(id: ProfileId): Secret<String>? {
        val profile = stored.value.firstOrNull { it.id == id } as? VpnProfile.Xray ?: return null
        return (profile.outbound as XrayOutbound.Vless).userId
    }

    override suspend fun profile(id: ProfileId): VpnProfile? =
        stored.value.firstOrNull { it.id == id }

    override suspend fun delete(id: ProfileId) {
        stored.value = stored.value.filterNot { it.id == id }
    }

    override suspend fun rename(id: ProfileId, name: String) {
        require(name.isNotBlank()) { "a profile name must not be blank" }
        edit(id) { it.copy(name = name) }
    }

    /** A copy with a new identity and none of the original's history. */
    override suspend fun duplicate(id: ProfileId, name: String): ProfileId? {
        val source = stored.value.firstOrNull { it.id == id } as? VpnProfile.Xray ?: return null
        val copy = source.copy(
            id = ProfileId("stored-${next++}"),
            name = name,
            createdAt = CREATED_AT,
            lastUsedAt = null,
        )
        stored.value = stored.value + copy
        return copy.id
    }


    override suspend fun markUsed(id: ProfileId) {
        edit(id) { it.copy(lastUsedAt = CREATED_AT) }
    }

    private fun edit(id: ProfileId, transform: (VpnProfile.Xray) -> VpnProfile.Xray) {
        stored.value = stored.value.map { profile ->
            if (profile.id == id) transform(profile as VpnProfile.Xray) else profile
        }
    }

    /** Puts a profile in the store without going through the wizard, by reading a synthetic link with the
     * real parser — so a screen test is looking at the shape an import actually produces. */
    fun put(id: String, name: String, link: String = ImportTestLinks.reality()) {
        val result = ConfigParserRegistry.default().parse(ConfigSource.PlainText(Secret.of(link)))
        val draft = (result as ParseResult.Recognized).draft
        stored.value = stored.value + draft.toProfile(
            id = ProfileId(id),
            name = name,
            origin = ProfileOrigin.IMPORTED_LINK,
            createdAt = CREATED_AT,
        )
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
