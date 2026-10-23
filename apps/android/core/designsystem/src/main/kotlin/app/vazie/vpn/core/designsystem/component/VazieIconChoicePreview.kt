package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.R
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VaziePreview
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme

// The launcher art lives in :app; the Vazie mark stands in here.
@VaziePreview
@Composable
private fun VazieIconChoicePreview(
    @PreviewParameter(Appearances::class) appearance: Appearance,
) {
    VaziePreviewTheme(appearance) {
        Row(horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs)) {
            VazieIconChoice(
                label = "Orbit",
                plate = BRAND_PLATE,
                foreground = R.drawable.ic_vazie_mark,
                selected = true,
                onSelect = {},
                modifier = Modifier,
            )
            VazieIconChoice(
                label = "Match theme",
                plate = BRAND_PLATE,
                foreground = R.drawable.ic_vazie_mark,
                selected = false,
                onSelect = {},
                stateDescription = "Currently Deep",
            )
        }
    }
}

// The canonical plate's gradient, copied so the preview matches the launcher.
private val BRAND_PLATE = Brush.linearGradient(
    0f to Color(0xFF4A1585),
    0.5f to Color(0xFF25115E),
    1f to Color(0xFF071A3A),
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f),
)
