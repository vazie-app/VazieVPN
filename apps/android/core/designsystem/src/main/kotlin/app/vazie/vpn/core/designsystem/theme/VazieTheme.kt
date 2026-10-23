package app.vazie.vpn.core.designsystem.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalVazieColors = staticCompositionLocalOf { NightIndigoColors }
val LocalVazieTypography = staticCompositionLocalOf { VazieTypography() }
val LocalVazieSpacing = staticCompositionLocalOf { VazieSpacing() }
val LocalVazieShapes = staticCompositionLocalOf { VazieShapes() }
val LocalVazieElevation = staticCompositionLocalOf { VazieElevation() }
val LocalVazieBorders = staticCompositionLocalOf { VazieBorders() }

val LocalReduceMotion = staticCompositionLocalOf { false }

/** How much of the bottom of the window is covered by something floating over the content. */
val LocalVazieScrollEdge = staticCompositionLocalOf { 0.dp }

/** Entry point for every Vazie surface. */
@Composable
fun VazieTheme(
    appearance: Appearance = Appearance.Default,
    content: @Composable () -> Unit,
) {
    val colors = colorsFor(appearance)
    val typography = remember { VazieTypography() }
    val spacing = remember { VazieSpacing() }
    val shapes = remember { VazieShapes() }
    val elevation = remember { VazieElevation() }
    val borders = remember { VazieBorders() }
    val reduceMotion = rememberReduceMotion()

    CompositionLocalProvider(
        LocalVazieColors provides colors,
        LocalVazieTypography provides typography,
        LocalVazieSpacing provides spacing,
        LocalVazieShapes provides shapes,
        LocalVazieElevation provides elevation,
        LocalVazieBorders provides borders,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = shapes.toMaterialShapes(),
        ) {
            // The one line that gives every touchable surface in Vazie a Material ripple.
            CompositionLocalProvider(LocalIndication provides ripple(), content = content)
        }
    }
}

object VazieTheme {
    val colors: VazieColors
        @Composable @ReadOnlyComposable get() = LocalVazieColors.current
    val typography: VazieTypography
        @Composable @ReadOnlyComposable get() = LocalVazieTypography.current
    val spacing: VazieSpacing
        @Composable @ReadOnlyComposable get() = LocalVazieSpacing.current
    val shapes: VazieShapes
        @Composable @ReadOnlyComposable get() = LocalVazieShapes.current
    val elevation: VazieElevation
        @Composable @ReadOnlyComposable get() = LocalVazieElevation.current
    val borders: VazieBorders
        @Composable @ReadOnlyComposable get() = LocalVazieBorders.current
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
    val scrollEdge: Dp
        @Composable @ReadOnlyComposable get() = LocalVazieScrollEdge.current
}

/** True when animations are off system-wide; observed, so changes apply immediately. */
@Composable
fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    var reduceMotion by remember(resolver) { mutableStateOf(resolver.animationsDisabled()) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = resolver.animationsDisabled()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        runCatching { resolver.registerContentObserver(uri, false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduceMotion
}

private fun ContentResolver.animationsDisabled(): Boolean = runCatching {
    Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

private fun VazieColors.toMaterialColorScheme() =
    if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = if (isDark) background else elevated,
            secondary = secondary,
            tertiary = accent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceMuted,
            onSurfaceVariant = onSurfaceMuted,
            error = error,
            onError = background,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = border,
            outlineVariant = dividerSubtle,
            scrim = scrim,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = elevated,
            secondary = secondary,
            tertiary = accent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceMuted,
            onSurfaceVariant = onSurfaceMuted,
            error = error,
            onError = elevated,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = border,
            outlineVariant = dividerSubtle,
            scrim = scrim,
        )
    }

private fun VazieTypography.toMaterialTypography() = Typography(
    displaySmall = display,
    headlineSmall = headline,
    titleMedium = title,
    titleSmall = titleSmall,
    bodyLarge = body,
    bodyMedium = bodySecondary,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = labelSmall,
    labelSmall = caption,
)

private fun VazieShapes.toMaterialShapes() = androidx.compose.material3.Shapes(
    extraSmall = sm,
    small = sm,
    medium = md,
    large = lg,
    extraLarge = xl,
)
