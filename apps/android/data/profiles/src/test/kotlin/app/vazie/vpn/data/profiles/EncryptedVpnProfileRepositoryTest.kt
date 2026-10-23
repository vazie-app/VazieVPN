package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XraySecurityKind
import app.vazie.vpn.api.XrayTransportKind
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedVpnProfileRepositoryTest {

    private lateinit var directory: File
    private lateinit var file: File
    private val cipher = TestProfileCipher()
    private var nextId = 0

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("vazie-profiles").toFile()
        file = File(directory, ProfileStore.FILE_NAME)
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `a saved profile can be read back by its id`() = runTest {
        val repository = repository()

        val id = repository.create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        val profile = assertIs<VpnProfile.Xray>(repository.profile(id))
        assertEquals("Home relay", profile.name)
        val outbound = assertIs<XrayOutbound.Vless>(profile.outbound)
        assertEquals(TestProfiles.USER_ID, outbound.userId.expose())
        assertEquals(TestProfiles.HOST, outbound.endpoint.host)
    }

    @Test
    fun `the profile list is observable and updates on every save`() = runTest {
        val repository = repository()
        assertEquals(emptyList(), repository.observeSummaries().first())

        repository.create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)
        assertEquals(listOf("Home relay"), repository.observeSummaries().first().map { it.name })

        repository.create(TestProfiles.plainDraft(), "Travel relay", ProfileOrigin.IMPORTED_LINK)
        assertEquals(
            listOf("Travel relay", "Home relay"),
            repository.observeSummaries().first().map { it.name },
            "newest first",
        )
    }

    @Test
    fun `a profile survives a new repository over the same file`() = runTest {
        repository().create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        val reopened = repository()

        val summary = reopened.observeSummaries().first().single()
        assertEquals("Home relay", summary.name)
        assertEquals("VLESS", summary.protocolLabel)
        val outbound = assertIs<XrayOutbound.Vless>(
            assertIs<VpnProfile.Xray>(reopened.profile(summary.id)).outbound,
        )
        assertEquals(TestProfiles.USER_ID, outbound.userId.expose())
        val security = assertIs<XraySecurity.Reality>(outbound.security)
        assertEquals(TestProfiles.PUBLIC_KEY, security.publicKey.expose())
        assertEquals(TestProfiles.SHORT_ID, security.shortId?.expose())
    }

    @Test
    fun `a parameter Vazie did not understand survives the round trip`() = runTest {
        val id = repository().create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        val outbound = assertIs<XrayOutbound.Vless>(
            assertIs<VpnProfile.Xray>(repository().profile(id)).outbound,
        )
        assertEquals(listOf("packetEncoding"), outbound.unknownParameters.names)
        assertEquals("xudp", outbound.unknownParameters.entries.single().value.expose())
    }

    @Test
    fun `the profile id is Vazie's own and never the imported user id`() = runTest {
        val repository = EncryptedVpnProfileRepository(
            store = ProfileStore(file, cipher),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            clock = TickingClock(),
        )

        val id = repository.create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        assertNotEquals(TestProfiles.USER_ID, id.value)
        assertTrue(TestProfiles.USER_ID !in file.readText(Charsets.ISO_8859_1), "stored in the clear")
    }

    @Test
    fun `a summary carries nothing that could be a secret`() = runTest {
        val repository = repository()
        repository.create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        val rendered = repository.observeSummaries().first().single().toString()

        listOf(TestProfiles.USER_ID, TestProfiles.PUBLIC_KEY, TestProfiles.SHORT_ID, TestProfiles.HOST)
            .forEach { assertTrue(it !in rendered, "a summary rendered as \"$rendered\"") }
    }

    @Test
    fun `details describe the profile without its credential`() = runTest {
        val repository = repository()
        val id = repository.create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        val details = repository.details(id)!!

        assertEquals(XraySecurityKind.REALITY, details.security)
        assertEquals(XrayTransportKind.TCP, details.transport)
        assertEquals(TestProfiles.HOST, details.endpointHost)
        assertEquals(443, details.endpointPort)
        assertEquals("chrome", details.fingerprint)
        assertEquals(listOf("packetEncoding"), details.unknownParameterNames)
        listOf(TestProfiles.USER_ID, TestProfiles.PUBLIC_KEY, TestProfiles.SHORT_ID)
            .forEach { assertTrue(it !in details.toString(), "details rendered a secret") }
    }

    @Test
    fun `the credential is handed over only when it is asked for by name`() = runTest {
        val repository = repository()
        val id = repository.create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        assertEquals(TestProfiles.USER_ID, repository.revealCredential(id)?.expose())
        assertNull(repository.revealCredential(ProfileId("nothing-under-this-id")))
    }

    @Test
    fun `a blank name is not a profile and nothing is written`() = runTest {
        val repository = repository()

        assertFailsWith<IllegalArgumentException> {
            repository.create(TestProfiles.realityDraft(), "   ", ProfileOrigin.IMPORTED_LINK)
        }

        assertEquals(emptyList(), repository.observeSummaries().first())
        assertTrue(!file.exists(), "an import that failed left a file behind")
    }

    @Test
    fun `deleting removes the profile from the file too`() = runTest {
        val repository = repository()
        val id = repository.create(TestProfiles.realityDraft(), "Home relay", ProfileOrigin.IMPORTED_LINK)

        repository.delete(id)

        assertEquals(emptyList(), repository.observeSummaries().first())
        assertEquals(emptyList(), repository().observeSummaries().first())
        assertNull(repository.profile(id))
    }

    @Test
    fun `a file this build cannot read is left alone and reads as empty`() = runTest {
        file.parentFile.mkdirs()
        file.writeBytes(byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9))
        val before = file.readBytes()

        assertEquals(emptyList(), repository().observeSummaries().first())
        assertTrue(before.contentEquals(file.readBytes()), "an unreadable store was overwritten")
    }

    @Test
    fun `renaming keeps the id, the credential and every parameter Vazie does not understand`() =
        runTest {
            // Rename copies rather than re-parses, keeping `unknownParameters` and the id.
            val repository = repository()
            val id = repository.create(
                TestProfiles.realityDraft(),
                "Home relay",
                ProfileOrigin.IMPORTED_LINK,
            )
            val before = assertIs<VpnProfile.Xray>(repository.profile(id))

            repository.rename(id, "Home server")

            val after = assertIs<VpnProfile.Xray>(repository.profile(id))
            assertEquals("Home server", after.name)
            assertEquals(id, after.id, "the id changed")
            assertEquals(before.createdAt, after.createdAt, "a rename is not a new profile")
            assertEquals(before.outbound.toString(), after.outbound.toString())
            assertEquals(
                TestProfiles.USER_ID,
                assertIs<XrayOutbound.Vless>(after.outbound).userId.expose(),
            )
        }

    @Test
    fun `a duplicate is a separate profile with the same connection and none of the history`() =
        runTest {
            val repository = repository()
            val id = repository.create(
                TestProfiles.realityDraft(),
                "Home relay",
                ProfileOrigin.IMPORTED_LINK,
            )
            repository.markUsed(id)
            val original = assertIs<VpnProfile.Xray>(repository.profile(id))

            val copyId = repository.duplicate(id, "Home relay (copy)")

            assertNotNull(copyId)
            assertTrue(copyId != id, "the copy shares the original's id")
            val copy = assertIs<VpnProfile.Xray>(repository.profile(copyId))
            assertEquals("Home relay (copy)", copy.name)
            assertEquals(
                original.outbound.toString(),
                copy.outbound.toString(),
                "a copy that cannot connect is not a copy",
            )
            assertEquals(
                TestProfiles.USER_ID,
                assertIs<XrayOutbound.Vless>(copy.outbound).userId.expose(),
                "the credential did not come with it",
            )
            assertNull(copy.lastUsedAt, "the copy claimed a session it never had")
            assertTrue(copy.createdAt >= original.createdAt, "the copy is not older than its original")

            // And the original is untouched by any of it.
            val stillThere = assertIs<VpnProfile.Xray>(repository.profile(id))
            assertEquals("Home relay", stillThere.name)
            assertNotNull(stillThere.lastUsedAt)
        }

    @Test
    fun `duplicating something that is not there produces nothing`() = runTest {
        assertNull(repository().duplicate(ProfileId("no-such-profile"), "Copy"))
    }

    @Test
    fun `a store written while pinning existed still reads`() = runTest {
        // Earlier versions wrote a `favourite` key into every record. Pinning is gone, and a person
        // upgrading must not lose their configurations over a key nobody reads any more.
        val id = repository().create(TestProfiles.plainDraft(), "Travel", ProfileOrigin.IMPORTED_LINK)
        val plain = cipher.decrypt(file.readBytes()).toString(Charsets.UTF_8)
        val legacy = plain.replace("\"lastUsedAt\"", "\"favourite\":true,\"lastUsedAt\"")
        assertTrue(legacy != plain, "the fixture did not inject the legacy key")
        file.writeBytes(cipher.encrypt(legacy.toByteArray(Charsets.UTF_8)))

        assertEquals("Travel", assertNotNull(repository().profile(id)).name)
    }

    @Test
    fun `marking a profile used records when, and touches nothing else`() = runTest {
        val repository = repository()
        val id = repository.create(TestProfiles.plainDraft(), "Travel", ProfileOrigin.IMPORTED_LINK)
        val before = assertIs<VpnProfile.Xray>(repository.profile(id))
        assertNull(before.lastUsedAt)

        repository.markUsed(id)

        val after = assertIs<VpnProfile.Xray>(repository.profile(id))
        assertNotNull(after.lastUsedAt)
        assertEquals(before.name, after.name)
        assertEquals(before.createdAt, after.createdAt, "being used is not being created")
        assertEquals(before.outbound.toString(), after.outbound.toString())
        assertEquals(after.lastUsedAt, repository.observeSummaries().first().single().lastUsedAt)
    }

    @Test
    fun `editing a profile that is not there does nothing and does not throw`() = runTest {
        // Operations on a deleted profile are no-ops: no crash, no invented profile.
        val repository = repository()
        val missing = ProfileId("no-such-profile")

        repository.rename(missing, "Anything")
        repository.markUsed(missing)

        assertEquals(emptyList(), repository.observeSummaries().first())
    }

    private fun TestScope.repository(): VpnProfileRepository = EncryptedVpnProfileRepository(
        store = ProfileStore(file, cipher),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        clock = TickingClock(),
        newId = { "profile-${nextId++}" },
    )

    /** A clock that moves one second per reading, so "newest first" is a property the test can observe rather
     * than an accident of insertion order. */
    private class TickingClock : Clock() {
        private var tick = 0L

        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = BASE.plusSeconds(tick++)

        private companion object {
            val BASE: Instant = Instant.parse("2026-01-01T00:00:00Z")
        }
    }
}
