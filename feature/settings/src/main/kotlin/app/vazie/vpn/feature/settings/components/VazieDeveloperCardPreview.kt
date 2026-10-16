package app.vazie.vpn.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.settings.bundledAboutLinks

/** The author's two links under one human sentence, then Vazie's own card. What to look at: whether the first
 * still reads as a person and the two read as two different places to write. */
@VaziePreview
@Composable
private fun VazieDeveloperCardPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Column(verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            val links = bundledAboutLinks()
            VazieDeveloperCard(links = links.author, onOpenLink = {})
            VazieProjectCard(links = links.project, onOpenLink = {})
        }
    }
}
