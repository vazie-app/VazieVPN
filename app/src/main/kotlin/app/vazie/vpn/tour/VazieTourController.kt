package app.vazie.vpn.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import app.vazie.vpn.core.designsystem.tour.VazieTourTargets

/** Where the tour is, and the only three things that move it. */
@Stable
class VazieTourController internal constructor(
    private val sequence: List<VazieTourStep>,
    private val targets: VazieTourTargets,
    private val onDone: () -> Unit,
    private val position: TourPosition,
) {

    private var finished by mutableStateOf(false)

    /** The steps whose targets are on screen right now, in sequence order. */
    val presentable: List<VazieTourStep>
        get() = sequence.filter { targets.bounds(it.target) != null }

    val phase: VazieTourPhase
        get() {
            val steps = presentable
            if (finished || steps.isEmpty()) return VazieTourPhase.Finished
            if (position.completed) return VazieTourPhase.Completion
            val index = indexIn(steps)
            return VazieTourPhase.Step(steps[index], number = index + 1, of = steps.size)
        }

    /** `true` on every step but the first; the completion card returns to the last step. */
    val canGoBack: Boolean
        get() {
            val steps = presentable
            if (steps.isEmpty()) return false
            return if (position.completed) true else indexIn(steps) > 0
        }

    fun next() {
        val steps = presentable
        if (steps.isEmpty() || position.completed) return
        val index = indexIn(steps)
        if (index == steps.lastIndex) position.complete() else position.moveTo(steps[index + 1].target)
    }

    fun back() {
        val steps = presentable
        if (steps.isEmpty()) return
        if (position.completed) {
            position.moveTo(steps.last().target)
            return
        }
        val index = indexIn(steps)
        if (index > 0) position.moveTo(steps[index - 1].target)
    }

    /** Where the reader is in the live set, as an index into it. */
    private fun indexIn(steps: List<VazieTourStep>): Int {
        val current = position.current ?: return 0
        return steps.indexOfFirst { it.target == current }.coerceAtLeast(0)
    }

    fun finish() {
        if (finished) return
        finished = true
        onDone()
    }
}

/** Where in the sequence the reader is, in a form that survives a configuration change. */
@Stable
class TourPosition internal constructor(current: String?, completed: Boolean) {

    var currentName by mutableStateOf(current)
        private set
    var completed by mutableStateOf(completed)
        private set

    val current: VazieTourTargetId?
        get() = currentName?.let { name ->
            VazieTourTargetId.entries.firstOrNull { it.name == name } ?: RENAMED_TARGETS[name]
        }

    fun moveTo(id: VazieTourTargetId) {
        currentName = id.name
        completed = false
    }

    fun complete() {
        completed = true
    }
}

/** Target names saved before the "Маршрут" redesign, mapped to the current ones. */
private val RENAMED_TARGETS: Map<String, VazieTourTargetId> = mapOf(
    "CONNECT_ACTION" to VazieTourTargetId.CONNECT_CONTROL,
    "CURRENT_CONFIGURATION" to VazieTourTargetId.CONNECTION_CARD,
    "SETTINGS_TAB" to VazieTourTargetId.SETTINGS_GEAR,
)

/** What the overlay should be drawing right now. */
sealed interface VazieTourPhase {

    /** A numbered step with a target to point at. */
    data class Step(val step: VazieTourStep, val number: Int, val of: Int) : VazieTourPhase

    /** The card after the last step. */
    data object Completion : VazieTourPhase

    data object Finished : VazieTourPhase
}

/** A controller whose position survives a rotation and whose step list does not need to. */
@Composable
internal fun rememberVazieTourController(
    targets: VazieTourTargets,
    sequence: List<VazieTourStep>,
    onDone: () -> Unit,
): VazieTourController {
    val position = rememberSaveable(saver = TourPositionSaver) { TourPosition(null, false) }
    return remember(targets, sequence) {
        VazieTourController(
            sequence = sequence,
            targets = targets,
            onDone = onDone,
            position = position,
        )
    }
}

private val TourPositionSaver = androidx.compose.runtime.saveable.listSaver<TourPosition, Any?>(
    save = { listOf(it.currentName, it.completed) },
    restore = { TourPosition(it[0] as String?, it[1] as Boolean) },
)
