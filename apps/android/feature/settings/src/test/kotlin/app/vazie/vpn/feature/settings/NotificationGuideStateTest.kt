package app.vazie.vpn.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.VazieGuideId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** The notification guide is the only one that can read its own subject, and it has to. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotificationGuideStateTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `notifications off says so, and offers the way to change it`() {
        render(SystemIntegrationUi(canOpenNotificationSettings = true, notificationsEnabled = false))

        compose.onNodeWithText(string(R.string.guide_notification_body_off))
            .performScrollTo()
            .assertExists()
        compose.onNodeWithText(string(R.string.guide_notification_action))
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun `notifications on does not ask for what it already has`() {
        render(SystemIntegrationUi(canOpenNotificationSettings = true, notificationsEnabled = true))

        compose.onNodeWithText(string(R.string.guide_notification_body_on))
            .performScrollTo()
            .assertExists()
        assertEquals(
            0,
            compose.count(string(R.string.guide_notification_body_off)),
            "the guide told somebody with notifications on that they were off",
        )
        // Still offered: it is a door, not a request. See
        // `SettingsAction.OpenNotificationSettings`. `SettingsAction.OpenNotificationSettings`.
        compose.onNodeWithText(string(R.string.guide_notification_action))
            .performScrollTo()
            .assertHasClickAction()
    }

    /** A device with nowhere to send somebody gets the steps and no button — the same rule the widget and
     * tile guides live under, applied to the one action that practically never fails. */
    @Test
    fun `nowhere to send them means no button, and the steps are still there`() {
        render(SystemIntegrationUi(canOpenNotificationSettings = false, notificationsEnabled = false))

        assertEquals(
            0,
            compose.count(string(R.string.guide_notification_action)),
            "a button was drawn on a device with no notification settings to open",
        )
        compose.onNodeWithText(string(R.string.guide_notification_manual))
            .performScrollTo()
            .assertExists()
    }

    private fun render(integration: SystemIntegrationUi) {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                GuideDetailScreen(
                    state = GuideDetailUiState(
                        id = VazieGuideId.NOTIFICATION,
                        systemIntegration = integration,
                    ),
                    onAction = {},
                )
            }
        }
    }

    private fun string(id: Int): String = context.getString(id)

    private fun ComposeContentTestRule.count(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
