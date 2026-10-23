package app.vazie.vpn.core.designsystem.tour

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** The things a guided tour is allowed to point at. */
enum class VazieTourTargetId {

    /** The control that opens and closes the tunnel. */
    CONNECT_CONTROL,

    /** The current-connection card on Home, which opens Connections. */
    CONNECTION_CARD,

    /** The gear on Home that opens Settings. There is no bottom bar any more. */
    SETTINGS_GEAR,

    /** The first configuration in the list. */
    CONFIGURATION_ROW,

    /** Settings rows the Settings tour walks through, top to bottom. */
    SETTINGS_APPEARANCE,
    SETTINGS_SPLIT_TUNNEL,
    SETTINGS_GUIDES,
    SETTINGS_REPORT_BUG,
}

/** Where each anchor currently is, and how to bring it on screen. */
@Stable
interface VazieTourTargets {

    fun place(id: VazieTourTargetId, bounds: Rect, bringIntoView: suspend () -> Unit)

    fun remove(id: VazieTourTargetId)

    /** `null` until this anchor has been laid out, or after it has left the composition. */
    fun bounds(id: VazieTourTargetId): Rect?

    /** Every anchor currently laid out, as a snapshot-readable set. */
    fun registered(): Set<VazieTourTargetId>

    /** Scroll an anchor into view, or do nothing if it is not present. */
    suspend fun bringIntoView(id: VazieTourTargetId)
}

/** The answer everywhere nobody is running a tour, which is almost everywhere. */
internal object NoTourTargets : VazieTourTargets {
    override fun place(id: VazieTourTargetId, bounds: Rect, bringIntoView: suspend () -> Unit) = Unit
    override fun remove(id: VazieTourTargetId) = Unit
    override fun bounds(id: VazieTourTargetId): Rect? = null
    override fun registered(): Set<VazieTourTargetId> = emptySet()
    override suspend fun bringIntoView(id: VazieTourTargetId) = Unit
}

/** `compositionLocalOf` rather than `staticCompositionLocalOf`, and the difference matters here. */
val LocalVazieTourTargets = compositionLocalOf<VazieTourTargets> { NoTourTargets }

/** Mark this composable as something a tour may point at. */
fun Modifier.vazieTourTarget(id: VazieTourTargetId): Modifier = composed {
    val targets = LocalVazieTourTargets.current
    val requester = remember { BringIntoViewRequester() }

    DisposableEffect(targets, id) {
        onDispose { targets.remove(id) }
    }

    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            targets.place(
                id = id,
                bounds = coordinates.boundsInRoot(),
                bringIntoView = { requester.bringIntoView() },
            )
        }
}

/** A registry the application shell can hold, and the only implementation that stores anything. */
@Stable
class VazieTourTargetRegistry : VazieTourTargets {

    private val placed = mutableStateMapOf<VazieTourTargetId, PlacedTarget>()

    override fun place(id: VazieTourTargetId, bounds: Rect, bringIntoView: suspend () -> Unit) {
        val existing = placed[id]
        // Compared before writing, because `onGloballyPositioned` fires on every layout pass and an
        // unconditional write would recompose the overlay on frames where nothing moved.
        if (existing?.bounds == bounds) return
        placed[id] = PlacedTarget(bounds = bounds, bringIntoView = bringIntoView)
    }

    override fun remove(id: VazieTourTargetId) {
        placed.remove(id)
    }

    override fun bounds(id: VazieTourTargetId): Rect? = placed[id]?.bounds

    override fun registered(): Set<VazieTourTargetId> = placed.keys.toSet()

    override suspend fun bringIntoView(id: VazieTourTargetId) {
        placed[id]?.bringIntoView?.invoke()
    }
}

private data class PlacedTarget(val bounds: Rect, val bringIntoView: suspend () -> Unit)

/** Holds a registry for the lifetime of the composition that owns it. */
@Composable
fun rememberVazieTourTargets(): VazieTourTargetRegistry = remember { VazieTourTargetRegistry() }
