package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

@VaziePreview
@Composable
private fun VazieButtonPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieButton("Connect", onClick = {}, modifier = Modifier.fillMaxWidth())
        VazieButton(
            text = "Import config",
            onClick = {},
            variant = VazieButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        VazieButton(
            text = "Remove",
            onClick = {},
            variant = VazieButtonVariant.Destructive,
            modifier = Modifier.fillMaxWidth(),
        )
        VazieButton(text = "Not now", onClick = {}, variant = VazieButtonVariant.Text)
    }
}

@VaziePreview
@Composable
private fun VazieButtonStatesPreview() {
    VaziePreviewTheme {
        VazieButton("Checking", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
        VazieButton("Connect", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
        VazieButton(
            text = "Cancel",
            onClick = {},
            variant = VazieButtonVariant.Secondary,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        VazieButton(
            text = "Add config",
            onClick = {},
            leadingContent = { Icon(VazieIcons.Plus, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@VaziePreview
@Composable
private fun VazieButtonAppearancePreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieButton("Connect", onClick = {}, modifier = Modifier.fillMaxWidth())
        VazieButton(
            text = "Импортировать конфигурацию",
            onClick = {},
            variant = VazieButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
