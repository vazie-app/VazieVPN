package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

@VaziePreview
@Composable
private fun VazieTextFieldPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieTextField(value = "Home relay", onValueChange = {}, label = "Name")
        VazieTextField(
            value = "",
            onValueChange = {},
            label = "Link",
            placeholder = "Paste a link",
        )
    }
}

@VaziePreview
@Composable
private fun VazieTextFieldStatesPreview() {
    VaziePreviewTheme {
        VazieTextField(
            value = "not a valid link",
            onValueChange = {},
            label = "Link",
            errorMessage = "Could not read this link",
        )
        VazieTextField(value = "Home relay", onValueChange = {}, label = "Name", enabled = false)
        VazieTextField(
            value = "relay.example.net",
            onValueChange = {},
            label = "Endpoint",
            trailingContent = { VazieFieldAction(label = "COPY", onClick = {}) },
        )
    }
}
