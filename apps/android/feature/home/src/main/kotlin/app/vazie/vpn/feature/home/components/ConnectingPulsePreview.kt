package app.vazie.vpn.feature.home.components

import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.feature.home.R

// Previews always render the resting state: LocalInspectionMode reports reduce-motion, which is also
// what the dots look like for anyone who turned animations off.
@VaziePreview
@Composable
private fun ConnectingPulsePreview() {
    VaziePreviewTheme {
        ConnectingPulse()
    }
}
