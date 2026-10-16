package app.vazie.vpn.update

import app.vazie.vpn.update.api.AppUpdateChecker
import app.vazie.vpn.update.api.AppUpdateStore
import app.vazie.vpn.update.api.AppVersionPolicy
import app.vazie.vpn.update.api.AppVersionSource
import app.vazie.vpn.update.api.StoredUpdateState
import app.vazie.vpn.update.api.UpdateStatus
import app.vazie.vpn.update.api.VersionAnswer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** What the person is shown: the automatic offer, the manual check in Settings and its four outcomes. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var answer: VersionAnswer = VersionAnswer.Published(POLICY)
    private var now = 1_000_000_000L
    private val store = object : AppUpdateStore {
        var stored = StoredUpdateState()
        override fun read() = stored
        override suspend fun write(state: StoredUpdateState) {
            stored = state
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(installed: Int) =
        AppUpdateViewModel(AppUpdateChecker(installed, AppVersionSource { answer }, store, { now }))

    @Test
    fun `a current build sees nothing and a check on launch changes nothing on screen`() = runTest(dispatcher) {
        val viewModel = viewModel(42)
        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(UpdateStatus.UpToDate, viewModel.state.value.status)
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun `an older supported build is offered the update once, and later hides it`() = runTest(dispatcher) {
        val viewModel = viewModel(40)
        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(UpdatePrompt.Available(POLICY), viewModel.state.value.prompt)

        viewModel.onPostpone()
        advanceUntilIdle()

        assertNull(viewModel.state.value.prompt)
        assertIs<UpdateStatus.Optional>(viewModel.state.value.status)
    }

    @Test
    fun `a required build gets the block and no polite prompt`() = runTest(dispatcher) {
        val viewModel = viewModel(38)
        viewModel.onForeground()
        advanceUntilIdle()

        assertIs<UpdateStatus.Required>(viewModel.state.value.status)
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun `a manual check on a current build says so`() = runTest(dispatcher) {
        val viewModel = viewModel(42)
        viewModel.onCheckNow()
        advanceUntilIdle()

        assertEquals(UpdatePrompt.UpToDate, viewModel.state.value.prompt)
        viewModel.onDismissPrompt()
        advanceUntilIdle()
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun `a manual check offers an available update even if it was postponed`() = runTest(dispatcher) {
        val viewModel = viewModel(40)
        viewModel.onForeground()
        advanceUntilIdle()
        viewModel.onPostpone()
        advanceUntilIdle()

        viewModel.onCheckNow()
        advanceUntilIdle()

        assertEquals(UpdatePrompt.Available(POLICY), viewModel.state.value.prompt)
    }

    @Test
    fun `a manual check that finds a required update blocks the app`() = runTest(dispatcher) {
        val viewModel = viewModel(38)
        viewModel.onCheckNow()
        advanceUntilIdle()

        assertIs<UpdateStatus.Required>(viewModel.state.value.status)
        assertNull(viewModel.state.value.prompt)
        assertFalse(viewModel.state.value.couldNotVerify)
    }

    @Test
    fun `a manual check with no network says so and can be tried again`() = runTest(dispatcher) {
        answer = VersionAnswer.Failed
        val viewModel = viewModel(40)
        viewModel.onCheckNow()
        advanceUntilIdle()

        assertEquals(UpdatePrompt.CheckFailed, viewModel.state.value.prompt)
        assertEquals(UpdateStatus.UpToDate, viewModel.state.value.status, "no answer must not require anything")

        answer = VersionAnswer.Published(POLICY)
        viewModel.onCheckNow()
        advanceUntilIdle()

        assertEquals(UpdatePrompt.Available(POLICY), viewModel.state.value.prompt)
    }

    @Test
    fun `closing an information prompt is not a postponement`() = runTest(dispatcher) {
        answer = VersionAnswer.Failed
        val viewModel = viewModel(40)
        viewModel.onCheckNow()
        advanceUntilIdle()
        viewModel.onPostpone()
        advanceUntilIdle()

        assertNull(store.stored.postponedVersionCode)
    }

    @Test
    fun `on the required screen a check that cannot reach the backend keeps the block and says it is unconfirmed`() = runTest(dispatcher) {
        val viewModel = viewModel(38)
        viewModel.onForeground()
        advanceUntilIdle()

        answer = VersionAnswer.Failed
        viewModel.onCheckNow()
        advanceUntilIdle()

        assertIs<UpdateStatus.Required>(viewModel.state.value.status)
        assertTrue(viewModel.state.value.couldNotVerify)

        // The backend is back and still requires it: confirmed, no longer "unconfirmed".
        answer = VersionAnswer.Published(POLICY)
        viewModel.onCheckNow()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.couldNotVerify)
    }

    @Test
    fun `a required screen is lifted by a check that finds the build supported`() = runTest(dispatcher) {
        val viewModel = viewModel(38)
        viewModel.onForeground()
        advanceUntilIdle()

        answer = VersionAnswer.Published(POLICY.copy(minimumSupportedVersionCode = 30))
        viewModel.onCheckNow()
        advanceUntilIdle()

        assertIs<UpdateStatus.Optional>(viewModel.state.value.status)
        assertEquals(UpdatePrompt.Available(POLICY.copy(minimumSupportedVersionCode = 30)), viewModel.state.value.prompt)
    }

    private companion object {
        val POLICY = AppVersionPolicy(42, "1.4.0", 39, "https://vazie.app/vpn")
    }
}
