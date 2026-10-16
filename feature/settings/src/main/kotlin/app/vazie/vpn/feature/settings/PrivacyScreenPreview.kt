package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VazieScreenPreview
@Composable
private fun PrivacyScreenPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        PrivacyScreen(onBack = {})
    }
}

@VazieScreenPreview
@Composable
private fun PrivacyScreenDarkPreview() {
    VazieTheme(appearance = Appearance.NIGHT_INDIGO) {
        PrivacyScreen(onBack = {})
    }
}

// The longest lines in the app: Russian privacy copy at a large font scale.
@Preview(name = "ru · large font", widthDp = 360, heightDp = 900, fontScale = 1.6f, locale = "ru")
@Composable
private fun PrivacyScreenRussianPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        PrivacyScreen(onBack = {})
    }
}

