package app.vazie.vpn.ui

import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.data.AppPreferences
import app.vazie.vpn.data.AppStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** What the shell knows before it draws its first frame. */
@OptIn(ExperimentalCoroutinesApi::class)
class VazieAppViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var directory: File

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        directory = Files.createTempDirectory("vazie-app-state").toFile()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    @Test
    fun `the stored theme is known before anything suspends`() = runTest(dispatcher) {
        preferences().setAppearance(Appearance.MILK)
        advanceUntilIdle()

        val viewModel = viewModel()

        // Known on the first state, so the shell never flashes the other palette.
        assertEquals(Appearance.MILK, viewModel.state.value.appearance, "the shell would have flashed a default")
    }

    @Test
    fun `a fresh install opens onboarding on the default theme`() = runTest(dispatcher) {
        val state = viewModel().state.value

        assertFalse(state.onboardingCompleted)
        assertEquals(Appearance.NIGHT_INDIGO, state.appearance)
        assertEquals(VazieAppIcon.Default, state.appIcon)
    }

    @Test
    fun `finishing onboarding is remembered by the next process`() = runTest(dispatcher) {
        viewModel().onOnboardingFinished()
        advanceUntilIdle()

        // A second view model over a second store is this test's new process: nothing is shared but
        // the directory, which is exactly what survives a real one.
        assertTrue(
            viewModel().state.value.onboardingCompleted,
            "a restart put the user back through onboarding",
        )
    }

    @Test
    fun `a chosen theme shows at once and survives a restart`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAppearanceSelected(Appearance.MILK)
        advanceUntilIdle()

        assertEquals(Appearance.MILK, viewModel.state.value.appearance, "the screen did not follow")
        assertEquals(Appearance.MILK, viewModel().state.value.appearance, "a restart forgot the theme")
    }

    /** The choice is written down, and nothing here talks to the package manager. */
    @Test
    fun `choosing an icon stores it on its signature plate`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAppIconPlateSelected(VazieAppIconPlate.LIGHT)
        advanceUntilIdle()

        viewModel.onAppIconSelected(VazieAppIcon.CRIMSON)
        advanceUntilIdle()

        assertEquals(VazieAppIcon.CRIMSON, viewModel.state.value.appIcon)
        assertEquals(
            VazieAppIcon.CRIMSON,
            preferences().snapshot().appIcon,
            "the icon was shown as chosen but never written down",
        )
        assertEquals(VazieAppIcon.CRIMSON.signaturePlate, preferences().snapshot().appIconPlate)
    }

    @Test
    fun `choosing an icon plate stores it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAppIconPlateSelected(VazieAppIconPlate.INK)
        advanceUntilIdle()
        assertEquals(VazieAppIconPlate.INK, viewModel.state.value.appIconPlate)
        assertEquals(VazieAppIconPlate.INK, preferences().snapshot().appIconPlate)
    }

    private fun viewModel() = VazieAppViewModel(preferences = preferences())

    /** Writes finish on a dispatcher the test drives; reads never suspend at all. */
    private fun preferences(): AppPreferences = AppStorage.preferences(
        filesDir = directory,
        dispatcher = UnconfinedTestDispatcher(dispatcher.scheduler),
    )
}
