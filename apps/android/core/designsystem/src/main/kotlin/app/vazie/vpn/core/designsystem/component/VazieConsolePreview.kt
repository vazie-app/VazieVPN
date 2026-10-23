package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The kit is drawn on the `technical*` roles, so it has to survive both palettes, Milk included. */
@VaziePreview
@Composable
private fun VazieConsoleKitPreview(
    @PreviewParameter(Appearances::class) appearance: Appearance,
) {
    VaziePreviewTheme(appearance = appearance) {
        ConsoleKit()
    }
}

/** The alignment claim, under the font scale that would break it. Values start at the same x on every row or
 * the section is not a table. */
@Preview(name = "Narrow", widthDp = 300)
@Preview(name = "Large font", widthDp = 360, fontScale = 1.8f)
@Preview(name = "Russian", widthDp = 360, locale = "ru")
@Composable
private fun VazieConsoleDensityPreview() {
    VaziePreviewTheme {
        ConsoleKit()
    }
}

@Composable
private fun ConsoleKit() {
    VazieConsoleSection(title = "Session") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.md),
        ) {
            VazieMetric(value = "00:42:17", label = "Duration", modifier = Modifier.weight(1f))
            VazieMetric(value = "184.2 MB", label = "Received", modifier = Modifier.weight(1f))
            VazieMetric(value = "21.8 MB", label = "Sent", modifier = Modifier.weight(1f))
        }
    }
    VazieConsoleSection(
        title = "Tunnel",
    ) {
        VazieReadout(label = "Engine", value = "xray")
        VazieReadout(label = "Protocol", value = "vless · reality", tone = VazieReadoutTone.Accent)
        VazieReadout(label = "Transport", value = "tcp · xtls-vision")
        VazieReadout(label = "Latency", value = "—", tone = VazieReadoutTone.Absent)
    }
}
