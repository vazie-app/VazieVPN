package app.vazie.vpn.feature.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.VazieFaqItem
import app.vazie.vpn.core.model.VazieFaqLink
import app.vazie.vpn.core.model.VazieFaqText
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Questions and answers: only what the backend published, in the device's language, with links to the site; and
 * with nothing cached, a way to try again instead of any text of the app's own. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaqScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<String>()
    private var retried = 0

    private fun show(state: FaqUiState) {
        compose.setContent {
            val handler = object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }
            CompositionLocalProvider(LocalUriHandler provides handler) {
                VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                    FaqScreen(onBack = {}, state = state, onRetry = { retried++ })
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `every published question is shown, with its answer`() {
        show(FaqUiState.Ready(ITEMS))
        compose.onNodeWithText("Where are configurations stored?").performScrollTo()
        compose.onNodeWithText("Server access.").performScrollTo()
        compose.onNodeWithText("Как связаться?").performScrollTo()
    }

    @Test
    fun `a site path opens on the site, an https link as it is`() {
        show(FaqUiState.Ready(ITEMS))
        compose.onNodeWithText("Refunds").performScrollTo().performClick()
        compose.onNodeWithText("Telegram").performScrollTo().performClick()
        assertEquals(listOf("https://site.example/legal/refunds", "https://t.me/vazieapp"), opened)
    }

    @Test
    fun `with nothing cached the screen offers to try again`() {
        show(FaqUiState.Unavailable)
        compose.onNodeWithText(context.getString(R.string.faq_unavailable)).assertExists()
        compose.onNodeWithText(context.getString(R.string.faq_retry)).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `everything switched off is said, not filled in`() {
        show(FaqUiState.Ready(emptyList()))
        compose.onNodeWithText(context.getString(R.string.faq_empty)).assertExists()
    }

    private companion object {
        val ITEMS = listOf(
            VazieFaqItem(
                key = "what",
                question = VazieFaqText("Где хранятся конфигурации?", "Where are configurations stored?"),
                answer = VazieFaqText("Доступ к серверам.", "Server access."),
                link = VazieFaqLink("/legal/refunds", VazieFaqText("Возвраты", "Refunds")),
            ),
            VazieFaqItem(
                key = "contact",
                question = VazieFaqText("Как связаться?"),
                answer = VazieFaqText("Напишите нам."),
                link = VazieFaqLink("https://t.me/vazieapp", VazieFaqText("Telegram")),
            ),
        )
    }
}
