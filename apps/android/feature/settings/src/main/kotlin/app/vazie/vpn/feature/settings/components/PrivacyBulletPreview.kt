package app.vazie.vpn.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.settings.R

@VaziePreview
@Composable
private fun PrivacyBulletPreview() {
    VaziePreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            PrivacyBullet(text = stringResource(R.string.privacy_never_browsing), excluded = true)
            PrivacyBullet(text = stringResource(R.string.privacy_never_analytics), excluded = false)
        }
    }
}
