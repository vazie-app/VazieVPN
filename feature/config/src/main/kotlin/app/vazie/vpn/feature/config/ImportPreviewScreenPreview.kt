package app.vazie.vpn.feature.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** Every outcome step 2 can reach, in one strip: waiting, three valid, unsupported, two invalid. */
private class DetectionStates : PreviewParameterProvider<DetectionUiState> {
    override val values = AddConfigFixtures.allOutcomes.asSequence()
}

@VazieScreenPreview
@Composable
private fun ImportPreviewStatesPreview(
    @PreviewParameter(DetectionStates::class) detection: DetectionUiState,
) {
    VazieTheme(appearance = Appearance.MILK) {
        ImportPreviewScreen(
            state = AddConfigUiState(
                method = ImportMethodUi.PASTE,
                detection = detection,
                name = detection.suggestedName(),
            ),
            onAction = {},
        )
    }
}

