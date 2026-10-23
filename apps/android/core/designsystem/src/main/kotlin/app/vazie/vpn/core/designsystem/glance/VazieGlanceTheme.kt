package app.vazie.vpn.core.designsystem.glance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.MilkColors
import app.vazie.vpn.core.designsystem.theme.NightIndigoColors

/** The Vazie token layer for Glance surfaces. */
@Composable
fun VazieGlanceTheme(
    appearance: Appearance = Appearance.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVazieGlanceColors provides vazieGlanceColors(appearance),
        LocalVazieGlanceDimens provides VazieGlanceDimens(),
        LocalVazieGlanceTypography provides VazieGlanceTypography(),
        content = content,
    )
}

object VazieGlanceTheme {
    val colors: VazieGlanceColors
        @Composable @ReadOnlyComposable get() = LocalVazieGlanceColors.current
    val dimens: VazieGlanceDimens
        @Composable @ReadOnlyComposable get() = LocalVazieGlanceDimens.current
    val typography: VazieGlanceTypography
        @Composable @ReadOnlyComposable get() = LocalVazieGlanceTypography.current
}

/** The roles a widget draws with — a strict subset of [app.vazie.vpn.core.designsystem.theme.VazieColors]. */
@Immutable
data class VazieGlanceColors(
    val background: ColorProvider,
    val surface: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val success: ColorProvider,
    val warning: ColorProvider,
    val error: ColorProvider,
    val border: ColorProvider,
    val surfaceMuted: ColorProvider,
    val statusDotIdle: ColorProvider,
)

/** The palette of the appearance the person chose in the app - Milk or Night Indigo - for both halves of the
 * day/night pair: the widget follows the app, not the system theme, exactly like the app does. */
private fun vazieGlanceColors(appearance: Appearance = Appearance.Default): VazieGlanceColors {
    val palette = when (appearance) {
        Appearance.MILK -> MilkColors
        Appearance.NIGHT_INDIGO -> NightIndigoColors
    }
    val light = palette
    val dark = palette
    return VazieGlanceColors(
        background = ColorProvider(day = light.background, night = dark.background),
        surface = ColorProvider(day = light.surface, night = dark.surface),
        textPrimary = ColorProvider(day = light.textPrimary, night = dark.textPrimary),
        textSecondary = ColorProvider(day = light.textSecondary, night = dark.textSecondary),
        primary = ColorProvider(day = light.primary, night = dark.primary),
        onPrimary = ColorProvider(day = light.onPrimary, night = dark.onPrimary),
        // The *Text variants, not the fills: a widget's status word is small text on a surface, and
        // the fills do not reach AA at that size (DS-1).
        success = ColorProvider(day = light.successText, night = dark.successText),
        warning = ColorProvider(day = light.warningText, night = dark.warningText),
        error = ColorProvider(day = light.errorText, night = dark.errorText),
        border = ColorProvider(day = light.border, night = dark.border),
        surfaceMuted = ColorProvider(day = light.surfaceMuted, night = dark.surfaceMuted),
        statusDotIdle = ColorProvider(day = light.statusDotIdle, night = dark.statusDotIdle),
    )
}

internal val LocalVazieGlanceColors =
    staticCompositionLocalOf { vazieGlanceColors() }
internal val LocalVazieGlanceDimens = staticCompositionLocalOf { VazieGlanceDimens() }
internal val LocalVazieGlanceTypography = staticCompositionLocalOf { VazieGlanceTypography() }
