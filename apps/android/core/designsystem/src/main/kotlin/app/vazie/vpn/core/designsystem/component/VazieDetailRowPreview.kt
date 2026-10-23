package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme

@VaziePreview
@Composable
private fun VazieDetailRowPreview() {
    VaziePreviewTheme {
        VazieListCard {
            VazieDetailRow(label = "Protocol", value = "VLESS · Reality")
            VazieDivider()
            VazieDetailRow(label = "Server", value = "relay.example.net")
            VazieDivider()
            VazieDetailRow(label = "Last used", value = "2 hours ago", technical = false)
        }
    }
}

@VaziePreview
@Composable
private fun VazieDetailRowTrailingPreview() {
    VaziePreviewTheme {
        VazieListCard {
            VazieDetailRow(
                label = "Server",
                value = "relay.example.net",
                trailingContent = { VazieStatusChip(label = "Reachable", tone = VazieStatusTone.Success) },
            )
        }
    }
}

