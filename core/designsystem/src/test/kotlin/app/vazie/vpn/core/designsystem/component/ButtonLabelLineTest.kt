package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import app.vazie.vpn.core.designsystem.theme.VazieSpacing
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A button's label is one line, always — squeezed, long or at a large font it ends in an ellipsis rather
 * than wrapping. A wrapped label is how "Далее" became "Дал / ее" in the tour. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi", fontScale = 1.5f)
class ButtonLabelLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val narrow = VazieSpacing().minTouchTarget * 2

    @Test
    fun `a VazieButton label never wraps`() {
        compose.setContent {
            VazieTheme {
                VazieButton(text = LONG, onClick = {}, modifier = Modifier.width(narrow))
            }
        }
        assertOneLine(LONG)
    }

    @Test
    fun `a text button label never wraps`() {
        compose.setContent {
            VazieTheme {
                VazieButton(text = LONG, onClick = {}, variant = VazieButtonVariant.Text, modifier = Modifier.width(narrow))
            }
        }
        assertOneLine(LONG)
    }

    @Test
    fun `the connection action label never wraps`() {
        compose.setContent {
            VazieTheme {
                VazieConnectionAction(
                    kind = VazieConnectionActionKind.Connect,
                    text = LONG,
                    onClick = {},
                    modifier = Modifier.width(narrow),
                )
            }
        }
        assertOneLine(LONG)
    }

    private fun assertOneLine(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        assertTrue(results.isNotEmpty(), "no text layout for the label")
        assertEquals(1, results.first().lineCount, "the label wrapped onto ${results.first().lineCount} lines")
    }

    private companion object {
        const val LONG = "Подключиться через конфигурацию с очень длинным названием"
    }
}
