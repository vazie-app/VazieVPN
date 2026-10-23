package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

private val QuickSettingsSteps = listOf(
    "Pull the quick settings panel all the way down.",
    "Tap edit — usually a pencil.",
    "Find Vazie among the tiles that are not in use.",
    "Drag it up into the active tiles.",
)

private val ShortcutSteps = listOf(
    "Hold the Vazie icon on your home screen.",
    "Let go on the configuration you want.",
)

/** The two states that differ by whether the platform can be asked for anything. */
@VaziePreview
@Composable
private fun VazieGuidePreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieGuide(
            title = "Connect from the pull-down panel",
            body = "Vazie has a Quick Settings tile: one tap to open or close the tunnel, without " +
                "opening the app.",
            stepsTitle = "How to add it by hand",
            steps = QuickSettingsSteps,
            action = VazieGuideAction(label = "Add Quick Settings tile", onClick = {}),
        )
    }
}

/** No button, and none possible: nothing adds launcher shortcuts, because nothing has to. This is also what
 * any guide looks like on a device that cannot be asked for the others. */
@VaziePreview
@Composable
private fun VazieGuideWithoutActionPreview(
    @PreviewParameter(Appearances::class) appearance: Appearance,
) {
    VaziePreviewTheme(appearance) {
        VazieGuide(
            title = "Your configurations under the icon",
            body = "Holding the Vazie icon on your home screen offers your saved configurations " +
                "straight away, without opening the app and choosing one first.",
            stepsTitle = "How to use it",
            steps = ShortcutSteps,
        )
    }
}

