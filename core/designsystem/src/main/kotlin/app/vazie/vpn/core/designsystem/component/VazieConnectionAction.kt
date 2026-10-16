package app.vazie.vpn.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieColors
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** What the primary connection action offers. The caller chooses the kind from its own connection state and
 * supplies the words; this only decides how the action looks. */
enum class VazieConnectionActionKind {
    /** Cream — the one thing to do. */
    Connect,

    /** A connection is being made; the action cancels it. */
    Connecting,

    /** Connected; the action disconnects. */
    Disconnect,

    /** The connection is being restored; the action cancels it. */
    Reconnecting,

    /** It failed; the action tries again. Cream, like Connect. */
    Retry,
}

/** The primary connection button: cream only when there is something to start (Connect, Retry). */
@Composable
fun VazieConnectionAction(
    kind: VazieConnectionActionKind,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
) {
    val colors = VazieTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val palette = connectionActionPalette(kind, colors, enabled, pressed)
    // Colours glide and words cross-fade between states; instant with "remove animations".
    val still = VazieTheme.reduceMotion
    val container by animateColorAsState(palette.container, colorSpec(still, pressed), label = "action-container")
    val content by animateColorAsState(palette.content, colorSpec(still, pressed), label = "action-content")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ActionHeight)
            .then(if (stateDescription != null) Modifier.semantics { this.stateDescription = stateDescription } else Modifier),
        shape = RoundedCornerShape(ActionRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        contentPadding = PaddingValues(horizontal = VazieTheme.spacing.lg, vertical = VazieTheme.spacing.sm),
    ) {
        AnimatedContent(
            targetState = ActionFace(kind.icon, text),
            transitionSpec = {
                val change = if (still) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(FaceMillis)) togetherWith fadeOut(tween(FaceMillis))
                }
                change.using(SizeTransform(clip = false) { _, _ -> tween(if (still) 0 else FaceMillis) })
            },
            contentAlignment = Alignment.Center,
            label = "action-face",
        ) { face ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm),
            ) {
                Icon(imageVector = face.icon, contentDescription = null, modifier = Modifier.size(IconSize))
                Text(text = face.text, style = VazieTheme.typography.button, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
    }
}

/** What the button shows; a change of either cross-fades. */
@Immutable
private data class ActionFace(val icon: ImageVector, val text: String)

/** A press answers at once; a change of state glides. */
private fun colorSpec(still: Boolean, pressed: Boolean): AnimationSpec<Color> =
    if (still || pressed) snap() else tween(ColorMillis)

@Immutable
internal data class ConnectionActionPalette(val container: Color, val content: Color)

internal fun connectionActionPalette(
    kind: VazieConnectionActionKind,
    colors: VazieColors,
    enabled: Boolean,
    pressed: Boolean = false,
): ConnectionActionPalette = when {
    !enabled -> ConnectionActionPalette(colors.fieldDisabledBg, colors.textDisabled)
    kind.isStart -> ConnectionActionPalette(if (pressed) colors.actionPressed else colors.action, colors.onAction)
    else -> ConnectionActionPalette(if (pressed) colors.elevated else colors.surfaceMuted, colors.textPrimary)
}

internal val VazieConnectionActionKind.isStart: Boolean
    get() = this == VazieConnectionActionKind.Connect || this == VazieConnectionActionKind.Retry

private val VazieConnectionActionKind.icon: ImageVector
    get() = when (this) {
        VazieConnectionActionKind.Connect, VazieConnectionActionKind.Disconnect -> VazieIcons.Power
        VazieConnectionActionKind.Connecting, VazieConnectionActionKind.Reconnecting -> VazieIcons.Close
        VazieConnectionActionKind.Retry -> VazieIcons.Retry
    }

private val ActionHeight = 60.dp
private val ActionRadius = 20.dp
private val IconSize = 20.dp
private const val ColorMillis = 300
private const val FaceMillis = 220
