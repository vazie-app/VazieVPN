package app.vazie.vpn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What a device answers that no unit test can. */
@HiltAndroidTest
class FirstRunTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** The application starts and draws something. */
    @Test
    fun theApplicationStarts() {
        compose.waitForIdle()
        assertTrue(
            "nothing was drawn",
            compose.onAllNodesWithText("", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** A fresh install has nothing selected and offers the one thing it can do. */
    @Test
    fun aFreshInstallHasNoConfigurationAndOffersToAddOne() {
        finishOnboardingIfShown()
        compose.onAllNodesWithText(addConfiguration(), substring = true, ignoreCase = true)
            .onFirst()
            .assertIsDisplayed()
    }

    /** Nowhere on the first run does Vazie name an account, a plan or a price. */
    @Test
    fun theFirstRunNamesNoAccountAndNoPrice() {
        finishOnboardingIfShown()
        listOf("Vazie account", "Аккаунт Vazie", "Vazie Free", "Subscription", "Подписка")
            .forEach { forbidden ->
                assertTrue(
                    "the first run says \"$forbidden\", which promises something this build cannot do",
                    compose.onAllNodesWithText(forbidden, substring = true)
                        .fetchSemanticsNodes()
                        .isEmpty(),
                )
            }
    }

    /** The import screen offers pasting a link, and does not read the clipboard to get there. */
    @Test
    fun theImportScreenOffersPastingAndNothingElse() {
        finishOnboardingIfShown()
        compose.onAllNodesWithText(addConfiguration(), substring = true, ignoreCase = true)
            .onFirst()
            .performClick()
        compose.waitForIdle()

        compose.onAllNodesWithText(paste(), substring = true, ignoreCase = true)
            .onFirst()
            .assertIsDisplayed()

        listOf("WireGuard", "QR", "Shadowsocks", "VMess", "Trojan").forEach { absent ->
            assertTrue(
                "the import screen offers \"$absent\", which Vazie cannot read",
                compose.onAllNodesWithText(absent, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /** Onboarding is shown once and this test does not care which run it is on. */
    private fun finishOnboardingIfShown() {
        compose.waitForIdle()
        val skip = compose.onAllNodesWithText(onboardingSkip(), substring = true, ignoreCase = true)
        if (skip.fetchSemanticsNodes().isNotEmpty()) {
            skip.onFirst().performClick()
            compose.waitForIdle()
        }
        // The guided tour, which opens over the empty state on a first run.
        val dismiss = compose.onAllNodesWithText(tourSkip(), substring = true, ignoreCase = true)
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) {
            dismiss.onFirst().performClick()
            compose.waitForIdle()
        }
    }

    /** A string as the device would render it, read from the same resource the screen draws. */
    private fun text(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun addConfiguration() = text(app.vazie.vpn.feature.home.R.string.home_add_config)

    private fun paste() = text(app.vazie.vpn.feature.config.R.string.config_action_paste)

    private fun onboardingSkip() =
        text(app.vazie.vpn.feature.onboarding.R.string.onboarding_skip)

    private fun tourSkip() = text(R.string.tour_skip)
}
