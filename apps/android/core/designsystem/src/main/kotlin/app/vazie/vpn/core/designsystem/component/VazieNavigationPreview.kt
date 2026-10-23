package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

@VaziePreview
@Composable
private fun VazieBottomBarPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieBottomBar {
            VazieBottomBarItem(label = "HOME", selected = true, onClick = {})
            VazieBottomBarItem(label = "CONNECTIONS", selected = false, onClick = {})
            VazieBottomBarItem(label = "SETTINGS", selected = false, onClick = {})
        }
    }
}

