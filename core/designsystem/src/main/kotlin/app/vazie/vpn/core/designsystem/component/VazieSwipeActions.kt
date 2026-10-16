package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** One of the two things a swipe on a row can do. */
@androidx.compose.runtime.Immutable
data class VazieSwipeAction(
    val icon: ImageVector,
    /** Spoken, and used as the accessible action's name. Never drawn — the icon is the drawing. */
    val label: String,
    val onAction: () -> Unit,
)

/** A row with an action behind each edge, revealed by dragging it. */
@Composable
fun VazieSwipeableRow(
    start: VazieSwipeAction?,
    end: VazieSwipeAction,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val colors = VazieTheme.colors
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { width -> width * SwipeThreshold },
    )

    // Neither action removes the row itself, so it always returns to rest and the state resets.
    LaunchedEffect(state, start, end) {
        snapshotFlow { state.currentValue }.collect { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> start?.onAction()
                SwipeToDismissBoxValue.EndToStart -> end.onAction()
                SwipeToDismissBoxValue.Settled -> return@collect
            }
            state.reset()
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = start != null,
        backgroundContent = {
            val revealing = state.dismissDirection

            // Nothing revealed, nothing drawn.
            if (revealing == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox

            val reveals = revealing == SwipeToDismissBoxValue.EndToStart
            val background = if (reveals) colors.errorContainer else colors.accentContainer
            val tint = if (reveals) colors.onErrorContainer else colors.onAccentContainer
            val action = (if (reveals) end else start) ?: return@SwipeToDismissBox
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
                    .padding(horizontal = VazieTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (reveals) Arrangement.End else Arrangement.Start,
            ) {
                Icon(
                    imageVector = action.icon,
                    // The row itself announces the actions through `customActions`; a description
                    // here would be a second announcement of the same thing, from the decoration.
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(VazieTheme.spacing.xl),
                )
            }
        },
        // The actions are handed to the row as a modifier rather than set on the box around it.
        content = {
            content(
                Modifier.semantics {
                    customActions = listOfNotNull(
                        start?.let { CustomAccessibilityAction(it.label) { it.onAction(); true } },
                        CustomAccessibilityAction(end.label) { end.onAction(); true },
                    )
                },
            )
        },
    )
}

/** How far a row travels before letting go performs the action. */
private const val SwipeThreshold = 0.33f
