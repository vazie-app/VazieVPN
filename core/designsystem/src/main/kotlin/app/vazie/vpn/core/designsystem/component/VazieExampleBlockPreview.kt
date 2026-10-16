package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

/** The same specimen in both palettes, monospaced in each. */
@VaziePreview
@Composable
private fun VazieExampleBlockPreview() {
    VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
        VazieExampleBlock(
            text = SPECIMEN,
            description = "Example of machine-readable input",
        )
    }
    VaziePreviewTheme(Appearance.MILK) {
        VazieExampleBlock(
            text = SPECIMEN,
            description = "Example of machine-readable input",
        )
    }
}

private const val SPECIMEN =
    "example-machine-readable-input-that-does-not-fit-in-the-block-and-scrolls"
