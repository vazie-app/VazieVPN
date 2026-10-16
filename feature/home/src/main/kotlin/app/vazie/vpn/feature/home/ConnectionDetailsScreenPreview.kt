package app.vazie.vpn.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VazieScreenPreview
@Composable
private fun ConnectionDetailsStandardPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConnectionDetailsScreen(session = HomeFixtures.session, onBack = {})
    }
}

@VazieScreenPreview
@Composable
private fun ConnectionDetailsEmptyPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConnectionDetailsScreen(session = null, onBack = {})
    }
}

