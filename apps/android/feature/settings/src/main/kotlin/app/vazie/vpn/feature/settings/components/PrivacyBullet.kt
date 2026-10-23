package app.vazie.vpn.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.settings.R

/** One line of the privacy list: a mark and a sentence. */
@Composable
internal fun PrivacyBullet(
    text: String,
    excluded: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VazieTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm),
    ) {
        val description = stringResource(
            if (excluded) R.string.settings_privacy_never else R.string.settings_privacy_always,
        )
        Icon(
            imageVector = if (excluded) VazieIcons.Close else VazieIcons.Check,
            contentDescription = null,
            tint = if (excluded) colors.errorText else colors.successText,
            modifier = Modifier
                .size(VazieTheme.spacing.lg)
                .semantics { this.contentDescription = description },
        )
        Text(
            text = text,
            style = VazieTheme.typography.body,
            color = colors.textPrimary,
        )
    }
}
