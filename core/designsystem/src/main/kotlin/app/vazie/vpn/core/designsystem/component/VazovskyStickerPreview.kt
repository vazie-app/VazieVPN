package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

// Rendered against every palette because the artwork has its own colours and a transparent
// background: this is where a sticker that only works on light surfaces would show it.
@VaziePreview
@Composable
private fun VazovskyStickerPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazovskySticker()
    }
}
