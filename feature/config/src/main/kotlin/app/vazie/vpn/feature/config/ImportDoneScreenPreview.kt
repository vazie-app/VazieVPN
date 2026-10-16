package app.vazie.vpn.feature.config

import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VazieScreenPreview
@Composable
private fun ImportDonePreview() {
    VazieTheme {
        ImportDoneScreen(
            state = AddConfigUiState(
                detection = DetectionUiState.Recognized(AddConfigFixtures.reality),
                name = "Home relay",
            ),
            onAction = {},
        )
    }
}
