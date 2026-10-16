package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.vazie.vpn.core.designsystem.R as DesignSystemR
import app.vazie.vpn.core.designsystem.preview.Appearances
import app.vazie.vpn.core.designsystem.preview.VazieScreenPreview
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate

// The Vazie mark stands in for the launcher art, which lives in :app. One render per palette.
@VazieScreenPreview
@Composable
private fun AppearanceScreenPreview(@PreviewParameter(Appearances::class) appearance: Appearance) {
    VazieTheme(appearance = appearance) {
        // Stand-in plates, built from theme tokens so the preview owns no literal. The real ones
        // are `drawable/ic_launcher_plate_*` in :app, which this module cannot see.
        val colors = VazieTheme.colors
        val plates = mapOf(
            VazieAppIconPlate.DEEP to SolidColor(colors.primary),
            VazieAppIconPlate.LIGHT to SolidColor(colors.surfaceMuted),
            VazieAppIconPlate.INK to SolidColor(colors.scrim),
        )
        AppearanceScreen(
            state = AppearanceUiState(
                appearance = appearance,
                appIcon = VazieAppIcon.ORBIT,
                appIconPlate = VazieAppIconPlate.DEEP,
            ),
            appIconArt = { _, plate ->
                AppIconArt(
                    plate = plates[plate] ?: SolidColor(colors.primary),
                    foreground = DesignSystemR.drawable.ic_vazie_mark,
                )
            },
            resolvePlate = { icon, plate -> if (icon.supports(plate)) plate else icon.signaturePlate },
            onAction = {},
        )
    }
}
