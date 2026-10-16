package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A one-time code as a row of cells — one digit in each, the next one to fill highlighted. */
@Composable
fun VazieCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    length: Int = DefaultLength,
    enabled: Boolean = true,
    errorMessage: String? = null,
    /** Tells autofill this is a one-time code, so a keyboard or an autofill service that has seen the message
     * with it can offer it in one tap. */
    contentType: ContentType = ContentType.SmsOtpCode,
) {
    val colors = VazieTheme.colors
    val spacing = VazieTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isError = errorMessage != null

    Column(modifier = modifier) {
        Text(
            text = label,
            style = VazieTheme.typography.labelSmall,
            color = when {
                !enabled -> colors.textDisabled
                isError -> colors.errorText
                focused -> colors.primaryText
                else -> colors.textSecondary
            },
            modifier = Modifier.padding(bottom = spacing.xs),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    this.contentType = contentType
                    if (isError) error(errorMessage.orEmpty())
                },
            decorationBox = { innerTextField ->
                Box {
                    // The real field, invisible: it takes the input, the cells show it.
                    Box(modifier = Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                    Row(
                        modifier = Modifier
                            .widthIn(max = (CellMaxWidth + CellGap) * length)
                            .fillMaxWidth()
                            .clearAndSetSemantics { },
                        horizontalArrangement = Arrangement.spacedBy(CellGap),
                    ) {
                        repeat(length) { index ->
                            val digit = value.getOrNull(index)?.toString().orEmpty()
                            val active = enabled && focused && index == value.length.coerceAtMost(length - 1)
                            val border = when {
                                !enabled -> colors.border
                                isError -> colors.error
                                active -> colors.primary
                                else -> colors.border
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(CellHeight)
                                    .background(
                                        color = if (enabled) colors.surface else colors.fieldDisabledBg,
                                        shape = VazieTheme.shapes.md,
                                    )
                                    .border(
                                        width = if (active || isError) VazieTheme.borders.strong else VazieTheme.borders.regular,
                                        color = border,
                                        shape = VazieTheme.shapes.md,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = digit,
                                    style = VazieTheme.typography.monoTitle,
                                    color = if (enabled) colors.textPrimary else colors.textDisabled,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            },
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = VazieTheme.typography.caption,
                color = colors.errorText,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

private const val DefaultLength = 6
private val CellHeight = 56.dp
private val CellMaxWidth = 52.dp
private val CellGap = 8.dp
