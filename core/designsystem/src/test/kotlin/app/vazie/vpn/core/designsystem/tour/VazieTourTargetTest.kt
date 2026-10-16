package app.vazie.vpn.core.designsystem.tour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The registration pipeline, and the promise that it costs the interface nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class VazieTourTargetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an unregistered target has no bounds, rather than bounds at the origin`() {
        val registry = VazieTourTargetRegistry()

        assertNull(
            registry.bounds(VazieTourTargetId.CONNECT_CONTROL),
            "an overlay would have drawn a spotlight at (0,0)",
        )
    }

    @Test
    fun `a laid-out target reports where it actually is`() {
        val registry = VazieTourTargetRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalVazieTourTargets provides registry) {
                Box(Modifier.padding(24.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .vazieTourTarget(VazieTourTargetId.CONNECT_CONTROL),
                    )
                }
            }
        }
        compose.waitForIdle()

        val bounds = assertNotNull(registry.bounds(VazieTourTargetId.CONNECT_CONTROL))
        assertTrue(bounds.width > 0f && bounds.height > 0f, "a measured target has a size")
        assertTrue(bounds.left > 0f && bounds.top > 0f, "the padding should have moved it off the origin")
    }

    @Test
    fun `leaving the composition removes the target`() {
        val registry = VazieTourTargetRegistry()
        var present by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalVazieTourTargets provides registry) {
                if (present) {
                    Box(Modifier.size(48.dp).vazieTourTarget(VazieTourTargetId.SETTINGS_GEAR))
                }
            }
        }
        compose.waitForIdle()
        assertNotNull(registry.bounds(VazieTourTargetId.SETTINGS_GEAR))

        present = false
        compose.waitForIdle()

        assertNull(
            registry.bounds(VazieTourTargetId.SETTINGS_GEAR),
            "a tour could have pointed at a control that is no longer on screen",
        )
    }

    /** The claim that keeps the targeted screens from moving. */
    @Test
    fun `the modifier changes no layout at all`() {
        val registry = VazieTourTargetRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalVazieTourTargets provides registry) {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        Modifier
                            .testTag(PLAIN)
                            .size(120.dp, 56.dp),
                    ) { Text("Connect") }
                    Box(
                        Modifier
                            .vazieTourTarget(VazieTourTargetId.CONNECTION_CARD)
                            .testTag(TARGETED)
                            .size(120.dp, 56.dp),
                    ) { Text("Connect") }
                }
            }
        }
        compose.waitForIdle()

        val plain = compose.onNodeWithTag(PLAIN).fetchSemanticsNode().boundsInRoot
        val targeted = compose.onNodeWithTag(TARGETED).fetchSemanticsNode().boundsInRoot

        assertEquals(plain.width, targeted.width, "the modifier changed a measured width")
        assertEquals(plain.height, targeted.height, "the modifier changed a measured height")
        assertEquals(plain.left, targeted.left, "the modifier changed where a control is placed")
        assertEquals(
            plain.bottom,
            targeted.top,
            "the modifier contributed space of its own between the two boxes",
        )
    }

    private companion object {
        const val PLAIN = "plain"
        const val TARGETED = "targeted"
    }
}
