package app.vazie.vpn.core.designsystem.component

import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance

private fun labels(step: Int, of: Int) = VazieTourCalloutLabels(
    next = "Next",
    back = "Back",
    skip = "Skip tour",
    progress = "$step / $of",
    progressSpoken = "Step $step of $of",
)

/** The first step and a middle one: the difference is whether Back exists. */
@VaziePreview
@Composable
private fun VazieTourCalloutPreview() {
    VaziePreviewTheme(Appearance.MILK) {
        VazieTourCallout(
            title = "Connect and disconnect",
            body = "This is the button that opens and closes the tunnel. It changes to Disconnect " +
                "once you are protected.",
            labels = labels(1, 4),
            onNext = {},
            onSkip = {},
        )
        VazieTourCallout(
            title = "Where your configurations live",
            body = "The Connections tab holds everything you have imported, plus the servers Vazie " +
                "runs itself.",
            labels = labels(3, 4),
            onNext = {},
            onBack = {},
            onSkip = {},
        )
    }
}

