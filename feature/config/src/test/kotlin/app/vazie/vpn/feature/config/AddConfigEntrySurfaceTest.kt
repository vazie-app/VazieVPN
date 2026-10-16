package app.vazie.vpn.feature.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The entry step advertises what exists, and nothing else. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AddConfigEntrySurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the paste button sits beside the field, and continue sends what is in it`() {
        val calls = render()

        compose.onNodeWithContentDescription(string(R.string.config_action_paste)).assertHasClickAction().performClick()
        assertEquals(1, calls.pastes)

        // Pinned below the scroll area, so it is on screen without scrolling; disabled while the field is empty.
        val next = compose.onNodeWithText(string(R.string.config_action_continue), ignoreCase = true)
        next.assertIsDisplayed().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("a pasted link")
        next.assertIsEnabled().performClick()
        assertEquals(1, calls.submits)
    }

    @Test
    fun `no absent import method is named, as a row or as a roadmap`() {
        render()

        listOf("QR", "JSON", "Manual setup", "Import file").forEach { absent ->
            assertEquals(
                0,
                compose.onAllNodes(hasText(absent, substring = true, ignoreCase = true))
                    .fetchSemanticsNodes().size,
                "the entry screen is advertising an import method that does not exist: $absent",
            )
        }
    }

    @Test
    fun `files are never mentioned, because there is no file import`() {
        render()

        listOf("file", "файл").forEach { word ->
            assertEquals(
                0,
                compose.onAllNodes(hasText(word, substring = true, ignoreCase = true))
                    .fetchSemanticsNodes().size,
                "the entry screen offered something Vazie cannot do: $word",
            )
        }
    }

    @Test
    fun `the example is on screen`() {
        render()

        compose.onAllNodes(
            hasContentDescription(string(R.string.config_method_example_a11y), ignoreCase = true),
        ).onFirst().assertExists()
    }

    @Test
    fun `the format sentence names what was passed in, not a hardcoded protocol`() {
        render(formats = "EXAMPLEPROTO")

        assertTrue(
            compose.onAllNodes(hasText("EXAMPLEPROTO", substring = true))
                .fetchSemanticsNodes().isNotEmpty(),
            "the screen ignored the formats it was given and said something of its own",
        )
    }

    private class Calls {
        var pastes = 0
        var submits = 0
    }

    private fun render(
        formats: String = "VLESS",
    ): Calls {
        val calls = Calls()
        compose.setContent {
            var link by remember { mutableStateOf("") }
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                AddConfigMethodScreen(
                    state = AddConfigUiState(supportedFormats = formats),
                    link = link,
                    onLinkChange = { link = it },
                    onPaste = { calls.pastes++ },
                    onSubmit = { calls.submits++ },
                    onAction = {},
                )
            }
        }
        compose.waitForIdle()
        return calls
    }

    private fun string(id: Int): String = context.getString(id)
}
