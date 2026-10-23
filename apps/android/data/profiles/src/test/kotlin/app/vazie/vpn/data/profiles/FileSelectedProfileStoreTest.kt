package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.ProfileDetails
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** Which profile is selected, and what happens when the answer stops being true. */
class FileSelectedProfileStoreTest {

    private val directory: File = Files.createTempDirectory("vazie-selection").toFile()
    private val file = File(directory, "selected")
    private val profiles = FakeRepository()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `nothing is selected on a fresh install`() = runTest {
        assertNull(store().selected())
    }

    @Test
    fun `a selection is remembered`() = runTest {
        val store = store()
        profiles.add("a")

        store.select(ProfileId("a"))

        assertEquals(ProfileId("a"), store.selected())
    }

    @Test
    fun `a selection an earlier build left that names no stored profile is forgotten`() = runTest {
        val store = store()

        store.select(ProfileId("profile-other"))

        assertNull(store.observeSelected().first())
        assertNull(store.selected())
    }

    @Test
    fun `a selection survives the process`() = runTest {
        profiles.add("a")
        store().select(ProfileId("a"))

        // A second store over the same file is what a restart looks like from here.
        assertEquals(ProfileId("a"), store().selected())
    }

    @Test
    fun `only the id is written, never the profile`() = runTest {
        profiles.add("a")
        store().select(ProfileId("a"))

        val contents = file.readText()
        assertEquals("a", contents)
        assertFalse(contents.contains("relay"), "something other than an id reached the file")
    }

    @Test
    fun `clearing leaves nothing behind`() = runTest {
        profiles.add("a")
        val store = store()
        store.select(ProfileId("a"))

        store.clear()

        assertNull(store.selected())
        assertFalse(file.exists())
    }

    @Test
    fun `a selection whose profile was deleted reads as nothing selected`() = runTest {
        profiles.add("a")
        val store = store()
        store.select(ProfileId("a"))

        profiles.remove("a")

        assertNull(store.selected())
    }

    @Test
    fun `a stale selection is erased rather than left to rot`() = runTest {
        profiles.add("a")
        val store = store()
        store.select(ProfileId("a"))
        profiles.remove("a")

        store.selected()

        assertFalse(file.exists(), "a stale id was left on disk to be answered again")
    }

    @Test
    fun `the observed selection drops a profile that disappears`() = runTest {
        profiles.add("a")
        val store = store()
        store.select(ProfileId("a"))
        assertEquals(ProfileId("a"), store.observeSelected().first())

        profiles.remove("a")

        assertNull(store.observeSelected().first())
    }

    private fun store() = FileSelectedProfileStore(
        file = file,
        profiles = profiles,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private class FakeRepository : VpnProfileRepository {

        private val state = MutableStateFlow(emptyList<ProfileSummary>())

        fun add(id: String) {
            state.value = state.value + ProfileSummary(
                id = ProfileId(id),
                name = "Relay $id",
                engineId = VpnEngineId.XRAY,
                protocolLabel = "VLESS",
            )
        }

        fun remove(id: String) {
            state.value = state.value.filterNot { it.id.value == id }
        }

        override fun observeSummaries(): Flow<List<ProfileSummary>> = state

        override suspend fun create(
            draft: ProfileDraft,
            name: String,
            origin: ProfileOrigin,
        ): ProfileId = error("not used")

        override suspend fun details(id: ProfileId): ProfileDetails? =
            state.value.firstOrNull { it.id == id }?.let {
                ProfileDetails(
                    id = it.id,
                    name = it.name,
                    engineId = it.engineId,
                    protocolLabel = it.protocolLabel,
                    origin = ProfileOrigin.IMPORTED_LINK,
                    security = app.vazie.vpn.api.XraySecurityKind.NONE,
                    transport = app.vazie.vpn.api.XrayTransportKind.TCP,
                    endpointHost = "relay.example.net",
                    endpointPort = 443,
                    serverName = null,
                    fingerprint = null,
                    flow = null,
                    transportDetail = null,
                    unknownParameterNames = emptyList(),
                    createdAt = Instant.EPOCH,
                    lastUsedAt = null,
                )
            }

        override suspend fun revealCredential(id: ProfileId): Secret<String>? = null

        override suspend fun profile(id: ProfileId): VpnProfile? = null

        override suspend fun delete(id: ProfileId) = Unit

        override suspend fun rename(id: ProfileId, name: String) {
            state.value = state.value.map { if (it.id == id) it.copy(name = name) else it }
        }

        override suspend fun duplicate(id: ProfileId, name: String): ProfileId? {
            val source = state.value.firstOrNull { it.id == id } ?: return null
            val copyId = ProfileId(id.value + "-copy")
            state.value = state.value + source.copy(
                id = copyId,
                name = name,
                lastUsedAt = null,
            )
            return copyId
        }


        override suspend fun markUsed(id: ProfileId) {
            state.value = state.value.map { if (it.id == id) it.copy(lastUsedAt = Instant.EPOCH) else it }
        }
    }
}
