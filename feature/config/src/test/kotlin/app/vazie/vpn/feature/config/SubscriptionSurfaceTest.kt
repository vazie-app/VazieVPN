package app.vazie.vpn.feature.config

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A subscription on step 2: what its list held, what was left out, and the one button that adds it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SubscriptionSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the list is shown by name with its server, and one button adds all of it`() {
        val actions = preview(
            DetectionUiState.Subscription(
                host = HOST,
                configs = persistentListOf(AddConfigFixtures.reality, AddConfigFixtures.webSocket),
                skipped = 3,
            ),
        )

        compose.onNodeWithText(HOST).assertIsDisplayed()
        compose.onNodeWithText(AddConfigFixtures.reality.suggestedName).assertIsDisplayed()
        compose.onNodeWithText(AddConfigFixtures.webSocket.server, substring = true).assertIsDisplayed()
        // Below the list, so possibly below the fold: it has to exist, not to fit.
        compose.onNodeWithText(plural(R.plurals.config_subscription_skipped, 3)).assertExists()
        compose.onNodeWithText(plural(R.plurals.config_action_add_all, 2), ignoreCase = true).performClick()

        assertEquals(listOf<AddConfigAction>(AddConfigAction.ImportSubscription), actions)
    }

    @Test
    fun `while the list is on its way there is nothing to press but close`() {
        preview(DetectionUiState.SubscriptionLoading(HOST))

        compose.onNodeWithText(context.getString(R.string.config_subscription_loading, HOST)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.config_action_paste), ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.config_action_continue), ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `a link that gave no list says why and offers the clipboard again`() {
        val actions = preview(DetectionUiState.SubscriptionFailed(HOST, SubscriptionFailureUi.REFUSED))

        compose.onNodeWithText(context.getString(R.string.config_subscription_refused, HOST)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.config_action_paste), ignoreCase = true).performClick()

        assertEquals(listOf<AddConfigAction>(AddConfigAction.PasteRequested), actions)
    }

    @Test
    fun `the last step counts what a subscription added`() {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                ImportDoneScreen(state = AddConfigUiState(importedCount = 5), onAction = {})
            }
        }

        compose.onNodeWithText(plural(R.plurals.config_done_many_title, 5)).assertIsDisplayed()
    }

    private fun preview(detection: DetectionUiState): List<AddConfigAction> {
        val actions = mutableListOf<AddConfigAction>()
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                ImportPreviewScreen(
                    state = AddConfigUiState(method = ImportMethodUi.PASTE, detection = detection),
                    onAction = { actions += it },
                )
            }
        }
        compose.waitForIdle()
        return actions
    }

    private fun plural(id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)

    private companion object {
        const val HOST = "sub.example.net"
    }
}
