package app.vazie.vpn.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.vazie.vpn.core.designsystem.R

/** The bundled families, each one variable file rather than one file per weight. */
@Immutable
object VazieFontFamilies {

    /** Interface text. */
    val geologica: FontFamily = geologicaFamily(sharpness = 0f)

    /** Headings: the same face with sharpened terminals. */
    val geologicaHeading: FontFamily = geologicaFamily(sharpness = HeadingSharpness)

    /** Technical values. */
    val martianMono: FontFamily = martianMonoFamily()

}

private const val HeadingSharpness = 40f

/** The weights the scale below uses, and nothing else. */
private val GeologicaWeights = listOf(400, 500, 600, 650, 700)
private val MartianMonoWeights = listOf(400, 550, 600)

// FontVariation.Settings is still marked experimental; there is no stable way to pin an axis, and
// leaving the weight axis unset is not an option — see the note on VazieFontFamilies.
@OptIn(ExperimentalTextApi::class)
private fun geologicaFamily(sharpness: Float): FontFamily = FontFamily(
    GeologicaWeights.map { weight ->
        Font(
            resId = R.font.geologica,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("SHRP", sharpness),
            ),
        )
    }
)

@OptIn(ExperimentalTextApi::class)
private fun martianMonoFamily(): FontFamily = FontFamily(
    MartianMonoWeights.map { weight ->
        Font(
            resId = R.font.martian_mono,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }
)

/** Which family paints which kind of text. One answer since the "Маршрут" redesign: Geologica for product
 * text and labels, Martian Mono for technical values. [lineHeightScale] is 1. */
@Immutable
data class VazieTypefaces(
    val product: FontFamily,
    val label: FontFamily,
    val technical: FontFamily,
    val lineHeightScale: Float,
)

/** The one set of typefaces. A getter rather than a stored value: this file's top-level functions build
 * [VazieFontFamilies], so a stored value here would be initialised while those families are not. */
val DefaultTypefaces: VazieTypefaces
    get() = VazieTypefaces(
        product = VazieFontFamilies.geologica,
        label = VazieFontFamilies.geologica,
        technical = VazieFontFamilies.martianMono,
        lineHeightScale = 1f,
    )

/** The type scale of the "Маршрут" design, measured off the approved screens. */
@Immutable
data class VazieTypography(
    val display: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologicaHeading,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.0345).em,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologicaHeading,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.023).em,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologicaHeading,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.005).em,
    ),
    val titleSmall: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    val bodySecondary: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.em,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    val sectionLabel: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.em,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    ),
    val button: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.geologica,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    val monoDisplay: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.martianMono,
        fontWeight = FontWeight(550),
        fontSize = 27.5.sp,
        lineHeight = 32.sp,
    ),
    val monoTitle: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.martianMono,
        fontWeight = FontWeight(550),
        fontSize = 18.5.sp,
        lineHeight = 24.sp,
    ),
    val monoValue: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.martianMono,
        fontWeight = FontWeight(550),
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.martianMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    val monoSmall: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.martianMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    val monoMicro: TextStyle = TextStyle(
        fontFamily = VazieFontFamilies.martianMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.5.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.06.em,
    ),
)
