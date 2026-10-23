package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
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

/** The overlay, driven through the real registration pipeline rather than through a supplied `Rect`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class VazieTourOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an overlay with no measured target draws nothing at all`() {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                VazieTourOverlay(target = null) {
                    Text(text = CALLOUT_BODY, modifier = Modifier.testTag(CALLOUT))
                }
            }
        }
        compose.waitForIdle()

        assertEquals(
            0,
            compose.onAllNodes(hasText(CALLOUT_BODY)).fetchSemanticsNodes().size,
            "the overlay drew before it knew where its target was",
        )
    }

    @Test
    fun `a target registered by the modifier is what the overlay draws around`() {
        val bounds = renderHost()

        // The overlay read real layout bounds in its own coordinate space.
        assertTrue(bounds.width > 0f && bounds.height > 0f)
        compose.onNodeWithTag(CALLOUT).assertIsDisplayed()
    }

    @Test
    fun `the overlay swallows a tap aimed at the control it is highlighting`() {
        var connects = 0
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                TourHost(onConnect = { connects++ })
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(CONNECT).performClick()

        assertEquals(0, connects, "a tap reached the live control underneath a modal overlay")
    }

    @Test
    fun `the callout keeps its controls reachable when it barely fits`() {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                TourHost(tallCallout = true)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(NEXT).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(SKIP).assertIsDisplayed().assertHasClickAction()
    }

    private fun renderHost(): androidx.compose.ui.geometry.Rect {
        lateinit var captured: () -> androidx.compose.ui.geometry.Rect?
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                val targets = rememberVazieTourTargets()
                captured = { targets.bounds(VazieTourTargetId.CONNECT_CONTROL) }
                CompositionLocalProvider(LocalVazieTourTargets provides targets) {
                    HostContent(
                        target = targets.bounds(VazieTourTargetId.CONNECT_CONTROL),
                        onConnect = {},
                        tallCallout = false,
                    )
                }
            }
        }
        compose.waitForIdle()
        return requireNotNull(captured()) { "the modifier never registered the control" }
    }

    @Composable
    private fun TourHost(onConnect: () -> Unit = {}, tallCallout: Boolean = false) {
        val targets = rememberVazieTourTargets()
        CompositionLocalProvider(LocalVazieTourTargets provides targets) {
            HostContent(
                target = targets.bounds(VazieTourTargetId.CONNECT_CONTROL),
                onConnect = onConnect,
                tallCallout = tallCallout,
            )
        }
    }

    @Composable
    private fun HostContent(
        target: androidx.compose.ui.geometry.Rect?,
        onConnect: () -> Unit,
        tallCallout: Boolean,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                VazieButton(
                    text = "Connect",
                    onClick = onConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CONNECT)
                        .vazieTourTarget(VazieTourTargetId.CONNECT_CONTROL),
                )
            }
            VazieTourOverlay(target = target) {
                Box(Modifier.testTag(CALLOUT)) {
                    VazieTourCallout(
                        title = "Connect and disconnect",
                        body = if (tallCallout) CALLOUT_BODY.repeat(24) else CALLOUT_BODY,
                        labels = VazieTourCalloutLabels(
                            next = NEXT,
                            back = "Back",
                            skip = SKIP,
                            progress = "1 / 4",
                            progressSpoken = "Step 1 of 4",
                        ),
                        onNext = {},
                        onSkip = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val CONNECT = "connect"
        const val CALLOUT = "callout"
        const val CALLOUT_BODY = "This is the button that opens and closes the tunnel. "
        const val NEXT = "Next"
        const val SKIP = "Skip tour"
    }
}
