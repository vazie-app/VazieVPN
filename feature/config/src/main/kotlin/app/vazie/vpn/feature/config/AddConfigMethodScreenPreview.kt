package app.vazie.vpn.feature.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The format phrase is passed in, as in production, where `:app` reads the parser registry. */
private fun previewState() = AddConfigUiState(supportedFormats = "VLESS")

@VazieScreenPreview
@Composable
private fun AddConfigMethodPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        AddConfigMethodScreen(state = previewState(), link = "", onLinkChange = {}, onPaste = {}, onSubmit = {}, onAction = {})
    }
}

/** Russian at a large font scale, which is where the example block has to prove it scrolls rather than wraps:
 * the prose around it grows and the specimen does not, and neither may push the action off. */
@Preview(name = "ru · large font", widthDp = 360, heightDp = 800, fontScale = 1.5f, locale = "ru")
@Composable
private fun AddConfigMethodRussianPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        AddConfigMethodScreen(state = previewState(), link = "", onLinkChange = {}, onPaste = {}, onSubmit = {}, onAction = {})
    }
}

/** The narrowest width Vazie supports. The specimen is longer than this at any font scale, which is the
 * point: it scrolls inside its own block instead of breaking the layout around it. */
@Preview(name = "narrow", widthDp = 280, heightDp = 640)
@Composable
private fun AddConfigMethodNarrowPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        AddConfigMethodScreen(state = previewState(), link = "", onLinkChange = {}, onPaste = {}, onSubmit = {}, onAction = {})
    }
}

