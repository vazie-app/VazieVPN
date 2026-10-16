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
private fun VazieConsentCheckPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieConsentCheck(
            checked = false,
            onCheckedChange = {},
            text = "I accept the terms of the %1\$s",
            linkText = "public offer",
            url = "https://vazie.app/legal/offer",
        )
        VazieConsentCheck(
            checked = true,
            onCheckedChange = {},
            text = "I accept the terms of the %1\$s",
            linkText = "public offer",
            url = "https://vazie.app/legal/offer",
        )
    }
}

@Preview(name = "ru · large font", widthDp = 300, fontScale = 1.6f, locale = "ru")
@Composable
private fun VazieConsentCheckRussianPreview() {
    VaziePreviewTheme {
        VazieConsentCheck(
            checked = true,
            onCheckedChange = {},
            text = "Я принимаю условия %1\$s",
            linkText = "публичной оферты",
            url = "https://vazie.app/legal/offer",
        )
    }
}
