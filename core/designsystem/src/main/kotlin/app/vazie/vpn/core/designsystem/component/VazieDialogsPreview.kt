package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

// VazieBottomSheet has no preview: it needs a window.

@VaziePreview
@Composable
private fun VazieDialogPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieDialog(
            title = "Remove this config?",
            text = "It will be deleted from this device. This cannot be undone.",
            onDismissRequest = {},
            confirmButton = {
                VazieButton("Remove", onClick = {}, variant = VazieButtonVariant.Destructive)
            },
            dismissButton = {
                VazieButton("Cancel", onClick = {}, variant = VazieButtonVariant.Text)
            },
        )
    }
}
