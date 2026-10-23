package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The format phrase is passed in exactly as it is in production — `:app` reads the parser registry and hands
 * the answer down. A preview that hardcoded it would keep rendering yesterday's answer. */
private fun previewState() = GettingStartedUiState(
    topic = GettingStartedTopic.FIRST_CONFIGURATION,
    supportedFormats = "VLESS",
)

@VazieScreenPreview
@Composable
private fun GettingStartedScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        GettingStartedScreen(state = previewState(), onAction = {})
    }
}

/** Russian at a large font scale. The prose grows, the specimen does not, and the action stays pinned — the
 * three properties this screen has to keep at once. */
@Preview(name = "ru · large font", widthDp = 360, heightDp = 900, fontScale = 1.5f, locale = "ru")
@Composable
private fun GettingStartedRussianPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        GettingStartedScreen(state = previewState(), onAction = {})
    }
}

/** 280dp: the specimen is longer than this at any scale, so it scrolls inside its block rather than widening
 * the screen around it. */
@Preview(name = "narrow", widthDp = 280, heightDp = 720)
@Composable
private fun GettingStartedNarrowPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        GettingStartedScreen(state = previewState(), onAction = {})
    }
}
