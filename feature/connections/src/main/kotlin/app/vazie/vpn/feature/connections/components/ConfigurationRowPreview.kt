package app.vazie.vpn.feature.connections.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.feature.connections.ConnectionsFixtures
import app.vazie.vpn.feature.connections.R

// All five row states at once: this is where an "unavailable" row that looks like an error, or a
// status that reads only as a colour, becomes obvious.
@VaziePreview
@Composable
private fun ConfigurationRowPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        VazieListCard {
            ConnectionsFixtures.populated.configurations.forEachIndexed { index, row ->
                if (index > 0) VazieDivider()
                ConfigurationRow(row = row, onSelect = {}, onOpenDetails = {})
            }
        }
    }
}
