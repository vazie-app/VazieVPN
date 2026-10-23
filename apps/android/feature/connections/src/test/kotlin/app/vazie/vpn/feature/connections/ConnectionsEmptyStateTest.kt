package app.vazie.vpn.feature.connections

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** An empty state that names an action has to contain it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ConnectionsEmptyStateTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the standard empty state offers the action it describes`() {
        val actions = render()

        clickAddConfiguration()

        assertEquals(listOf(ConnectionsAction.AddConfiguration), actions)
    }

    /** Matched case-insensitively: casing is a rendering decision of `vazieUppercase`. */
    private fun clickAddConfiguration() {
        compose.onNodeWithText(
            text = string(R.string.connections_add_configuration),
            ignoreCase = true,
        )
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
    }

    /** The empty state may not name a protocol Vazie cannot read. */
    @Test
    fun `the empty state teaches without naming a protocol nothing reads`() {
        val body = context.getString(R.string.connections_empty_body)

        listOf("WireGuard", "Xray", "VLESS").forEach { protocol ->
            assertFalse(
                body.contains(protocol, ignoreCase = true),
                "the empty state names a protocol, which belongs in the import flow: " + body,
            )
        }
        // It still teaches: an empty state that only reports emptiness has wasted the one moment
        // somebody is looking at it.
        assertTrue(
            body.contains("provider", ignoreCase = true) || body.contains("server", ignoreCase = true),
            "the empty state stopped saying where a configuration comes from: " + body,
        )
    }

    private fun render(): List<ConnectionsAction> {
        val actions = mutableListOf<ConnectionsAction>()
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                ConnectionsScreen(
                    state = ConnectionsUiState(),
                    onAction = { actions += it },
                )
            }
        }
        return actions
    }

    private fun string(id: Int): String = context.getString(id)
}
