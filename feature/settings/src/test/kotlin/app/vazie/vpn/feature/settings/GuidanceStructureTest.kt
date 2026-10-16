package app.vazie.vpn.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.VazieGuideId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where guidance lives: once on the root, in two labelled sections on the Guides screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GuidanceStructureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `root settings offers one way to guidance and renders no guide of its own`() {
        val actions = renderSettings()

        compose.onAllNodes(saying(string(R.string.settings_guides_row))).onFirst()
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf(SettingsAction.OpenGuides), actions)

        // No guide content. A step, a steps heading or a platform action on the root screen would
        // mean the cards came back under another name.
        VazieGuideId.entries.forEach { guide ->
            assertEquals(
                0,
                compose.count(string(guide.stepsTitleRes)),
                "root Settings is rendering ${guide.name}'s steps again",
            )
        }
        assertEquals(0, compose.count(string(R.string.guide_widgets_action)))
        assertEquals(0, compose.count(string(R.string.guide_quick_settings_action)))
    }

    @Test
    fun `the guides screen has both sections, and everything is still reachable`() {
        renderGuides()

        compose.onAllNodes(saying(string(R.string.getting_started_section))).onFirst()
            .performScrollTo()
            .assertExists()
        compose.onAllNodes(saying(string(R.string.settings_guides_system_section))).onFirst()
            .performScrollTo()
            .assertExists()

        // Getting Started above System Integration, asserted rather than assumed: a reader who does
        // not know what a configuration is has no use for the widget guide yet.
        assertTrue(
            topOf(string(R.string.getting_started_section)) <
                topOf(string(R.string.settings_guides_system_section)),
            "System Integration came before Getting Started",
        )

        GettingStartedTopic.entries.forEach { topic ->
            compose.onAllNodes(saying(string(topic.titleRes))).onFirst().performScrollTo().assertHasClickAction()
        }
        VazieGuideId.entries.forEach { guide ->
            compose.onAllNodes(saying(string(guide.titleRes))).onFirst().performScrollTo().assertHasClickAction()
        }
        compose.onAllNodes(saying(string(R.string.settings_guides_replay_tour))).onFirst()
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun `opening the getting started topic is its own action`() {
        val actions = mutableListOf<GuidesAction>()
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                GuidesScreen(
                    state = GuidesUiState(guides = guideList(acknowledged = emptySet())),
                    onAction = { actions += it },
                )
            }
        }
        compose.waitForIdle()

        compose.onAllNodes(saying(string(GettingStartedTopic.FIRST_CONFIGURATION.titleRes))).onFirst()
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf<GuidesAction>(
                GuidesAction.OpenGettingStarted(GettingStartedTopic.FIRST_CONFIGURATION),
            ),
            actions.toList(),
            "a product-learning topic must not travel as a system-integration guide",
        )
    }

    /** The marker says the guide was opened and never that anything was installed. Every guide is on the same
     * terms, including the one about a gesture. */
    @Test
    fun `viewed is the strongest word the list uses`() {
        renderGuides(acknowledged = VazieGuideId.entries.toSet())

        assertEquals(
            VazieGuideId.entries.size,
            compose.count(string(R.string.settings_guides_viewed)),
            "an opened guide is missing its marker, or a usage guide was excluded again",
        )
        listOf("Installed", "Configured", "Completed", "Done").forEach { forbidden ->
            assertEquals(
                0,
                compose.count(forbidden),
                "the guides list claimed something Vazie cannot observe: " + forbidden,
            )
        }
    }

    private fun renderSettings(): List<SettingsAction> {
        val actions = mutableListOf<SettingsAction>()
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                SettingsScreen(
                    state = SettingsUiState(
                        appearance = Appearance.NIGHT_INDIGO,
                        appIcon = app.vazie.vpn.core.model.VazieAppIcon.ORBIT,
                        systemIntegration = SystemIntegrationUi(
                            canAddWidget = true,
                            canAddQuickSettingsTile = true,
                        ),
                    ),
                    onAction = { actions += it },
                )
            }
        }
        compose.waitForIdle()
        return actions
    }

    private fun renderGuides(
        acknowledged: Set<VazieGuideId> = emptySet(),
    ) {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                GuidesScreen(
                    state = GuidesUiState(guides = guideList(acknowledged = acknowledged)),
                    onAction = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /** A node that says this, whichever way its mode says things. */
    private fun saying(text: String) =
        hasText(text, substring = true, ignoreCase = true) or
            hasContentDescription(text, substring = true, ignoreCase = true)

    private fun topOf(text: String): Float =
        compose.onAllNodes(saying(text)).onFirst().fetchSemanticsNode().boundsInRoot.top

    private fun string(id: Int): String = context.getString(id)

    private fun ComposeContentTestRule.count(text: String): Int =
        onAllNodes(saying(text)).fetchSemanticsNodes().size
}
