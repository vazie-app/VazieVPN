package app.vazie.vpn.feature.config

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric because `SavedStateHandle.toRoute` needs `Bundle`. `sdk = 36` is the targetSdk. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigDetailsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val profiles = FakeProfileRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the screen shows the stored profile, without its credential`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Home relay", state.name)
        assertEquals("VLESS", state.protocolLabel)
        assertEquals("REALITY", state.securityLabel)
        assertEquals("TCP", state.transportLabel)
        assertEquals("${ImportTestLinks.HOST}:443", state.server)
        assertEquals("xtls-rprx-vision", state.flow)
        assertEquals(listOf("packetEncoding"), state.keptParameters)
        assertFalse(state.loading)
        assertEquals("", state.secret)
        assertFalse(state.secretRevealed)
    }

    @Test
    fun `the credential arrives only when the user asks for it`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.ToggleSecret)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.secretRevealed)
        assertEquals(ImportTestLinks.USER_ID, viewModel.state.value.secret)
    }

    @Test
    fun `hiding releases it, and so does leaving the screen`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(ConfigDetailsAction.ToggleSecret)
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.ToggleSecret)
        assertEquals("", viewModel.state.value.secret)

        viewModel.onAction(ConfigDetailsAction.ToggleSecret)
        advanceUntilIdle()
        viewModel.onAction(ConfigDetailsAction.HideSecret)
        assertEquals("", viewModel.state.value.secret)
        assertFalse(viewModel.state.value.secretRevealed)
    }

    @Test
    fun `an id nothing is stored under renders as gone rather than as empty`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            advanceUntilIdle()

            assertTrue(viewModel.state.value.missing)
            assertFalse(viewModel.state.value.loading)
        }

    @Test
    fun `confirming a delete removes the profile and closes the screen`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.RequestDelete)
        assertTrue(viewModel.state.value.confirmingDelete)

        viewModel.onAction(ConfigDetailsAction.ConfirmDelete)
        advanceUntilIdle()

        assertTrue(profiles.profiles.isEmpty())
        assertEquals(ConfigDetailsEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `dismissing a delete keeps the profile`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.RequestDelete)
        viewModel.onAction(ConfigDetailsAction.DismissDelete)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.confirmingDelete)
        assertEquals(1, profiles.profiles.size)
    }

    @Test
    fun `the masked screen holds no credential to leak`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        val rendered = viewModel.state.value.toString()
        assertTrue(ImportTestLinks.USER_ID !in rendered)
        assertTrue(ImportTestLinks.PUBLIC_KEY !in rendered)
    }

    @Test
    fun `renaming keeps the id, so a running tunnel is not dropped`() = runTest(dispatcher) {
        // Rename keeps the id, so a running tunnel keeps its profile.
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.RequestRename)
        viewModel.onAction(ConfigDetailsAction.EditRename("Home server"))
        viewModel.onAction(ConfigDetailsAction.ConfirmRename)
        advanceUntilIdle()

        assertEquals(1, profiles.profiles.size, "rename must not create a second profile")
        assertEquals(PROFILE_ID, profiles.profiles.single().id.value, "the id changed")
        assertEquals("Home server", profiles.profiles.single().name)
        assertEquals("Home server", viewModel.state.value.name)
        assertEquals(null, viewModel.state.value.renameDraft, "the sheet stayed open")
    }

    @Test
    fun `an abandoned rename changes nothing`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.RequestRename)
        viewModel.onAction(ConfigDetailsAction.EditRename("Half a name"))
        viewModel.onAction(ConfigDetailsAction.DismissRename)
        advanceUntilIdle()

        assertEquals("Home relay", profiles.profiles.single().name)
        assertEquals("Home relay", viewModel.state.value.name)
    }

    @Test
    fun `a blank rename is refused rather than applied`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.RequestRename)
        viewModel.onAction(ConfigDetailsAction.EditRename("   "))
        assertFalse(viewModel.state.value.renameValid, "the confirm button would have been enabled")

        viewModel.onAction(ConfigDetailsAction.ConfirmRename)
        advanceUntilIdle()

        assertEquals("Home relay", profiles.profiles.single().name)
    }

    @Test
    fun `a duplicate is a new profile with none of the original's history`() = runTest(dispatcher) {
        // Two separate claims, and each one is a way a duplicate could lie about itself: sharing an id would
        // make it the same profile, and inheriting `lastUsedAt` would have it claim a session it never had.
        profiles.put(id = PROFILE_ID, name = "Home relay")
        profiles.markUsed(app.vazie.vpn.core.model.ProfileId(PROFILE_ID))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(ConfigDetailsAction.Duplicate)
        advanceUntilIdle()

        assertEquals(2, profiles.profiles.size)
        val copy = profiles.profiles.first { it.id.value != PROFILE_ID }
        assertTrue(copy.name.startsWith("Home relay"), copy.name)
        assertEquals(null, copy.lastUsedAt, "the copy claimed a session it never had")
    }

    @Test
    fun `export produces a link only after it is confirmed`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.RequestExport)
        assertTrue(viewModel.state.value.confirmingExport, "the warning was skipped")

        val effects = mutableListOf<ConfigDetailsEffect>()
        val collector = launch { viewModel.effects.toList(effects) }
        viewModel.onAction(ConfigDetailsAction.ConfirmExport)
        advanceUntilIdle()
        collector.cancel()

        val share = effects.filterIsInstance<ConfigDetailsEffect.Share>().single()
        assertTrue(share.link.expose().startsWith("vless" + "://"), "not a share link")
        assertFalse(viewModel.state.value.confirmingExport)
    }

    @Test
    fun `a cancelled export produces nothing`() = runTest(dispatcher) {
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        val effects = mutableListOf<ConfigDetailsEffect>()
        val collector = launch { viewModel.effects.toList(effects) }
        viewModel.onAction(ConfigDetailsAction.RequestExport)
        viewModel.onAction(ConfigDetailsAction.DismissExport)
        advanceUntilIdle()
        collector.cancel()

        assertTrue(effects.isEmpty(), "a credential left the view model without being asked for")
        assertFalse(viewModel.state.value.confirmingExport)
    }

    @Test
    fun `the export link never reaches the state`() = runTest(dispatcher) {
        // The reason Share is an effect. State is remembered, recomposed and printed; an effect is
        // consumed once. See ConfigDetailsEffect.Share. ConfigDetailsEffect.Share.
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        val effects = mutableListOf<ConfigDetailsEffect>()
        val collector = launch { viewModel.effects.toList(effects) }
        viewModel.onAction(ConfigDetailsAction.RequestExport)
        viewModel.onAction(ConfigDetailsAction.ConfirmExport)
        advanceUntilIdle()
        collector.cancel()

        val rendered = viewModel.state.value.toString()
        assertTrue(ImportTestLinks.USER_ID !in rendered, rendered)
        assertTrue("vless" + "://" !in rendered, rendered)
    }

    @Test
    fun `nothing on this screen marks the profile as used`() = runTest(dispatcher) {
        // Only ProfileUsageRecorder writes "last used"; opening a screen does not.
        profiles.put(id = PROFILE_ID, name = "Home relay")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(ConfigDetailsAction.ToggleSecret)
        viewModel.onAction(ConfigDetailsAction.RequestRename)
        viewModel.onAction(ConfigDetailsAction.EditRename("Renamed"))
        viewModel.onAction(ConfigDetailsAction.ConfirmRename)
        advanceUntilIdle()

        assertEquals(null, profiles.profiles.single().lastUsedAt)
    }

    private fun viewModel() = ConfigDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("configurationId" to PROFILE_ID)),
        context = ApplicationProvider.getApplicationContext(),
        profiles = profiles,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private companion object {
        const val PROFILE_ID = "stored-profile-1"
        val NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")
    }
}
