package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

@VaziePreview
@Composable
private fun VazieCodeFieldPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieCodeField(value = "", onValueChange = {}, label = "Code from the email")
        VazieCodeField(value = "4821", onValueChange = {}, label = "Code from the email")
        VazieCodeField(
            value = "482193",
            onValueChange = {},
            label = "Code from the email",
            errorMessage = "That code did not work. Check the latest email.",
        )
        VazieCodeField(value = "4821", onValueChange = {}, label = "Code from the email", enabled = false)
    }
}

/** The narrowest window and a large font: the cells shrink, the digits stay on one line. */
@Preview(name = "Narrow · large font", widthDp = 280, fontScale = 1.6f)
@Composable
private fun VazieCodeFieldNarrowPreview() {
    VaziePreviewTheme {
        VazieCodeField(value = "482193", onValueChange = {}, label = "Код из письма")
    }
}
