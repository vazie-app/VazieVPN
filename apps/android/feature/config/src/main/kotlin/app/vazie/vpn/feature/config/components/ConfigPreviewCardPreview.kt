package app.vazie.vpn.feature.config.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.feature.config.AddConfigFixtures
import app.vazie.vpn.feature.config.R

@VaziePreview
@Composable
private fun ConfigPreviewCardPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        ConfigPreviewCard(preview = AddConfigFixtures.reality, name = "Home relay")
    }
}
