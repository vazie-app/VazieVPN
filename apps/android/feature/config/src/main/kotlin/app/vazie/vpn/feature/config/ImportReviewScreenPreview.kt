package app.vazie.vpn.feature.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VazieScreenPreview
@Composable
private fun ImportReviewPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ImportReviewScreen(
            state = AddConfigUiState(
                method = ImportMethodUi.PASTE,
                detection = DetectionUiState.Recognized(AddConfigFixtures.reality),
                name = "Home relay",
            ),
            onAction = {},
        )
    }
}

/** Russian at the largest accessibility font scale. */
@Preview(widthDp = 360, heightDp = 760, fontScale = 2f, locale = "ru")
@Composable
private fun ImportReviewRussianLargeFontPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ImportReviewScreen(
            state = AddConfigUiState(
                method = ImportMethodUi.PASTE,
                detection = DetectionUiState.Recognized(AddConfigFixtures.reality),
                name = "Домашний узел",
            ),
            onAction = {},
        )
    }
}

