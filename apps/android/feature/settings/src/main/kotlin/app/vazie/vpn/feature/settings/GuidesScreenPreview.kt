package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieGuideId

/** The mixed state, which is the only interesting one: two guides opened, two not. */
@VaziePreview
@Composable
private fun GuidesScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        GuidesScreen(
            state = GuidesUiState(
                guides = guideList(
                    acknowledged = setOf(VazieGuideId.WIDGETS, VazieGuideId.LAUNCHER_SHORTCUTS),
                ),
            ),
            onAction = {},
        )
    }
}

/** Nothing opened yet — the state a person meets the screen in, and the one where the two sections have to
 * hold apart on structure alone, with no markers to help. */
@Preview(name = "nothing viewed", widthDp = 360, heightDp = 800)
@Composable
private fun GuidesScreenUntouchedPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        GuidesScreen(state = GuidesUiState(guides = guideList(acknowledged = emptySet())), onAction = {})
    }
}

/** Russian at a large font scale: two headings, five rows and two-line subtitles, which is where the
 * separation is most likely to collapse into one wall of text. */
@Preview(name = "ru · large font", widthDp = 360, heightDp = 900, fontScale = 1.5f, locale = "ru")
@Composable
private fun GuidesScreenRussianPreview() {
    VazieTheme(appearance = Appearance.MILK) {
        GuidesScreen(
            state = GuidesUiState(guides = guideList(acknowledged = setOf(VazieGuideId.WIDGETS))),
            onAction = {},
        )
    }
}
