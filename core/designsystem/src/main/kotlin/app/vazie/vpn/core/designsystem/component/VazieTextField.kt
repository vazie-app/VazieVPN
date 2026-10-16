package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieSpacing
import app.vazie.vpn.core.designsystem.theme.VazieTheme

private val FieldMinHeight = 48.dp

/** Built on `BasicTextField`, not the Material field; an error thickens the border and is attached to the
 * field's semantics. */
@Composable
fun VazieTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    errorMessage: String? = null,
    /** False when the screen shows [errorMessage] itself; the border and the semantics still carry it. */
    showErrorMessage: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = VazieTheme.typography.body,
    trailingContent: @Composable (() -> Unit)? = null,
    /** What autofill may offer here — an email address, a password. */
    contentType: ContentType? = null,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isError = errorMessage != null

    val borderColor = when {
        !enabled -> colors.border
        isError -> colors.error
        focused -> colors.primary
        else -> colors.border
    }
    val borderWidth = if (enabled && (isError || focused)) {
        VazieTheme.borders.strong
    } else {
        VazieTheme.borders.regular
    }
    val labelColor = when {
        !enabled -> colors.textDisabled
        isError -> colors.errorText
        focused -> colors.primaryText
        else -> colors.textSecondary
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = VazieTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(bottom = spacing.xxs),
            )
        }
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = colors.primary,
                backgroundColor = colors.primary.copy(alpha = SelectionAlpha),
            ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = textStyle.copy(
                    color = if (enabled) colors.textPrimary else colors.textDisabled,
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isError) Modifier.semantics { error(errorMessage) } else Modifier,
                    )
                    .then(
                        if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier,
                    ),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = FieldMinHeight)
                            .background(
                                color = if (enabled) colors.surface else colors.fieldDisabledBg,
                                shape = VazieTheme.shapes.md,
                            )
                            .border(borderWidth, borderColor, VazieTheme.shapes.md)
                            .padding(horizontal = spacing.sm, vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = textStyle,
                                    color = colors.textDisabled,
                                )
                            }
                            innerTextField()
                        }
                        trailingContent?.invoke()
                    }
                },
            )
        }
        if (errorMessage != null && showErrorMessage) {
            Text(
                text = errorMessage,
                style = VazieTheme.typography.caption,
                color = colors.errorText,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}

private const val SelectionAlpha = 0.3f

/** The compact text action the design puts inside fields and beside rows — "SHOW", "DETAILS", "ADD". */
@Composable
fun VazieFieldAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = VazieTheme.colors.primaryText,
) {
    val colors = VazieTheme.colors
    Box(
        modifier = modifier
            .clip(VazieTheme.shapes.sm)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(
                minWidth = VazieTheme.spacing.minTouchTarget,
                minHeight = VazieTheme.spacing.minTouchTarget,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VazieTheme.typography.labelSmall,
            color = if (enabled) color else colors.textDisabled,
            modifier = Modifier.padding(
                horizontal = VazieTheme.spacing.xs,
                vertical = VazieTheme.spacing.xxs,
            ),
        )
    }
}
