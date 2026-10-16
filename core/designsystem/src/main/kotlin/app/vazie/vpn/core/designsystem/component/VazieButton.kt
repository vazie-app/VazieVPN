package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.R
import app.vazie.vpn.core.designsystem.theme.VazieColors
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import androidx.compose.ui.text.style.TextOverflow

enum class VazieButtonVariant { Primary, Secondary, Destructive, Text }

@Immutable
internal data class ButtonPalette(
    val container: Color,
    val content: Color,
    val border: Color?,
)

/** Pure so the variant-to-token mapping is testable and cannot drift per call site. */
internal fun buttonPalette(
    variant: VazieButtonVariant,
    colors: VazieColors,
    enabled: Boolean,
): ButtonPalette = when {
    !enabled && variant.isFilled -> ButtonPalette(colors.disabled, colors.textDisabled, null)
    !enabled -> ButtonPalette(Color.Transparent, colors.textDisabled, null)
    else -> when (variant) {
        // "Маршрут": cream is the one colour a person acts with; the secondary action is a calm
        // raised surface with no outline; a destructive one is soft clay rather than an alarm.
        VazieButtonVariant.Primary -> ButtonPalette(colors.action, colors.onAction, null)
        VazieButtonVariant.Destructive -> ButtonPalette(colors.errorContainer, colors.onErrorContainer, null)
        VazieButtonVariant.Secondary -> ButtonPalette(colors.surfaceMuted, colors.textPrimary, null)
        VazieButtonVariant.Text -> ButtonPalette(Color.Transparent, colors.primaryText, null)
    }
}

internal val VazieButtonVariant.isFilled: Boolean
    get() = this != VazieButtonVariant.Text

private val ButtonMinHeight = 52.dp
private val SpinnerSize = 16.dp
private val SpinnerStroke = 2.dp

/** Wraps the Material button so the ripple, focus handling and button semantics come from the platform, while
 * every colour, shape and text style comes from Vazie. */
@Composable
fun VazieButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VazieButtonVariant = VazieButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    // Loading keeps the enabled palette, so "working" never looks like "disabled".
    val palette = buttonPalette(variant, VazieTheme.colors, enabled)
    val spacing = VazieTheme.spacing
    val busy = stringResource(R.string.vazie_a11y_busy)

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .defaultMinSize(minHeight = ButtonMinHeight)
            .then(if (loading) Modifier.semantics { stateDescription = busy } else Modifier),
        shape = VazieTheme.shapes.md,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.container,
            contentColor = palette.content,
            disabledContainerColor = palette.container,
            disabledContentColor = palette.content,
        ),
        border = palette.border?.let { BorderStroke(VazieTheme.borders.regular, it) },
        contentPadding = PaddingValues(
            horizontal = if (variant == VazieButtonVariant.Text) spacing.md else spacing.xl,
            vertical = spacing.sm,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            leadingContent?.invoke()
            // One line on every button, everywhere: a label that wraps turns "Далее" into "Дал /
            // ее".
            Text(
                text = text,
                style = VazieTheme.typography.button,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SpinnerSize),
                    color = palette.content,
                    strokeWidth = SpinnerStroke,
                )
            }
        }
    }
}
