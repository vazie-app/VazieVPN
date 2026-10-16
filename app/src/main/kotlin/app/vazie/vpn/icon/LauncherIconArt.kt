package app.vazie.vpn.icon

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import app.vazie.vpn.R
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.feature.settings.AppIconArt

/** Everything `:app` knows about what a launcher icon looks like. */
object LauncherIconArt {

    /** Turn a stored plate choice into a plate this mark actually stands on. */
    fun resolvePlate(plate: VazieAppIconPlate, icon: VazieAppIcon): VazieAppIconPlate =
        if (icon.supports(plate)) plate else icon.signaturePlate

    /** The foreground layer for a mark on a plate. */
    @DrawableRes
    fun foreground(icon: VazieAppIcon, plate: VazieAppIconPlate): Int = when (icon) {
        VazieAppIcon.ORBIT -> R.mipmap.ic_launcher_orbit_foreground
        VazieAppIcon.LIME -> R.mipmap.ic_launcher_lime_foreground
        VazieAppIcon.EMERALD -> R.mipmap.ic_launcher_emerald_foreground
        VazieAppIcon.MAGENTA -> R.mipmap.ic_launcher_magenta_foreground
        VazieAppIcon.COTTON_CANDY -> R.mipmap.ic_launcher_cotton_candy_foreground
        VazieAppIcon.CRIMSON -> R.mipmap.ic_launcher_crimson_foreground
        VazieAppIcon.GOLD -> R.mipmap.ic_launcher_gold_foreground
        VazieAppIcon.PEARL -> R.mipmap.ic_launcher_pearl_foreground
        VazieAppIcon.ONYX -> R.mipmap.ic_launcher_onyx_foreground

        // The mask itself, never the `<bitmap>` wrapper: `painterResource` only parses `<vector>`. Ink comes
        // via [tint].
        VazieAppIcon.MONOCHROME -> R.mipmap.ic_launcher_monochrome
    }

    /** The ink a mark is drawn in, or `null` when the mark carries its own colour. */
    fun tint(icon: VazieAppIcon, plate: VazieAppIconPlate, colours: LauncherPlateColours): Color? =
        if (icon != VazieAppIcon.MONOCHROME) null
        else if (plate.isLight) colours.ink else colours.paper

    /** Everything the picker needs to draw one tile, in one call. */
    fun art(
        icon: VazieAppIcon,
        plate: VazieAppIconPlate,
        colours: LauncherPlateColours,
    ): AppIconArt = AppIconArt(
        plate = plateBrush(plate, colours),
        foreground = foreground(icon, plate),
        foregroundTint = tint(icon, plate, colours),
    )

    /** A plate as a brush, for the picker. */
    fun plateBrush(plate: VazieAppIconPlate, colours: LauncherPlateColours): Brush = when (plate) {
        VazieAppIconPlate.DEEP -> Brush.linearGradient(
            0f to colours.deepStart,
            0.5f to colours.deepCenter,
            1f to colours.deepEnd,
            start = BottomLeft,
            end = TopRight,
        )

        VazieAppIconPlate.LIGHT -> Brush.linearGradient(
            0f to colours.lightStart,
            1f to colours.lightEnd,
            start = BottomLeft,
            end = TopRight,
        )

        VazieAppIconPlate.FOREST -> Brush.linearGradient(
            0f to colours.forestStart,
            0.5f to colours.forestCentre,
            1f to colours.forestEnd,
            start = BottomLeft,
            end = TopRight,
        )

        VazieAppIconPlate.ROSE -> Brush.linearGradient(
            0f to colours.roseStart,
            0.5f to colours.roseCentre,
            1f to colours.roseEnd,
            start = BottomLeft,
            end = TopRight,
        )

        VazieAppIconPlate.AMBER -> Brush.linearGradient(
            0f to colours.amberStart,
            0.5f to colours.amberCentre,
            1f to colours.amberEnd,
            start = BottomLeft,
            end = TopRight,
        )

        VazieAppIconPlate.MIST -> Brush.linearGradient(
            0f to colours.mistStart,
            0.5f to colours.mistCentre,
            1f to colours.mistEnd,
            start = BottomLeft,
            end = TopRight,
        )

        VazieAppIconPlate.INK -> SolidColor(colours.ink)
    }

    private val BottomLeft = Offset(0f, Float.POSITIVE_INFINITY)
    private val TopRight = Offset(Float.POSITIVE_INFINITY, 0f)
}

/** The six values the three plates are made of, read once from resources. */
@Immutable
data class LauncherPlateColours(
    val deepStart: Color,
    val deepCenter: Color,
    val deepEnd: Color,
    val lightStart: Color,
    val lightEnd: Color,
    val ink: Color,
    val forestStart: Color,
    val forestCentre: Color,
    val forestEnd: Color,
    val roseStart: Color,
    val roseCentre: Color,
    val roseEnd: Color,
    val amberStart: Color,
    val amberCentre: Color,
    val amberEnd: Color,
    val mistStart: Color,
    val mistCentre: Color,
    val mistEnd: Color,
    /** The ink the flat mark takes on a light plate, and the paper it takes on a dark one. */
    val paper: Color,
) {
    companion object {
        @Composable
        fun read(): LauncherPlateColours = LauncherPlateColours(
            deepStart = colorResource(R.color.ic_launcher_plate_deep_start),
            deepCenter = colorResource(R.color.ic_launcher_plate_deep_center),
            deepEnd = colorResource(R.color.ic_launcher_plate_deep_end),
            lightStart = colorResource(R.color.ic_launcher_plate_light_start),
            lightEnd = colorResource(R.color.ic_launcher_plate_light_end),
            ink = colorResource(R.color.ic_launcher_plate_ink_fill),
            forestStart = colorResource(R.color.ic_launcher_plate_forest_start),
            forestCentre = colorResource(R.color.ic_launcher_plate_forest_center),
            forestEnd = colorResource(R.color.ic_launcher_plate_forest_end),
            roseStart = colorResource(R.color.ic_launcher_plate_rose_start),
            roseCentre = colorResource(R.color.ic_launcher_plate_rose_center),
            roseEnd = colorResource(R.color.ic_launcher_plate_rose_end),
            amberStart = colorResource(R.color.ic_launcher_plate_amber_start),
            amberCentre = colorResource(R.color.ic_launcher_plate_amber_center),
            amberEnd = colorResource(R.color.ic_launcher_plate_amber_end),
            mistStart = colorResource(R.color.ic_launcher_plate_mist_start),
            mistCentre = colorResource(R.color.ic_launcher_plate_mist_center),
            mistEnd = colorResource(R.color.ic_launcher_plate_mist_end),
            paper = colorResource(R.color.ic_launcher_mark_paper_fill),
        )
    }
}
