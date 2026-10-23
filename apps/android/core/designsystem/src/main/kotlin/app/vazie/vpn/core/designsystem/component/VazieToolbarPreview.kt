package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

/** Three toolbars at once: with a back button, without one, and with an action. */
@VaziePreview
@Composable
private fun VazieToolbarPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Column {
            VazieToolbar(title = "Settings")
            VazieToolbar(title = "Privacy", onBack = {}, backContentDescription = "Back")
            VazieToolbar(
                title = "A configuration with a rather long name",
                onBack = {},
                backContentDescription = "Back",
                actions = {
                    VazieIconButton(onClick = {}, contentDescription = "Close") {
                        Icon(imageVector = VazieIcons.Close, contentDescription = null)
                    }
                },
            )
        }
    }
}
