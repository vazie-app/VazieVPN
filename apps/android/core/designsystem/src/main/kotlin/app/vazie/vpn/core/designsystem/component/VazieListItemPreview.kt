package app.vazie.vpn.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
private fun VazieListItemPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieListItem(title = "Settings row", onClick = {})
        VazieListItem(
            title = "Home relay",
            subtitle = "VLESS · connected",
            leadingContent = { VazieMark(tone = VazieBadgeTone.Strong) { MarkLabel("VL") } },
            trailingContent = { VazieStatusDot(VazieStatusTone.Success) },
            onClick = {},
        )
        VazieListItem(
            title = "Office tunnel",
            subtitle = "relay.example.net",
            trailingContent = { Icon(VazieIcons.ChevronRight, contentDescription = null) },
            onClick = {},
        )
        VazieListItem(
            title = "Home server",
            subtitle = "Requires a subscription",
            leadingContent = { VazieMark { MarkLabel("WG") } },
            onClick = {},
            enabled = false,
        )
    }
}

// Rows are the component the density axis is most visible on.
@VaziePreview
@Composable
private fun VazieListItemDensityPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieListItem(
            title = "Home relay",
            subtitle = "Used 2 hours ago",
            trailingContent = { VazieStatusChip(label = "Connected", tone = VazieStatusTone.Success) },
            onClick = {},
        )
    }
}

@Composable
private fun MarkLabel(text: String) {
    Text(
        text = text,
        style = VazieTheme.typography.monoSmall,
        color = VazieTheme.colors.engineBadgeFg,
    )
}
