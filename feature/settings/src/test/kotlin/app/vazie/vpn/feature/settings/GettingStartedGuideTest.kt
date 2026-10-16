package app.vazie.vpn.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.SyntheticConfigExample
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The guide explains, and then hands over to the wizard that imports. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GettingStartedGuideTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the guide ends in the action that opens the existing wizard`() {
        val actions = render()

        compose.onNodeWithText(string(R.string.getting_started_add_action), ignoreCase = true)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(
            listOf<GettingStartedAction>(GettingStartedAction.AddConfiguration),
            actions.toList(),
        )
    }

    @Test
    fun `the example on screen is the guarded synthetic one`() {
        render()

        compose.onAllNodes(hasText(SyntheticConfigExample.VLESS_LINK)).onFirst().assertExists()
        compose.onAllNodes(
            hasContentDescription(string(R.string.getting_started_example_a11y), ignoreCase = true),
        ).onFirst().assertExists()
    }

    @Test
    fun `it names the formats it was told about and no others`() {
        render(formats = "EXAMPLEPROTO")

        compose.onAllNodes(hasText("EXAMPLEPROTO", substring = true)).onFirst().assertExists()
        assertEquals(
            0,
            compose.onAllNodes(hasText("WireGuard", substring = true, ignoreCase = true))
                .fetchSemanticsNodes().size,
            "the guide advertised a protocol nothing reads",
        )
    }

    @Test
    fun `it never mentions files, because there is no file import`() {
        render()

        listOf("file", "файл").forEach { word ->
            assertEquals(
                0,
                compose.onAllNodes(hasText(word, substring = true, ignoreCase = true))
                    .fetchSemanticsNodes().size,
                "the guide offered something Vazie cannot do: $word",
            )
        }
    }

    /** The management topic exists, is reachable, and says the thing about export that must not soften. */
    @Test
    fun `the management topic warns that an export carries credentials`() {
        renderTopic(GettingStartedTopic.MANAGE_CONFIGURATIONS)

        val body = context.getString(R.string.getting_started_manage_export_body)
        compose.onAllNodes(hasText(body, substring = true)).onFirst().assertExists()
        assertTrue(
            body.contains("credential", ignoreCase = true) ||
                body.contains("ключ", ignoreCase = true),
            "the export warning stopped naming what is in the file: " + body,
        )
        assertTrue(
            body.contains("password", ignoreCase = true) ||
                body.contains("парол", ignoreCase = true),
            "the export warning lost the comparison that makes it land: " + body,
        )
    }

    /** It offers no action, because it has nowhere to send anybody: the list it describes is one tab away and
     * the reader came from it. */
    @Test
    fun `the management topic ends in understanding rather than an action`() {
        renderTopic(GettingStartedTopic.MANAGE_CONFIGURATIONS)

        assertEquals(
            0,
            compose.onAllNodes(
                hasText(context.getString(R.string.getting_started_add_action), ignoreCase = true),
            ).fetchSemanticsNodes().size,
            "a guide about what you already have offered to add another",
        )
    }

    private fun renderTopic(topic: GettingStartedTopic) {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                GettingStartedScreen(
                    state = GettingStartedUiState(topic = topic, supportedFormats = "VLESS"),
                    onAction = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun render(
        formats: String = "VLESS",
    ): List<GettingStartedAction> {
        val actions = mutableListOf<GettingStartedAction>()
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                GettingStartedScreen(
                    state = GettingStartedUiState(
                        topic = GettingStartedTopic.FIRST_CONFIGURATION,
                        supportedFormats = formats,
                    ),
                    onAction = { actions += it },
                )
            }
        }
        compose.waitForIdle()
        return actions
    }

    private fun string(id: Int): String = context.getString(id)
}
