package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A consent checkbox with a link; the link opens [url] and does not toggle the box. [text] holds `%1$s`. */
@Composable
fun VazieConsentCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    linkText: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val colors = VazieTheme.colors
    val marker = "%1\$s"
    val at = text.indexOf(marker)
    val sentence = buildAnnotatedString {
        if (at < 0) {
            append(text)
            append(' ')
            appendLink(linkText, url, colors.primaryText)
        } else {
            append(text.substring(0, at))
            appendLink(linkText, url, colors.primaryText)
            append(text.substring(at + marker.length))
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VazieTheme.spacing.minTouchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                uncheckedColor = colors.textSecondary,
                checkmarkColor = colors.onPrimary,
            ),
        )
        Text(
            text = sentence,
            style = VazieTheme.typography.bodySecondary,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendLink(
    text: String,
    url: String,
    color: androidx.compose.ui.graphics.Color,
) {
    withLink(
        LinkAnnotation.Url(
            url = url,
            styles = TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
        ),
    ) { append(text) }
}
