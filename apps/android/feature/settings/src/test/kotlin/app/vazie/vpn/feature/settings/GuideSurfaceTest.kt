package app.vazie.vpn.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
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

/** A guide is setup, not trivia — asserted where guides now live. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GuideSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a device that can add a widget offers the button`() {
        render(VazieGuideId.WIDGETS, SystemIntegrationUi(canAddWidget = true))

        compose.onNodeWithText(string(R.string.guide_widgets_action))
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun `a launcher that declines the pin request offers no button for it`() {
        render(VazieGuideId.WIDGETS, SystemIntegrationUi(canAddWidget = false))

        assertEquals(
            0,
            compose.count(string(R.string.guide_widgets_action)),
            "a widget button was drawn on a launcher that would decline it",
        )
    }

    @Test
    fun `an android below 33 offers no tile button`() {
        render(VazieGuideId.QUICK_SETTINGS, SystemIntegrationUi(canAddQuickSettingsTile = false))

        assertEquals(
            0,
            compose.count(string(R.string.guide_quick_settings_action)),
            "a tile button was drawn on a device that cannot be asked",
        )
    }

    /** The usage guide never gets a button, whatever the device says: there is no request to add a launcher
     * shortcut, because they are not added — holding the icon already shows them. */
    @Test
    fun `the usage guide never offers a platform action`() {
        val actions = render(
            VazieGuideId.LAUNCHER_SHORTCUTS,
            SystemIntegrationUi(canAddWidget = true, canAddQuickSettingsTile = true),
        )

        assertTrue(
            actions.none { it is GuideDetailAction.Settings },
            "a usage guide invented a setup step for something that needs none",
        )
    }

    /** The tile guide on a device that cannot be asked: the case where the button is absent, so the steps are
     * the whole answer rather than a consolation prize beside a shortcut. */
    @Test
    fun `the steps are there whether or not the platform can be asked`() {
        val guide = VazieGuideId.QUICK_SETTINGS
        render(guide, SystemIntegrationUi(canAddQuickSettingsTile = false))

        compose.onNodeWithText(string(guide.stepsTitleRes)).performScrollTo().assertExists()
        guide.stepsRes.forEachIndexed { index, step ->
            val numbered = "${index + 1}. ${string(step)}"
            assertTrue(
                compose.count(numbered) > 0,
                "step ${index + 1} of $guide is missing: $numbered",
            )
        }
    }

    @Test
    fun `pressing the button asks for the surface`() {
        val actions = render(VazieGuideId.WIDGETS, SystemIntegrationUi(canAddWidget = true))

        compose.onNodeWithText(string(R.string.guide_widgets_action))
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf<GuideDetailAction>(GuideDetailAction.Settings(SettingsAction.AddWidget)),
            actions.filterIsInstance<GuideDetailAction.Settings>(),
        )
    }

    @Test
    fun `opening a guide marks it viewed, once`() {
        val actions = render(VazieGuideId.QUICK_SETTINGS, SystemIntegrationUi())

        assertEquals(
            listOf(GuideDetailAction.MarkViewed(VazieGuideId.QUICK_SETTINGS)),
            actions.filterIsInstance<GuideDetailAction.MarkViewed>(),
            "opening the guide is the acknowledgement, and it happens exactly once",
        )
    }

    private fun render(
        guide: VazieGuideId,
        integration: SystemIntegrationUi,
    ): List<GuideDetailAction> {
        val actions = mutableListOf<GuideDetailAction>()
        compose.setContentOnce(guide, integration) { actions += it }
        return actions
    }

    private fun ComposeContentTestRule.setContentOnce(
        guide: VazieGuideId,
        integration: SystemIntegrationUi,
        onAction: (GuideDetailAction) -> Unit,
    ) {
        setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                GuideDetailScreen(
                    state = GuideDetailUiState(id = guide, systemIntegration = integration),
                    onAction = onAction,
                )
            }
        }
        waitForIdle()
    }

    private fun string(id: Int): String = context.getString(id)

    private fun ComposeContentTestRule.count(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
