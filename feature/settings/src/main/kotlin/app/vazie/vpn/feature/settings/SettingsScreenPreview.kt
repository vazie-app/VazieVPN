package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieAppIcon

private fun state(
    appearance: Appearance = Appearance.Default,
) = SettingsUiState(
    appearance = appearance,
    appIcon = VazieAppIcon.ORBIT,
)

// The appearance axis is the screen's own content here: each render shows the picker in the palette it
// is offering.
@VazieScreenPreview
@Composable
private fun SettingsScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        SettingsScreen(state = state(appearance), onAction = {})
    }
}

@Preview(name = "ru · large font", widthDp = 360, heightDp = 900, fontScale = 1.5f, locale = "ru")
@Composable
private fun SettingsScreenRussianPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        SettingsScreen(state = state(), onAction = {})
    }
}
