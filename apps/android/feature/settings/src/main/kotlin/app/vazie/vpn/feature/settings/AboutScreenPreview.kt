package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance

@VazieScreenPreview
@Composable
private fun AboutScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        AboutScreen(
            state = AboutUiState(about = PREVIEW_ABOUT),
            onAction = {},
        )
    }
}

// Every hint dismissed: the one state where the "show the tips again" card exists.
@VazieScreenPreview
@Composable
private fun AboutScreenNoHintsPreview(
    @PreviewParameter(Appearances::class) appearance: Appearance,
) {
    VaziePreviewTheme(appearance) {
        AboutScreen(
            state = AboutUiState(about = PREVIEW_ABOUT),
            onAction = {},
        )
    }
}

private val PREVIEW_ABOUT = AboutUi(
    versionName = "0.4.0",
    versionCode = 40,
    buildType = "debug",
)

