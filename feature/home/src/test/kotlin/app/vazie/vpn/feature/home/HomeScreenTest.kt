package app.vazie.vpn.feature.home

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.theme.VazieSpacing
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The "Маршрут" Home: where its controls lead and how they read to TalkBack. No pixels are compared — this
 * checks behaviour and semantics only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val actions = mutableListOf<HomeAction>()

    private fun show(state: HomeUiState) {
        compose.setContent { VazieTheme { HomeScreen(state = state, onAction = { actions += it }) } }
    }

    @Test
    fun `the gear is described, large enough, and opens Settings`() {
        show(HomeFixtures.ready)
        val gear = compose.onNodeWithContentDescription(context.getString(R.string.home_a11y_settings))
        val target = VazieSpacing().minTouchTarget
        gear.assertHasClickAction().assertWidthIsAtLeast(target).assertHeightIsAtLeast(target)
        gear.performClick()
        assertEquals(listOf<HomeAction>(HomeAction.OpenSettings), actions)
    }

    @Test
    fun `the configurations are drawn on Home itself`() {
        compose.setContent {
            VazieTheme {
                HomeScreen(
                    state = HomeFixtures.ready,
                    onAction = { actions += it },
                    connections = { androidx.compose.material3.Text("configurations-list") },
                )
            }
        }
        compose.onNodeWithText("configurations-list").assertExists()
    }

    @Test
    fun `the route reads as one status, and pressing it does what the button does`() {
        show(HomeFixtures.ready)
        val description = context.getString(
            R.string.home_route_a11y,
            context.getString(R.string.home_route_title_disconnected),
            HomeFixtures.configuration.name,
        )
        compose.onNodeWithContentDescription(description).performClick()
        assertEquals(listOf<HomeAction>(HomeAction.Connect), actions)
    }

    @Test
    fun `a route that cannot act is not a button`() {
        show(HomeFixtures.unsupported)
        val description = context.getString(
            R.string.home_route_a11y,
            context.getString(R.string.home_route_title_disconnected),
            HomeFixtures.configuration.name,
        )
        val node = compose.onNodeWithContentDescription(description).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Role !in node.config, "a route that does nothing was announced as a button")
    }

    /** A connection that is reported before the route has arrived is not shown yet: the status stays on
     * "connecting" until the line has reached the server and the node has filled, then changes with it. */
    @Test
    fun `an early connection waits for the route to arrive`() {
        val state = mutableStateOf<HomeUiState>(HomeFixtures.ready.copy(connection = ConnectionUiState.Connecting))
        compose.mainClock.autoAdvance = false
        compose.setContent { VazieTheme { HomeScreen(state = state.value, onAction = { actions += it }) } }
        compose.mainClock.advanceTimeBy(FRAME)

        compose.runOnIdle { state.value = HomeFixtures.connected }
        compose.mainClock.advanceTimeBy(FRAME * 4)
        compose.onNodeWithText(context.getString(R.string.home_route_title_connecting)).assertExists()

        compose.mainClock.advanceTimeBy(ROUTE_STORY_MILLIS)
        compose.onNodeWithText(context.getString(R.string.home_route_title_connected)).assertExists()
    }

    /** A disconnection is never held: the words change at once, while the line draws back. */
    @Test
    fun `a disconnection is shown at once`() {
        val state = mutableStateOf<HomeUiState>(HomeFixtures.connected)
        compose.mainClock.autoAdvance = false
        compose.setContent { VazieTheme { HomeScreen(state = state.value, onAction = { actions += it }) } }
        compose.mainClock.advanceTimeBy(FRAME)

        compose.runOnIdle { state.value = HomeFixtures.ready }
        compose.mainClock.advanceTimeBy(FRAME * 2)
        compose.onNodeWithText(context.getString(R.string.home_route_title_disconnected)).assertExists()
    }

    private companion object {
        const val FRAME = 16L

        /** Longer than the whole connecting story (line, then the node filling). */
        const val ROUTE_STORY_MILLIS = 5_000L
    }
}
