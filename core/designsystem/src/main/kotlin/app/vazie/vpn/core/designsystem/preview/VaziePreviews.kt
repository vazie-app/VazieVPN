package app.vazie.vpn.core.designsystem.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieStatusChip
import app.vazie.vpn.core.designsystem.component.VazieStatusTone

/** Previews of the kit as a whole: a narrow screen and a large font scale. */
@Preview(name = "Narrow", widthDp = 280)
@Preview(name = "Large font", widthDp = 360, fontScale = 1.5f)
@Preview(name = "Largest font", widthDp = 360, fontScale = 2.0f)
@Composable
private fun VazieResponsivePreview() {
    VaziePreviewTheme {
        VazieSectionHeader(title = "My configs")
        VazieListCard {
            VazieListItem(
                title = "Home relay",
                subtitle = "VLESS · connected",
                trailingContent = { VazieStatusChip(label = "Connected", tone = VazieStatusTone.Success) },
                onClick = {},
            )
        }
        VazieButton("Connect", onClick = {}, modifier = Modifier.fillMaxWidth())
        VazieButton(
            text = "Import config",
            onClick = {},
            variant = VazieButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

