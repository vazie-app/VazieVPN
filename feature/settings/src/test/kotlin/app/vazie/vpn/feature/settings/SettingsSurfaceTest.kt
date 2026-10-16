package app.vazie.vpn.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieAppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a person actually sees and can do on the target-state surfaces. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Plans cases live in `PlansSurfaceTest`; only the Settings row that leads there stays here.

    // ---------- settings rows ----------

    /** The account and sync rows are not here at all — and that is the assertion. */
    @Test
    fun `settings names no account, no sync, no plan and no price`() {
        settings()
        listOf(
            "Vazie account", "Аккаунт Vazie",
            "Encrypted sync", "Шифрованная синхронизация",
            "Plan", "Тариф", "Vazie Free", "Subscription", "Подписка",
            "Sign in", "Войти", "Upgrade", "Оформить",
        ).forEach { token ->
            assertTrue(
                "settings still says \"$token\", which promises something this build cannot do",
                compose.onAllNodesWithText(token, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /** Split tunnelling is a real screen now: the row opens it, and says what it is doing. */
    @Test
    fun `the split tunneling row opens its screen and shows the current choice`() {
        val emitted = mutableListOf<SettingsAction>()
        settings(onAction = emitted::add)
        compose.onNodeWithText("All apps use VPN").assertIsDisplayed()
        compose.onNodeWithText("Split Tunneling").performClick()
        assertTrue("the row did not open split tunneling: $emitted", emitted == listOf(SettingsAction.OpenSplitTunnel))
    }

    /** No screen in Settings presents an identity Vazie does not have. */
    @Test
    fun `settings presents no identity`() {
        settings()
        listOf("Devices", "Sign in", "Create an account", "Guest", "Log in", "Signed in as")
            .forEach { token ->
                assertTrue(
                    "settings shows \"$token\" with no account system behind it",
                    compose.onAllNodesWithText(token, substring = true).fetchSemanticsNodes().isEmpty(),
                )
            }
    }

    @Test
    @Config(qualifiers = "+ru")
    fun `the split tunneling row is translated`() {
        settings()
        compose.onNodeWithText("Раздельное туннелирование").assertIsDisplayed()
        compose.onNodeWithText("Все приложения через VPN").assertIsDisplayed()
    }

    // ---------- harness ----------

    private fun settings(
        onAction: (SettingsAction) -> Unit = {},
    ) = compose.setContent {
        VazieTheme(appearance = Appearance.NIGHT_INDIGO) {
            SettingsScreen(
                state = SettingsUiState(
                    appearance = Appearance.NIGHT_INDIGO,
                    appIcon = VazieAppIcon.ORBIT,
                ),
                onAction = onAction,
            )
        }
    }
}
