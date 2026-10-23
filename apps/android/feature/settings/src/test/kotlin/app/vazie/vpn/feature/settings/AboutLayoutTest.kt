package app.vazie.vpn.feature.settings

import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.hasText
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** About holds together as a layout, and reads as a page about a character. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class AboutLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The developer card's heading is laid out as a heading, not as a column of letters. */
    @Test
    fun `the developer card gives its text room to be read`() {
        about()

        val title = compose.onNodeWithText(string(R.string.settings_made_by_title))
            .performScrollTo()
        val bounds = title.fetchSemanticsNode().boundsInRoot
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top

        assertTrue(
            width > MIN_TITLE_WIDTH_PX,
            "the developer card's title is $width px wide: the sticker has taken the row",
        )
        assertTrue(
            height < MAX_TITLE_HEIGHT_PX,
            "the developer card's title is $height px tall: it is wrapping one word per line",
        )
    }

    private fun SemanticsNodeInteraction.top(): Float = fetchSemanticsNode().positionInRoot.y

    private fun string(id: Int): String = context.getString(id)

    private fun about() {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                AboutScreen(
                    state = AboutUiState(about = AboutUi("1.0", 1, "debug")),
                    onAction = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        /** A third of a 360dp screen at xhdpi. The bug produced roughly one character - about 40px. */
        const val MIN_TITLE_WIDTH_PX = 240f

        /** Four lines of a title at this density. The bug produced a node taller than the window. */
        const val MAX_TITLE_HEIGHT_PX = 300f
    }
}
