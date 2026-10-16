package app.vazie.vpn.feature.connections

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertTrue

/** The swipe actions, and the promise that the gesture is never the only way to reach them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SwipeActionsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every configuration row offers delete, and only delete, without a gesture`() {
        render()

        val row = compose.onAllNodes(hasText(FIRST_NAME, substring = true)).onFirst()
        val actions = row.fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .map { it.label }

        assertEquals(
            listOf(context.getString(R.string.connections_delete)),
            actions,
            "the row offers something other than delete: $actions",
        )
    }

    @Test
    fun `delete asks before it deletes`() {
        val actions = mutableListOf<ConnectionsAction>()
        render() { actions += it }

        val row = compose.onAllNodes(hasText(FIRST_NAME, substring = true)).onFirst()
        row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == context.getString(R.string.connections_delete) }
            .action()
        compose.waitForIdle()

        assertTrue(
            actions.any { it is ConnectionsAction.RequestDelete },
            "the swipe deleted directly instead of asking: $actions",
        )
        assertTrue(
            actions.none { it == ConnectionsAction.ConfirmDelete },
            "the row confirmed its own deletion",
        )
    }

    @Test
    fun `the confirmation names what it is about to destroy`() {
        val actions = mutableListOf<ConnectionsAction>()
        val row = ConnectionsFixtures.populated.configurations.first()
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                ConnectionsScreen(
                    state = ConnectionsFixtures.populated.copy(deleting = row),
                    onAction = { actions += it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.connections_delete_title)).assertExists()
        compose.onNodeWithText(
            context.getString(R.string.connections_delete_body, row.name),
        ).assertExists()

        compose.onNodeWithText(context.getString(R.string.connections_delete_confirm)).performClick()
        assertTrue(actions.contains(ConnectionsAction.ConfirmDelete))
    }

    private fun render(
        onAction: (ConnectionsAction) -> Unit = {},
    ) {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                ConnectionsScreen(state = ConnectionsFixtures.populated, onAction = onAction)
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        val FIRST_NAME: String = ConnectionsFixtures.populated.configurations.first().name
    }
}
