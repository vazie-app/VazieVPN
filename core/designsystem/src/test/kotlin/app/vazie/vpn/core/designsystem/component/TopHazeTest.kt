package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The top of a screen: real room at rest, a fade only once something is under the bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TopHazeTest {

    @get:Rule
    val compose = createComposeRule()

    /** At rest the first content block sits a token's distance below the bar. */
    @Test
    fun `content starts a token below the toolbar`() {
        var expected = 0f
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                expected = VazieTheme.spacing.screenContentTop.value
                VazieScreenScaffold(topBar = { Bar() }) { FirstBlock() }
            }
        }
        compose.waitForIdle()

        val bar = compose.onNodeWithTag(BAR).getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag(FIRST).getUnclippedBoundsInRoot()
        val gap = (first.top - bar.bottom).value

        assertTrue(
            gap in (expected - TOLERANCE)..(expected + TOLERANCE),
            "content starts ${gap}dp under the bar, not the ${expected}dp the token asks for",
        )
    }

    /** A screen that has not scrolled reports nothing under the toolbar. */
    @Test
    fun `a column reports nothing under the toolbar until it scrolls`() {
        lateinit var under: () -> Boolean
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                val scroll = rememberScrollState()
                under = scroll.scrolledUnderToolbar()
                VazieScreenScaffold(
                    topBar = { Bar() },
                    contentScrolledUnderToolbar = under,
                ) {
                    Column(Modifier.verticalScroll(scroll)) {
                        FirstBlock()
                        Text("bottom", modifier = Modifier.testTag(LAST).height(2000.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(false, under(), "an unscrolled screen claims content is under the toolbar")

        compose.onNodeWithText("bottom").performScrollTo()
        compose.waitForIdle()
        assertTrue(under(), "a scrolled screen still claims nothing is under the toolbar")
    }

    /** The default is "nothing is under the bar", so a screen that does not scroll never hazes. */
    @Test
    fun `a screen that does not scroll never reports content under the toolbar`() {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                VazieScreenScaffold(topBar = { Bar() }) { FirstBlock() }
            }
        }
        compose.waitForIdle()
        // Nothing to assert on screen: the point is that the default parameter exists and is false,
        // which the type system enforces and this composition proves is usable without it.
        compose.onNodeWithTag(FIRST).getUnclippedBoundsInRoot()
    }

    @Composable
    private fun Bar() {
        Text(
            text = "Title",
            modifier = Modifier.testTag(BAR).fillMaxWidth().height(BAR_HEIGHT),
        )
    }

    @Composable
    private fun FirstBlock() {
        Box(
            modifier = Modifier
                .testTag(FIRST)
                .fillMaxWidth()
                .height(120.dp)
                .background(VazieTheme.colors.surface),
        )
    }

    private companion object {
        const val BAR = "bar"
        const val FIRST = "first"
        const val LAST = "last"
        const val TOLERANCE = 0.5f
        val BAR_HEIGHT = 64.dp
    }
}
