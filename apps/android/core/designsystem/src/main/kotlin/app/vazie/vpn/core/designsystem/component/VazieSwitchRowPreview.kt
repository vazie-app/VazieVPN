package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

@VaziePreview
@Composable
private fun VazieSwitchRowPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Column {
            VazieSwitchRow(title = "Crash reports", checked = true, onCheckedChange = {}, body = "Sent only with consent.")
            VazieSwitchRow(title = "Crash reports", checked = false, onCheckedChange = {})
        }
    }
}
