package app.vazie.vpn.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieLink
import app.vazie.vpn.core.model.VazieLinkSection
import app.vazie.vpn.core.model.VazieLinks
import app.vazie.vpn.core.model.VazieLocalizedText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The About screen and the feedback row show what the backend published, and what the app shipped with until it
 * has. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
class AboutLinksTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `published links replace the shipped ones, in the device's language`() {
        val actions = about(AboutLinksUi.from(PUBLISHED, language = "ru"))

        compose.onNodeWithText("Мой канал").performScrollTo().performClick()
        compose.onNodeWithText("новости").assertExists()
        compose.onAllNodesWithText(context.getString(R.string.settings_folio)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.settings_behance)).assertCountEquals(0)

        compose.onNodeWithText("Поддержать").performScrollTo().performClick()
        assertEquals(listOf<AboutAction>(AboutAction.OpenLink("https://t.me/vazieapp"), AboutAction.OpenLink("https://boosty.to/vazie")), actions)
    }

    @Test
    fun `a card the backend left empty is not shown`() {
        about(AboutLinksUi.from(PUBLISHED, language = "en"))

        compose.onNode(hasText(context.getString(R.string.settings_project_section), ignoreCase = true)).assertDoesNotExist()
        compose.onNodeWithText("My channel").performScrollTo()
        compose.onNodeWithText("Help", substring = false).performScrollTo()
    }

    @Test
    fun `with nothing published the shipped links are shown, Behance among them`() {
        about(links = null)

        compose.onNodeWithText(context.getString(R.string.settings_behance)).performScrollTo()
        compose.onNodeWithText(context.getString(R.string.settings_boosty_action)).performScrollTo()
    }

    /** The Settings row keeps its own words whatever the backend calls the link; only where it leads is published. */
    @Test
    fun `the feedback row reads its own words`() {
        settings()
        compose.onNodeWithText(context.getString(R.string.settings_feedback_row)).performScrollTo()
        compose.onAllNodesWithText("Написать нам").assertCountEquals(0)
    }

    @Test
    fun `feedback opens the published form, else the shipped one`() {
        val published = AboutLinksUi.from(WITH_FEEDBACK_AND_SUPPORT, language = "ru")
        assertEquals("https://forms.gle/published", feedbackUrl(published))
        assertEquals(AboutLink.BUG_REPORT.url, feedbackUrl(null))
        // Published without a feedback link: the row still works, as before.
        assertEquals(AboutLink.BUG_REPORT.url, feedbackUrl(AboutLinksUi.from(PUBLISHED, language = "ru")))
    }

    @Test
    fun `support writes to the first published mailto link, whatever its key`() {
        val published = AboutLinksUi.from(WITH_FEEDBACK_AND_SUPPORT, language = "ru")
        assertEquals("mailto:help@example.com", supportUrl(published))
        // The operator renamed the key in the admin panel: still the support row.
        val renamed = VazieLinks(WITH_FEEDBACK_AND_SUPPORT.links.map { if (it.key == "support_email") it.copy(key = "write_to_us") else it })
        assertEquals("mailto:help@example.com", supportUrl(AboutLinksUi.from(renamed, language = "ru")))
    }

    @Test
    fun `support falls back to the shipped address only while nothing is published`() {
        assertEquals(AboutLink.SUPPORT_EMAIL.url, supportUrl(null))
        // Published, but no mailto link (left out or switched off): no address, and so no row.
        assertNull(supportUrl(AboutLinksUi.from(PUBLISHED, language = "ru")))
    }

    private fun about(links: AboutLinksUi?): MutableList<AboutAction> {
        val actions = mutableListOf<AboutAction>()
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                AboutScreen(state = AboutUiState(about = AboutUi("1.0", 1, "debug"), links = links), onAction = { actions += it })
            }
        }
        compose.waitForIdle()
        return actions
    }

    private fun settings() {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                SettingsScreen(
                    state = SettingsUiState(appearance = Appearance.NIGHT_INDIGO, appIcon = VazieAppIcon.ORBIT),
                    onAction = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        val PUBLISHED = VazieLinks(
            listOf(
                VazieLink("channel", VazieLinkSection.AUTHOR, "https://t.me/vazieapp", VazieLocalizedText("Мой канал", "My channel"), VazieLocalizedText("новости", "news")),
                VazieLink("boosty", VazieLinkSection.SUPPORT, "https://boosty.to/vazie", VazieLocalizedText("Кофе", "Coffee"), action = VazieLocalizedText("Поддержать", "Help")),
            ),
        )

        val WITH_FEEDBACK_AND_SUPPORT = VazieLinks(
            PUBLISHED.links + listOf(
                VazieLink("support_email", VazieLinkSection.PROJECT, "mailto:help@example.com", VazieLocalizedText("Почта", "Email")),
                VazieLink("bug_report", VazieLinkSection.FEEDBACK, "https://forms.gle/published", VazieLocalizedText("Написать нам", "Write to us")),
            ),
        )
    }
}
