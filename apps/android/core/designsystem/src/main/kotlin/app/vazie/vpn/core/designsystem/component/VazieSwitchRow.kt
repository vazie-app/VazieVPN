package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A setting that is on or off: a title, an optional explanation, and a switch. The whole row is the control,
 * announced as a switch. */
@Composable
fun VazieSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    val colors = VazieTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VazieTheme.spacing.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs)) {
            Text(text = title, style = VazieTheme.typography.titleSmall, color = colors.textPrimary)
            if (body != null) {
                Text(text = body, style = VazieTheme.typography.bodySecondary, color = colors.textSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surface,
                uncheckedBorderColor = colors.textSecondary,
            ),
        )
    }
}
