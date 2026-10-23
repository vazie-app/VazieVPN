package app.vazie.vpn.update

import android.content.Intent
import android.net.Uri
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.update.api.AppVersionPolicy
import app.vazie.vpn.update.api.UpdateStatus
import androidx.activity.ComponentActivity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** A required update is a wall: nothing under it can be reached, Back does nothing, and the way out is the update page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class UpdateGateTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(AppUpdateUiState())
    private var underneathClicks by mutableIntStateOf(0)
    private var opened = mutableListOf<AppVersionPolicy>()
    private var checkedAgain = 0
    private var postponed = 0
    private var dismissed = 0

    private fun show() = compose.setContent {
        VazieTheme {
            UpdateGate(
                state = state,
                onOpenUpdate = { opened += it },
                onCheckAgain = { checkedAgain++ },
                onPostpone = { postponed++ },
                onDismissPrompt = { dismissed++ },
            ) { modifier ->
                Underneath(modifier)
            }
        }
    }

    @Composable
    private fun Underneath(modifier: Modifier) {
        Column(modifier) {
            Text(
                "settings row",
                modifier = Modifier
                    .testTag("underneath")
                    .clickable { underneathClicks++ },
            )
        }
    }

    @Test
    fun `an up to date app shows the app and no update screen`() {
        show()

        tapTopLeftOfTheWindow()
        assertEquals(1, underneathClicks, "the row was not reachable even with no update required")
        compose.onNodeWithTag(TAG_REQUIRED).assertDoesNotExist()
    }

    @Test
    fun `a required update shows the update screen with the backend's wording and the latest version`() {
        state = AppUpdateUiState(UpdateStatus.Required(POLICY))
        show()

        compose.onNodeWithTag(TAG_REQUIRED).assertIsDisplayed()
        compose.onNodeWithText("Update Vazie VPN to continue.").assertIsDisplayed()
        compose.onNodeWithText("Latest version: 1.4.0").assertIsDisplayed()
    }

    @Test
    fun `without the backend's wording the app's own is shown`() {
        state = AppUpdateUiState(UpdateStatus.Required(POLICY.copy(messages = emptyMap())))
        show()

        compose.onNodeWithText("This version is no longer supported. Update Vazie VPN to continue.").assertIsDisplayed()
    }

    @Test
    fun `nothing under a required update can be touched`() {
        state = AppUpdateUiState(UpdateStatus.Required(POLICY))
        show()

        // A real touch where the row is, through the window: the update screen must take it.
        tapTopLeftOfTheWindow()
        assertEquals(0, underneathClicks, "a control under the update screen was reachable")
    }

    @Test
    fun `system Back does nothing while the update is required`() {
        state = AppUpdateUiState(UpdateStatus.Required(POLICY))
        var backReachedTheApp = false
        compose.setContent {
            VazieTheme {
                val owner = LocalOnBackPressedDispatcherOwner.current!!
                // What the navigation host would register beneath the gate: it must never be called.
                androidx.compose.runtime.DisposableEffect(owner) {
                    val callback = object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            backReachedTheApp = true
                        }
                    }
                    owner.onBackPressedDispatcher.addCallback(callback)
                    onDispose { callback.remove() }
                }
                UpdateGate(state, {}, {}, {}, {}) { Underneath(it) }
            }
        }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertFalse(backReachedTheApp, "Back went through the update screen to the app")
        compose.onNodeWithTag(TAG_REQUIRED).assertIsDisplayed()
        assertFalse(compose.activity.isFinishing, "Back closed the app")
    }

    @Test
    fun `the update button opens the policy's page, and check again asks again`() {
        state = AppUpdateUiState(UpdateStatus.Required(POLICY))
        show()

        compose.onNodeWithTag(TAG_UPDATE).performClick()
        compose.onNodeWithTag(TAG_CHECK_AGAIN).performClick()

        assertEquals(listOf(POLICY), opened)
        assertEquals(1, checkedAgain)
    }

    @Test
    fun `when the requirement cannot be re-confirmed the screen says so`() {
        state = AppUpdateUiState(UpdateStatus.Required(POLICY), couldNotVerify = true)
        show()

        compose.onNodeWithText("Could not reach Vazie just now. The update is still required.").assertIsDisplayed()
    }

    @Test
    fun `the screen goes when the requirement does, and the app is as it was`() {
        state = AppUpdateUiState(UpdateStatus.Required(POLICY))
        show()
        compose.onNodeWithTag(TAG_REQUIRED).assertIsDisplayed()

        state = AppUpdateUiState(UpdateStatus.UpToDate)
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_REQUIRED).assertDoesNotExist()
        tapTopLeftOfTheWindow()
        assertEquals(1, underneathClicks)
    }

    @Test
    fun `an optional update is a dialog that can be postponed, over an app that stays usable`() {
        state = AppUpdateUiState(UpdateStatus.Optional(POLICY), prompt = UpdatePrompt.Available(POLICY))
        show()

        compose.onNodeWithText("Vazie VPN 1.4.0 is available").assertIsDisplayed()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("Update").performClick()

        assertEquals(1, postponed)
        assertEquals(listOf(POLICY), opened)
        compose.onNodeWithTag(TAG_REQUIRED).assertDoesNotExist()
    }

    @Test
    fun `a manual check on a current build says the installed version is current`() {
        state = AppUpdateUiState(prompt = UpdatePrompt.UpToDate)
        show()

        compose.onNodeWithText("You are up to date").assertIsDisplayed()
        compose.onNodeWithText("The latest version of Vazie VPN is installed.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a manual check with no network offers to try again`() {
        state = AppUpdateUiState(prompt = UpdatePrompt.CheckFailed)
        show()

        compose.onNodeWithText("Could not check for updates").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        assertEquals(1, checkedAgain)
        assertEquals(1, dismissed)
    }

    @Test
    fun `only an https page is ever opened`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadow = Shadows.shadowOf(context as android.app.Application)

        for (url in listOf("http://vazie.app/vpn", "intent://x#Intent;end", "market://details?id=x", "vazie-vpn://settings", "javascript:alert(1)")) {
            assertFalse(openUpdatePage(context, url), url)
            assertNull(shadow.nextStartedActivity, "started an activity for $url")
        }

        assertTrue(openUpdatePage(context, "https://vazie.app/vpn"))
        val intent: Intent = shadow.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("https://vazie.app/vpn"), intent.data)
        assertTrue(Intent.CATEGORY_BROWSABLE in intent.categories.orEmpty())
        assertNull(intent.component, "the page must go through the browser chooser, not to a named component")
    }

    /** A raw touch through the window, where the row sits: what a finger does, not a semantics action. */
    private fun tapTopLeftOfTheWindow() {
        compose.onRoot().performTouchInput { click(Offset(24f, 24f)) }
        compose.waitForIdle()
    }

    private companion object {
        val POLICY = AppVersionPolicy(42, "1.4.0", 39, "https://vazie.app/vpn", mapOf("en" to "Update Vazie VPN to continue."))
    }
}
