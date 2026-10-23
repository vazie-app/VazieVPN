package app.vazie.vpn.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** When the tour is owed, when it is not, and what happens to everybody who was already here. */
class TourPersistenceTest {

    private val directory: File = Files.createTempDirectory("vazie-tour").toFile()

    @Test
    fun `a fresh install owes nothing until onboarding is finished`() = runTest {
        // Nothing has happened yet, so nothing is owed. The tour is a consequence of finishing
        // onboarding, not of installing.
        assertEquals(TourState.DONE, preferences().snapshot().tourState)
    }

    @Test
    fun `finishing onboarding owes the tour, in the same write`() = runTest {
        preferences().setOnboardingCompleted(true)

        val state = preferences().snapshot()
        assertEquals(true, state.onboardingCompleted)
        assertEquals(TourState.PENDING, state.tourState)
    }

    @Test
    fun `the tour survives a process death mid-tour and is still owed`() = runTest {
        preferences().setOnboardingCompleted(true)

        // A second store over the same directory is this suite's stand-in for a new process.
        assertEquals(TourState.PENDING, preferences().snapshot().tourState)
    }

    @Test
    fun `finishing or skipping the tour settles it for good`() = runTest {
        preferences().setOnboardingCompleted(true)
        preferences().setTourDone()

        assertEquals(TourState.DONE, preferences().snapshot().tourState)
    }

    @Test
    fun `an existing install that predates the tour is not shown one`() = runTest {
        // Exactly what a file written by the previous version looks like: onboarding done, no
        // tour_state line at all.
        write("onboarding_completed=true", "appearance=MIDNIGHT")

        val state = preferences().snapshot()
        assertEquals(true, state.onboardingCompleted)
        assertEquals(
            TourState.DONE,
            state.tourState,
            "somebody who has been using Vazie for weeks was ambushed by a first-run tour",
        )
    }

    @Test
    fun `a tour_state this build cannot read settles rather than repeats`() = runTest {
        // An unknown value falls back to DONE: a missed tour is recoverable, a tour on every launch is not.
        write("onboarding_completed=true", "tour_state=HALFWAY")

        assertEquals(TourState.DONE, preferences().snapshot().tourState)
    }

    private fun preferences(): AppPreferences = AppStorage.preferences(filesDir = directory)

    private fun write(vararg lines: String) {
        val file = File(File(directory, AppStorage.DIRECTORY), "preferences")
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
