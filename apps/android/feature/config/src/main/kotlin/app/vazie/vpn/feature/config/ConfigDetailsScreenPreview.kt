package app.vazie.vpn.feature.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VazieScreenPreview
@Composable
private fun ConfigDetailsObscuredPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        ConfigDetailsScreen(state = ConfigDetailsFixtures.sample, onAction = {})
    }
}

// Revealed: the state that has to look deliberate rather than accidental.
@VazieScreenPreview
@Composable
private fun ConfigDetailsRevealedPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConfigDetailsScreen(state = ConfigDetailsFixtures.revealed, onAction = {})
    }
}

// A transport whose detail is a path, and no REALITY rows: the other shape the card has to hold.
@VazieScreenPreview
@Composable
private fun ConfigDetailsWebSocketPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConfigDetailsScreen(state = ConfigDetailsFixtures.webSocket, onAction = {})
    }
}

// The profile was deleted from another surface while this screen was open.
@VazieScreenPreview
@Composable
private fun ConfigDetailsMissingPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConfigDetailsScreen(state = ConfigDetailsFixtures.missing, onAction = {})
    }
}

