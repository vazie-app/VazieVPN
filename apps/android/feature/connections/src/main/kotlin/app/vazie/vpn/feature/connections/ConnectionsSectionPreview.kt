package app.vazie.vpn.feature.connections

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VaziePreview
@Composable
private fun ConnectionsSectionPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        ConnectionsList(state = ConnectionsFixtures.populated, onAction = {})
    }
}

@Preview(name = "Empty · ru", widthDp = 360, heightDp = 400, locale = "ru")
@Composable
private fun ConnectionsSectionEmptyPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConnectionsList(state = ConnectionsFixtures.empty, onAction = {})
    }
}
