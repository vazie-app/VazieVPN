package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VaziePreview
@Composable
private fun VazieIconButtonPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm)) {
            VazieIconButton(onClick = {}, contentDescription = "Add") {
                Icon(VazieIcons.Plus, contentDescription = null)
            }
            VazieIconButton(onClick = {}, contentDescription = "Close") {
                Icon(VazieIcons.Close, contentDescription = null)
            }
            VazieIconButton(onClick = {}, contentDescription = "Add", enabled = false) {
                Icon(VazieIcons.Plus, contentDescription = null)
            }
        }
    }
}
