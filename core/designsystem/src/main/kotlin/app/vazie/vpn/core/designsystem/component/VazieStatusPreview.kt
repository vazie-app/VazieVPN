package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

@VaziePreview
@Composable
private fun VazieStatusChipPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        ) {
            VazieStatusTone.entries.forEach { tone ->
                VazieStatusChip(label = tone.name, tone = tone)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            VazieStatusTone.entries.forEach { tone -> VazieStatusDot(tone) }
        }
    }
}

@VaziePreview
@Composable
private fun VazieBadgePreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            VazieBadge("WIREGUARD")
            VazieBadge("VLESS", tone = VazieBadgeTone.Strong)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            VazieMark {
                Text(
                    text = "WG",
                    style = VazieTheme.typography.monoSmall,
                    color = VazieTheme.colors.engineBadgeFg,
                )
            }
            VazieMark(tone = VazieBadgeTone.Strong) {
                Icon(VazieIcons.Check, contentDescription = null)
            }
        }
    }
}

// Status colours are where a new palette goes wrong first: the identity colour and one of the five
// tones start looking like each other. All eight, on one component, is the cheapest way to see it.
@VaziePreview
@Composable
private fun VazieStatusChipAllAppearancesPreview(
    @PreviewParameter(Appearances::class) appearance: Appearance,
) {
    VaziePreviewTheme(appearance) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        ) {
            VazieStatusTone.entries.forEach { tone -> VazieStatusChip(label = tone.name, tone = tone) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            VazieBadge("PRIMARY", tone = VazieBadgeTone.Strong)
            VazieBadge("MUTED")
        }
    }
}
