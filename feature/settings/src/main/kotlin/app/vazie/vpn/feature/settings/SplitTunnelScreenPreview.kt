package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.SplitTunnel
import kotlinx.collections.immutable.persistentListOf

private val apps = persistentListOf(
    LaunchableApp("com.example.bank", "Bank"),
    LaunchableApp("com.example.maps", "Maps"),
    LaunchableApp("com.example.music", "Music"),
)

@VazieScreenPreview
@Composable
private fun SplitTunnelScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        SplitTunnelScreen(
            state = SplitTunnelUiState(SplitTunnel(SplitTunnel.Mode.EXCLUDE, setOf("com.example.bank")), apps),
            onAction = {},
        )
    }
}

@Preview(name = "Off · ru · large font", widthDp = 320, heightDp = 760, fontScale = 1.5f, locale = "ru")
@Composable
private fun SplitTunnelScreenOffPreview() {
    VazieTheme {
        SplitTunnelScreen(state = SplitTunnelUiState(SplitTunnel(), apps), onAction = {})
    }
}
