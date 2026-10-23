package app.vazie.vpn.core.designsystem.component

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
private fun VazieCardPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieSurfaceStyle.entries.forEach { style ->
            VazieCard(style = style) {
                Text(
                    text = style.name,
                    style = VazieTheme.typography.titleSmall,
                    color = VazieTheme.colors.textPrimary,
                )
            }
        }
    }
}

@VaziePreview
@Composable
private fun VazieListCardPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieListCard {
            VazieListItem(title = "Home relay", subtitle = "Used 2 hours ago")
            VazieDivider()
            VazieListItem(title = "Office tunnel", subtitle = "relay.example.net")
        }
    }
}

@VaziePreview
@Composable
private fun VazieSectionHeaderPreview() {
    VaziePreviewTheme {
        VazieSectionHeader(title = "My configs")
        VazieSectionHeader(
            title = "Recent",
            action = { VazieFieldAction(label = "SEE ALL", onClick = {}) },
        )
    }
}

@VaziePreview
@Composable
private fun VazieGroupLabelPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieSectionHeader(title = "Theme")
        VazieGroupLabel(title = "Light")
        VazieGroupLabel(title = "Dark")
    }
}
