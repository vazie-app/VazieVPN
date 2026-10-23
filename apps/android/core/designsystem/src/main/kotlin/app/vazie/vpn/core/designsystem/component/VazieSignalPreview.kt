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
private fun VazieSignalPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Column {
            VazieSignal(bars = 4, label = "48 ms")
            VazieSignal(bars = 3, label = "180 ms")
            VazieSignal(bars = 2, label = "420 ms")
            VazieSignal(bars = 1, label = "910 ms")
            VazieSignal(bars = 0, label = "no answer")
            VazieListItem(
                title = "Home server",
                subtitle = "VLESS",
                subtitleAccessory = { VazieSignal(bars = 4, label = "48 ms") },
                trailingContent = { VazieStatusChip(label = "Ready", tone = VazieStatusTone.Neutral) },
                onClick = {},
            )
        }
    }
}
