package app.vazie.vpn.tour

import androidx.compose.ui.geometry.Rect
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetRegistry
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Sequencing, and the two things about it that are easy to get subtly wrong. */
class VazieTourControllerTest {

    /** Three steps: the connect control, the connection card and the settings gear. */
    @Test
    fun `the whole sequence is three steps and every one has a distinct target`() {
        val steps = vazieTourSequence()

        assertEquals(3, steps.size, "the first-run tour is three steps, not a museum audio guide")
        assertEquals(
            steps.size,
            steps.map { it.target }.toSet().size,
            "two steps pointing at the same control would spotlight it twice",
        )
    }

    @Test
    fun `next and back walk the steps and stop at the completion card`() {
        val controller = controller()

        assertEquals(1, (controller.phase as VazieTourPhase.Step).number)
        assertFalse(controller.canGoBack, "there is no step before the first")

        controller.next()
        val second = assertIs<VazieTourPhase.Step>(controller.phase)
        assertEquals(2, second.number)
        assertEquals(3, second.of)
        assertTrue(controller.canGoBack)

        repeat(2) { controller.next() }
        assertIs<VazieTourPhase.Completion>(controller.phase)

        // Not a dead end: the completion card can go back to the last step.
        controller.back()
        assertEquals(3, assertIs<VazieTourPhase.Step>(controller.phase).number)
    }

    @Test
    fun `the completion card carries no number of its own`() {
        val controller = controller()
        repeat(3) { controller.next() }

        // If this ever became a Step, the card would be announced as "4 of 3" or renumber
        // everything before it. It points at nothing and is not part of the count.
        assertIs<VazieTourPhase.Completion>(controller.phase)
    }

    @Test
    fun `skipping from the first step ends it once`() {
        var done = 0
        val controller = controller(onDone = { done++ })

        controller.finish()

        assertIs<VazieTourPhase.Finished>(controller.phase)
        assertEquals(1, done)
    }

    @Test
    fun `finishing twice records it once`() {
        var done = 0
        val controller = controller(onDone = { done++ })

        controller.finish()
        controller.finish()

        assertEquals(1, done, "the preference would have been written twice")
    }

    @Test
    fun `a missing target is skipped and the numbering stays contiguous`() {
        val controller = controller(
            present = VazieTourTargetId.entries.toSet() - VazieTourTargetId.CONNECTION_CARD,
        )

        val first = assertIs<VazieTourPhase.Step>(controller.phase)
        assertEquals(2, first.of, "the denominator counts what can actually be shown")
        assertEquals(VazieTourTargetId.CONNECT_CONTROL, first.step.target)

        controller.next()
        val second = assertIs<VazieTourPhase.Step>(controller.phase)
        assertEquals(2, second.number, "a skipped step left a hole in the numbering")
        assertEquals(VazieTourTargetId.SETTINGS_GEAR, second.step.target)
    }

    @Test
    fun `a tour with nothing to point at ends instead of showing a completion card`() {
        var done = 0
        val controller = controller(present = emptySet(), onDone = { done++ })

        assertIs<VazieTourPhase.Finished>(controller.phase)
        assertEquals(0, done, "nothing was shown, so nothing has been decided yet")
    }

    /** A registry holding exactly the targets a test wants present. */
    private fun controller(
        present: Set<VazieTourTargetId> = VazieTourTargetId.entries.toSet(),
        onDone: () -> Unit = {},
    ): VazieTourController {
        val registry = VazieTourTargetRegistry()
        present.forEach { registry.place(it, Rect(0f, 0f, 10f, 10f)) {} }
        return VazieTourController(
            sequence = vazieTourSequence(),
            targets = registry,
            onDone = onDone,
            position = TourPosition(null, false),
        )
    }
}
