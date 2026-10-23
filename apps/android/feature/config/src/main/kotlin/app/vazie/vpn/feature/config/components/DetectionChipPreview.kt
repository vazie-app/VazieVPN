package app.vazie.vpn.feature.config.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.config.AddConfigFixtures

// Every outcome together: the set step 2 can reach, and the quickest way to see that two of them are
// not protocols at all.
@VaziePreview
@Composable
private fun DetectionChipPreview() {
    VaziePreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            AddConfigFixtures.allOutcomes.forEach { DetectionChip(detection = it) }
        }
    }
}
