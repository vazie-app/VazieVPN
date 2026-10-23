package app.vazie.vpn.tour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.R
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.tour.LocalVazieTourTargets
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import app.vazie.vpn.core.designsystem.tour.rememberVazieTourTargets
import app.vazie.vpn.core.designsystem.tour.vazieTourTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tour waits for its targets, so a replay begins where a first run begins. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class TourReplayTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** The exact manual-replay path: the tour host is composed in the same frame the screen is, which is what
     * happens when replay navigates from Guides to Home and flips `replaying` at once. */
    @Test
    fun `replay begins at connect, with all four steps`() {
        render(homeArrivesLate = true)

        assertEquals(
            context.getString(R.string.tour_connect_title),
            currentTitle(),
            "the replay skipped the first step",
        )
        assertProgress(1, 3)
    }

    @Test
    fun `replay while already on home begins at connect too`() {
        render(homeArrivesLate = false)

        assertEquals(context.getString(R.string.tour_connect_title), currentTitle())
        assertProgress(1, 3)
    }

    /** Home's configuration card appears with the second state emission, later than the rest of the screen.
     * The old snapshot dropped it; the live set gains it. */
    @Test
    fun `a target that registers after the tour starts is gained, not lost`() {
        render(homeArrivesLate = true, configurationArrivesLater = true)

        assertProgress(1, 3)
        assertTrue(
            compose.onAllNodesWithTextCount(context.getString(R.string.tour_connect_title)) > 0,
            "the first step is not the connect step",
        )
    }

    @Test
    fun `walking to the end and replaying begins at one again`() {
        var runs by mutableStateOf(0)
        renderReplayable(runs = { runs }, onFinished = { runs += 1 })

        repeat(3) { compose.onNodeWithText(context.getString(R.string.tour_next)).performClick() }
        compose.onNodeWithText(context.getString(R.string.tour_done_dismiss)).performClick()
        compose.waitForIdle()

        assertEquals(context.getString(R.string.tour_connect_title), currentTitle())
        assertProgress(1, 3)
    }

    /** A tour with nothing to point at ends, rather than holding an invisible modal layer over the app. This
     * is the state the old code left behind on a replay that beat every target. */
    @Test
    fun `a tour with no targets at all finishes instead of hanging`() {
        var finished = 0
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                val targets = rememberVazieTourTargets()
                CompositionLocalProvider(LocalVazieTourTargets provides targets) {
                    Box(Modifier.fillMaxSize()) {
                        VazieTourHost(
                            targets = targets,
                            onFinished = { finished += 1 },
                            onOpenGuides = {},
                        )
                    }
                }
            }
        }
        // An empty set is given a grace period for a screen still arriving; past it, the tour ends.
        compose.mainClock.advanceTimeBy(EMPTY_GRACE_PLUS_MILLIS)
        compose.waitForIdle()

        assertEquals(1, finished, "the tour never reported itself finished, so the shell hid the app")
    }

    private fun render(homeArrivesLate: Boolean, configurationArrivesLater: Boolean = false) {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                val targets = rememberVazieTourTargets()
                var home by remember { mutableStateOf(!homeArrivesLate) }
                var configuration by remember { mutableStateOf(!configurationArrivesLater) }
                LaunchedEffect(Unit) {
                    home = true
                    configuration = true
                }
                CompositionLocalProvider(LocalVazieTourTargets provides targets) {
                    Box(Modifier.fillMaxSize()) {
                        Screen(home = home, configuration = configuration)
                        VazieTourHost(targets = targets, onFinished = {}, onOpenGuides = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun renderReplayable(runs: () -> Int, onFinished: () -> Unit) {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                val targets = rememberVazieTourTargets()
                CompositionLocalProvider(LocalVazieTourTargets provides targets) {
                    Box(Modifier.fillMaxSize()) {
                        Screen(home = true, configuration = true)
                        // Keyed on the run, so finishing and starting again is a fresh host exactly
                        // as it is in the shell, where `tourRunning` goes false and true.
                        androidx.compose.runtime.key(runs()) {
                            VazieTourHost(
                                targets = targets,
                                onFinished = onFinished,
                                onOpenGuides = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Screen(home: Boolean, configuration: Boolean) {
        if (!home) return
        Box(Modifier.size(VazieTheme.spacing.xxxl).vazieTourTarget(VazieTourTargetId.CONNECT_CONTROL))
        if (configuration) {
            Box(Modifier.size(VazieTheme.spacing.xxxl).vazieTourTarget(VazieTourTargetId.CONNECTION_CARD))
        }
        Box(Modifier.size(VazieTheme.spacing.xxxl).vazieTourTarget(VazieTourTargetId.SETTINGS_GEAR))
    }

    private fun currentTitle(): String = listOf(
        R.string.tour_connect_title,
        R.string.tour_configuration_title,
        R.string.tour_settings_title,
    ).map(context::getString)
        .first { compose.onAllNodesWithTextCount(it) > 0 }

    private fun assertProgress(number: Int, of: Int) {
        val expected = context.getString(R.string.tour_progress, number, of)
        assertTrue(
            compose.onAllNodesWithTextCount(expected) > 0,
            "expected progress $expected",
        )
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(
        text: String,
    ): Int = onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    private companion object {
        const val EMPTY_GRACE_PLUS_MILLIS = 1_500L
    }
}
