package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VaziePreview
@Composable
private fun VazieProgressPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.md)) {
            VazieCircularProgress()
        }
        VazieLinearProgress(progress = 0.6f)
        VazieLinearProgress()
    }
}

// The snackbar is the one surface that inverts per appearance, so all three are worth a look.
@VaziePreview
@Composable
private fun VazieSnackbarPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieSnackbar(message = "Config imported")
        VazieSnackbar(message = "Config imported", actionLabel = "CONNECT", onAction = {})
    }
}

@VaziePreview
@Composable
private fun VazieMessageStatePreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieMessageState(
            title = "No configurations yet",
            description = "Import your first config to get started.",
            action = { VazieButton("Add", onClick = {}) },
        )
        VazieMessageState(
            title = "Connection timed out",
            description = "The server did not respond.",
            mark = {
                VazieMark(tone = VazieBadgeTone.Strong) {
                    Text(
                        text = "!",
                        style = VazieTheme.typography.title,
                        color = VazieTheme.colors.errorText,
                    )
                }
            },
            action = { VazieButton("Try again", onClick = {}, variant = VazieButtonVariant.Secondary) },
        )
    }
}
