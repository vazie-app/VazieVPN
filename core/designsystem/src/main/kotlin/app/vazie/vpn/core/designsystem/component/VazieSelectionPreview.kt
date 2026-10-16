package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.theme.colorsFor

@VaziePreview
@Composable
private fun VazieSwitchPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VaziePreviewTheme(appearance) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm)) {
            VazieSwitch(checked = true, onCheckedChange = {})
            VazieSwitch(checked = false, onCheckedChange = {})
            VazieSwitch(checked = true, onCheckedChange = null, enabled = false)
            VazieSwitch(checked = false, onCheckedChange = null, enabled = false)
        }
    }
}

@VaziePreview
@Composable
private fun VazieSegmentedControlPreview() {
    VaziePreviewTheme {
        VazieSegmentedControl(
            options = listOf("Auto", "Manual"),
            selected = "Auto",
            onSelect = {},
            label = { it },
        )
        VazieSegmentedControl(
            options = listOf("Auto", "Manual"),
            selected = "Auto",
            onSelect = {},
            label = { it },
            enabled = false,
        )
    }
}

@VaziePreview
@Composable
private fun VazieSwatchPickerPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    // All of them: that is what the real picker offers, and nine swatches are where the wrapping
    // has to hold.
    val options = Appearance.entries
    VaziePreviewTheme(appearance) {
        VazieSwatchPicker(
            options = options,
            selected = appearance,
            onSelect = {},
            swatchColor = { colorsFor(it).background },
            label = { it.name },
        )
    }
}

// Four options at a narrow width and a large font: the case a segmented control cannot hold, and the reason
// `VazieChoiceChips` exists. If these ever stop wrapping, a period selector starts clipping its own labels.
@VaziePreview
@Composable
private fun VazieChoiceChipsPreview() {
    VaziePreviewTheme {
        VazieChoiceChips(
            options = listOf("1 month", "3 months", "6 months", "12 months"),
            selected = "12 months",
            onSelect = {},
            label = { it },
        )
    }
}

@Preview(name = "ru · narrow · large font", widthDp = 280, fontScale = 2f, locale = "ru")
@Composable
private fun VazieChoiceChipsStressPreview() {
    VaziePreviewTheme {
        VazieChoiceChips(
            options = listOf("1 месяц", "3 месяца", "6 месяцев", "12 месяцев"),
            selected = "12 месяцев",
            onSelect = {},
            label = { it },
        )
    }
}
