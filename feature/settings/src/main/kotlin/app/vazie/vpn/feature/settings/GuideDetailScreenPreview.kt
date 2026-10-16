package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.VazieGuideId

/** A setup guide on a device that will answer, and the usage guide that never has a button. */
@VaziePreview
@Composable
private fun GuideDetailScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        GuideDetailScreen(
            state = GuideDetailUiState(
                id = VazieGuideId.QUICK_SETTINGS,
                systemIntegration = SystemIntegrationUi(canAddQuickSettingsTile = true),
            ),
            onAction = {},
        )
    }
}

@VaziePreview
@Composable
private fun GuideDetailUsagePreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        GuideDetailScreen(
            state = GuideDetailUiState(id = VazieGuideId.LAUNCHER_SHORTCUTS),
            onAction = {},
        )
    }
}
