package app.vazie.vpn.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The configuration coach mark is shown once, and its "once" is a different once from the tour's. */
class ConfigurationGuidanceTest {

    private val directory: File = Files.createTempDirectory("vazie-guidance").toFile()

    @Test
    fun `a fresh install has not been shown it`() = runTest {
        assertFalse(preferences().snapshot().configurationGuidanceSeen)
    }

    @Test
    fun `an existing install is shown it, unlike the tour`() = runTest {
        // A file from before this version: onboarding long finished, no guidance key.
        write("onboarding_completed=true", "tour_state=DONE")

        val state = preferences().snapshot()
        assertEquals(TourState.DONE, state.tourState, "an old install was ambushed by the tour")
        assertFalse(
            state.configurationGuidanceSeen,
            "an old install never learns about a gesture that did not exist when it was set up",
        )
    }

    @Test
    fun `once shown, it stays shown across a restart`() = runTest {
        preferences().setConfigurationGuidanceSeen()

        assertTrue(
            preferences().snapshot().configurationGuidanceSeen,
            "the coach mark would come back on the next launch, which is an argument not a suggestion",
        )
    }

    @Test
    fun `it is independent of the tour in both directions`() = runTest {
        preferences().setConfigurationGuidanceSeen()
        assertEquals(
            TourState.DONE,
            preferences().snapshot().tourState,
            "seeing the coach mark decided something about the tour",
        )

        val second = AppStorage.preferences(filesDir = Files.createTempDirectory("v2").toFile())
        second.setOnboardingCompleted(true)
        assertFalse(
            second.snapshot().configurationGuidanceSeen,
            "finishing onboarding decided something about the coach mark",
        )
    }

    private fun preferences(): AppPreferences = AppStorage.preferences(filesDir = directory)

    private fun write(vararg lines: String) {
        val file = File(File(directory, AppStorage.DIRECTORY), "preferences")
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
