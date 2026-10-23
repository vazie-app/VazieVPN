package app.vazie.vpn.feature.settings

import androidx.compose.ui.test.isHeading
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.VazieAppIcon
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Support and About are two things, and the help text is readable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
class SupportAndAboutTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `Support and About are separate sections`() {
        settings()

        // Headers uppercase their text, so a case-sensitive match tells the header from the row.
        compose.onNode(hasText(string(R.string.settings_help_section)) and isHeading())
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText(string(R.string.settings_about_section)) and isHeading())
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** Four sections, in this order: Appearance, Connection, Help & Support, About. */
    @Test
    fun `the sections come in order`() {
        settings()

        // Read without scrolling: a scrolling Column lays out every child anyway.
        val tops = listOf(
            R.string.settings_look_and_feel,
            R.string.settings_vpn_behavior,
            R.string.settings_help_section,
            R.string.settings_about_section,
        ).map { compose.onNode(hasText(string(it)) and isHeading()).top() }

        assertEquals(tops.sorted(), tops, "the sections are out of order")
    }

    /** Help & Support: guides, questions, feedback, support - in that order; About: the About row, then updates. */
    @Test
    fun `the rows sit under their sections in order`() {
        settings()

        val help = compose.onNode(hasText(string(R.string.settings_help_section)) and isHeading()).top()
        val rows = listOf(
            R.string.settings_guides_row,
            R.string.settings_faq_row,
            R.string.settings_feedback_row,
            R.string.settings_contact_support,
        ).map { compose.onNodeWithText(string(it)).top() }
        val about = compose.onNode(hasText(string(R.string.settings_about_section)) and isHeading()).top()
        val aboutRow = compose.onNodeWithText(string(R.string.settings_about_row)).top()
        val updates = compose.onNodeWithText(string(R.string.settings_check_updates)).top()

        assertTrue(help < rows.first(), "the guides are not under Help & Support")
        assertEquals(rows.sorted(), rows, "the Help & Support rows are out of order")
        assertTrue(rows.last() < about, "Help & Support and About are interleaved")
        assertTrue(about < aboutRow && aboutRow < updates, "About is not «About Vazie VPN», then updates")
    }

    /** Privacy lives on About now; Settings does not repeat it. */
    @Test
    fun `settings has no privacy row`() {
        settings()
        compose.onNodeWithText(string(R.string.settings_privacy_row)).assertDoesNotExist()
    }

    /** The feedback subtitle is on screen in full, in both languages. */
    @Test
    fun `the feedback description is not truncated in English`() {
        settings()
        assertUntruncated(string(R.string.settings_feedback_row_body))
    }

    /** Russian is the case that matters: it runs about a third longer than English. */
    @Test
    @Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h760dp-xhdpi")
    fun `the feedback description is not truncated in Russian`() {
        settings()
        assertUntruncated(string(R.string.settings_contact_support_body))
        assertUntruncated(string(R.string.settings_feedback_row_body))
    }

    /** A large system font wraps the longest title and subtitle; nothing is cut. */
    @Test
    @Config(sdk = [34], qualifiers = "ru-rRU-w320dp-h760dp-xhdpi", fontScale = 2.0f)
    fun `nothing is cut at twice the font size`() {
        settings()
        assertUntruncated(string(R.string.settings_split_tunneling))
        assertUntruncated(string(R.string.settings_contact_support))
        assertUntruncated(string(R.string.settings_contact_support_body))
    }

    @Test
    fun `pressing the support row asks to write to support`() {
        val actions = mutableListOf<SettingsAction>()
        settings { actions += it }

        compose.onNodeWithText(string(R.string.settings_contact_support)).performScrollTo().performClick()

        assertEquals<List<SettingsAction>>(listOf(SettingsAction.ContactSupport), actions)
    }

    /** With no support address published there is nothing to open, so there is no row. */
    @Test
    fun `no support address, no support row`() {
        settings(canContactSupport = false)
        compose.onNodeWithText(string(R.string.settings_contact_support)).assertDoesNotExist()
    }

    /** The version moved from the updates row to the footer. */
    @Test
    fun `the version is in the footer, not on the updates row`() {
        settings(versionName = "1.2.3")

        compose.onNodeWithText("Vazie VPN · 1.2.3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_footer_made_by)).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("1.2.3", substring = true)).fetchSemanticsNodes().let {
            assertEquals(1, it.size, "the version is shown more than once")
        }
    }

    @Test
    fun `pressing check for updates asks once, and not while a check runs`() {
        val actions = mutableListOf<SettingsAction>()
        settings { actions += it }
        compose.onNodeWithText(string(R.string.settings_check_updates)).performScrollTo().performClick()
        assertEquals<List<SettingsAction>>(listOf(SettingsAction.CheckForUpdates), actions)

        actions.clear()
        settings(checkingForUpdates = true) { actions += it }
        compose.onNodeWithText(string(R.string.settings_check_updates_checking)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_check_updates)).performClick()
        assertTrue(actions.isEmpty(), "a second check started while one was running")
    }

    @Test
    fun `About opens the privacy page`() {
        val actions = mutableListOf<AboutAction>()
        about { actions += it }
        compose.onNodeWithText(string(R.string.settings_privacy_row)).performScrollTo().performClick()
        assertEquals<List<AboutAction>>(listOf(AboutAction.OpenPrivacy), actions)
    }

    /** The developer card carries the picture its text is about, read from the content description because
     * that is the part a screen reader gets. */
    @Test
    fun `About shows Vazovsky beside his own card`() {
        about()

        compose.onNodeWithText(string(R.string.settings_made_by_title))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            context.getString(DesignSystemStrings.VAZOVSKY_STICKER),
        ).performScrollTo().assertIsDisplayed()
    }

    /** The feedback row is in one place, not two. */
    @Test
    fun `About has the feedback card, under its own heading`() {
        about()
        compose.onNodeWithText(string(R.string.settings_feedback_section)).performScrollTo().assertExists()
        assertEquals(
            1,
            compose.onAllNodes(hasText(string(R.string.settings_report_bug))).fetchSemanticsNodes().size,
            "the feedback link is in the «Обратная связь» card once",
        )
    }

    @Test
    fun `pressing the feedback row asks to open the form`() {
        val actions = mutableListOf<SettingsAction>()
        settings { actions += it }

        compose.onNodeWithText(string(R.string.settings_feedback_row)).performScrollTo().performClick()

        assertEquals<List<SettingsAction>>(listOf(SettingsAction.ReportBug), actions)
    }

    /** The subtitle node reports the whole string. */
    private fun assertUntruncated(expected: String) {
        val node = compose.onNodeWithText(expected, useUnmergedTree = true).performScrollTo()
        val shown = node.fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .joinToString("") { it.text }
        assertEquals(expected, shown, "the help description is cut off")
        node.assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.top(): Float =
        fetchSemanticsNode().positionInRoot.y

    private fun string(id: Int): String = context.getString(id)

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** Set once per test; a second call swaps what is shown, since a compose rule takes one setContent. */
    private fun show(content: @Composable () -> Unit) {
        if (screen == null) compose.setContent { screen?.invoke() }
        screen = { VaziePreviewTheme(Appearance.NIGHT_INDIGO) { content() } }
        compose.waitForIdle()
    }

    private fun settings(
        versionName: String = "",
        checkingForUpdates: Boolean = false,
        canContactSupport: Boolean = true,
        onAction: (SettingsAction) -> Unit = {},
    ) = show {
        SettingsScreen(
            state = SettingsUiState(
                appearance = Appearance.NIGHT_INDIGO,
                appIcon = VazieAppIcon.ORBIT,
                systemIntegration = SystemIntegrationUi(),
                versionName = versionName,
                checkingForUpdates = checkingForUpdates,
                canContactSupport = canContactSupport,
            ),
            onAction = onAction,
        )
    }

    private fun about(onAction: (AboutAction) -> Unit = {}) = show {
        AboutScreen(
            state = AboutUiState(about = AboutUi("1.0", 1, "debug")),
            onAction = onAction,
        )
    }

    /** The sticker's description lives in `:core:designsystem`, whose `R` this module cannot name directly
     * without an import that reads worse than the indirection. */
    private object DesignSystemStrings {
        val VAZOVSKY_STICKER =
            app.vazie.vpn.core.designsystem.R.string.vazie_a11y_vazovsky_sticker
    }
}
