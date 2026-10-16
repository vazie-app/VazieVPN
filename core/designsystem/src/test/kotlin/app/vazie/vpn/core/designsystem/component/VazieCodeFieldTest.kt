package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The code field is one text field drawn as cells: typing and pasting go through it, each cell shows its
 * digit, and TalkBack meets one labelled field rather than six boxes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class VazieCodeFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private var code by mutableStateOf("")

    private fun show(errorMessage: String? = null) {
        compose.setContent {
            VazieTheme {
                VazieCodeField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(LENGTH) },
                    label = LABEL,
                    errorMessage = errorMessage,
                )
            }
        }
    }

    @Test
    fun `typing fills the cells one digit each`() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("1234")
        compose.waitForIdle()

        assertEquals("1234", code)
        listOf("1", "2", "3", "4").forEach { digit ->
            assertEquals(
                1,
                compose.onAllNodesWithText(digit, useUnmergedTree = true).fetchSemanticsNodes().size,
                "digit $digit is not in a cell of its own",
            )
        }
    }

    @Test
    fun `a pasted code lands whole`() {
        show()
        compose.onNode(hasSetTextAction()).performTextReplacement("987654")
        compose.waitForIdle()
        assertEquals("987654", code)
    }

    @Test
    fun `TalkBack meets one labelled field`() {
        show()
        compose.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(LABEL)))
        assertEquals(1, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun `an error is announced on the field`() {
        show(errorMessage = ERROR)
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, ERROR))
    }

    private companion object {
        const val LENGTH = 6
        const val LABEL = "Code from the email"
        const val ERROR = "That code did not work."
    }
}
