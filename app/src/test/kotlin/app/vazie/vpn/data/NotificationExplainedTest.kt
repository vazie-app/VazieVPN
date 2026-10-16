package app.vazie.vpn.data

import app.vazie.vpn.core.model.VazieGuideId
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The one fact behind the notification explanation, and the one thing it must never grow into. */
class NotificationExplainedTest {

    private val directory: File = Files.createTempDirectory("vazie-notification").toFile()

    @Test
    fun `nothing has been explained on a fresh install`() = runTest {
        assertFalse(preferences().snapshot().notificationExplained)
    }

    @Test
    fun `explaining survives a restart, so it is never asked twice`() = runTest {
        preferences().setNotificationExplained(true)

        assertTrue(
            preferences().snapshot().notificationExplained,
            "a person who answered once would be asked again on the next launch",
        )
    }

    @Test
    fun `acknowledging the guide does not count as having explained`() = runTest {
        val store = preferences()
        store.acknowledgeGuide(VazieGuideId.NOTIFICATION)

        val state = preferences().snapshot()
        assertTrue(VazieGuideId.NOTIFICATION in state.acknowledgedGuides)
        assertFalse(
            state.notificationExplained,
            "reading the guide is not answering a permission dialog nobody showed",
        )
    }

    @Test
    fun `explaining does not acknowledge the guide`() = runTest {
        preferences().setNotificationExplained(true)

        val state = preferences().snapshot()
        assertTrue(state.notificationExplained)
        assertFalse(
            VazieGuideId.NOTIFICATION in state.acknowledgedGuides,
            "answering the prompt is not a request to stop being told the guide exists",
        )
    }

    private fun preferences(): AppPreferences = AppStorage.preferences(filesDir = directory)
}
