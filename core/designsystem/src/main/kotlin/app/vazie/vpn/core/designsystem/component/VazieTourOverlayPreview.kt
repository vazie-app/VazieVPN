package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.tour.LocalVazieTourTargets
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import app.vazie.vpn.core.designsystem.tour.rememberVazieTourTargets
import app.vazie.vpn.core.designsystem.tour.vazieTourTarget
import androidx.compose.runtime.CompositionLocalProvider

/** The overlay over a stand-in screen, driven through the **real** registration pipeline. */
@VaziePreview
@Composable
private fun VazieTourOverlayPreview() {
    VaziePreviewTheme(Appearance.MILK) {
        Box(Modifier.fillMaxWidth().height(PreviewHeight)) {
            TourHost()
        }
    }
}

@Composable
private fun TourHost() {
    val targets = rememberVazieTourTargets()
    CompositionLocalProvider(LocalVazieTourTargets provides targets) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(VazieTheme.spacing.md),
            ) {
                Text(text = "Vazie", style = VazieTheme.typography.headline)
                VazieButton(
                    text = "Connect",
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = VazieTheme.spacing.xxl)
                        .vazieTourTarget(VazieTourTargetId.CONNECT_CONTROL),
                )
            }
            VazieTourOverlay(target = targets.bounds(VazieTourTargetId.CONNECT_CONTROL)) {
                VazieTourCallout(
                    title = "Connect and disconnect",
                    body = "This is the button that opens and closes the tunnel.",
                    labels = VazieTourCalloutLabels(
                        next = "Next",
                        back = "Back",
                        skip = "Skip tour",
                        progress = "1 / 4",
                        progressSpoken = "Step 1 of 4",
                    ),
                    onNext = {},
                    onSkip = {},
                )
            }
        }
    }
}

private val PreviewHeight = 520.dp
