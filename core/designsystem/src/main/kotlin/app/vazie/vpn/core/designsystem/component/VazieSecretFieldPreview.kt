package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

// The sample value is deliberately not key-shaped. A preview that looks like a credential is how a
// credential ends up in a screenshot.
private const val SampleValue = "sample-value"

@VaziePreview
@Composable
private fun VazieSecretFieldPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieSecretField(
            value = SampleValue,
            revealed = false,
            label = "Key",
            trailingContent = { VazieFieldAction(label = "SHOW", onClick = {}) },
        )
        VazieSecretField(
            value = SampleValue,
            revealed = true,
            label = "Key",
            trailingContent = { VazieFieldAction(label = "HIDE", onClick = {}) },
        )
        VazieSecretField(value = SampleValue, revealed = false, label = "Key", valid = true)
    }
}
