package app.vazie.vpn.feature.connections

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VazieScreenPreview
@Composable
private fun ConnectionsPopulatedPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        ConnectionsScreen(state = ConnectionsFixtures.populated, onAction = {})
    }
}

@VazieScreenPreview
@Composable
private fun ConnectionsEmptyPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConnectionsScreen(state = ConnectionsFixtures.empty, onAction = {})
    }
}

@Preview(name = "Large font · ru", widthDp = 360, heightDp = 800, fontScale = 1.6f, locale = "ru")
@Composable
private fun ConnectionsRussianPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        ConnectionsScreen(state = ConnectionsFixtures.populated, onAction = {})
    }
}

