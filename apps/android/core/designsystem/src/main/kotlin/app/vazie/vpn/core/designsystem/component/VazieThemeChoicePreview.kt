package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.theme.colorsFor

// Both palettes side by side, because the point of the card is that a person can see the difference
// without applying anything.
@VaziePreview
@Composable
private fun VazieThemeChoicePreview(
    @PreviewParameter(Appearances::class) appearance: Appearance,
) {
    VaziePreviewTheme(appearance) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            Appearance.entries.forEach { option ->
                VazieThemeChoice(
                    label = option.name,
                    colors = colorsFor(option),
                    selected = option == appearance,
                    onSelect = {},
                )
            }
        }
    }
}
