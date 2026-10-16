package app.vazie.vpn.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Accessibility outranks matching the source design exactly. */
class VazieColorsContrastTest {

    /** Derived from the enum, never listed by hand. */
    private val appearances: List<Pair<String, VazieColors>> = Appearance.entries
        .map { it.name to colorsFor(it) }

    /** Foreground/background pairs that carry text or a meaningful glyph. */
    private fun pairs(c: VazieColors) = listOf(
        "textPrimary on background" to (c.textPrimary to c.background),
        "textPrimary on surface" to (c.textPrimary to c.surface),
        "textSecondary on background" to (c.textSecondary to c.background),
        "textSecondary on surface" to (c.textSecondary to c.surface),
        "primaryText on background" to (c.primaryText to c.background),
        "primaryText on surface" to (c.primaryText to c.surface),
        "successText on surface" to (c.successText to c.surface),
        "warningText on surface" to (c.warningText to c.surface),
        "errorText on surface" to (c.errorText to c.surface),
        "onSurfaceMuted on surfaceMuted" to (c.onSurfaceMuted to c.surfaceMuted),
        "engineBadgeFg on surfaceMuted" to (c.engineBadgeFg to c.surfaceMuted),
        "onAccentContainer on accentContainer" to (c.onAccentContainer to c.accentContainer),
        "onErrorContainer on errorContainer" to (c.onErrorContainer to c.errorContainer),
        "onWarningContainer on warningContainer" to (c.onWarningContainer to c.warningContainer),
        "technicalLabel on technicalSurface" to (c.technicalLabel to c.technicalSurface),
        "technicalValue on technicalSurface" to (c.technicalValue to c.technicalSurface),
        "technicalValueAccent on technicalSurface" to (c.technicalValueAccent to c.technicalSurface),
        "technicalLabel on technicalSurfaceAlt" to (c.technicalLabel to c.technicalSurfaceAlt),
        "technicalLabel on background" to (c.technicalLabel to c.background),
        "technicalValue on background" to (c.technicalValue to c.background),
        "technicalValueAccent on background" to (c.technicalValueAccent to c.background),
        // The toggle fills with `primary`; an accent colour is not automatically a fill for glyphs.
        "onPrimary on primary" to (c.onPrimary to c.primary),
        // The destructive button paints the same content colour on the error fill.
        "onError on error" to (c.elevated to c.error),
        "onInverseSurface on inverseSurface" to (c.onInverseSurface to c.inverseSurface),
        "inverseAccent on inverseSurface" to (c.inverseAccent to c.inverseSurface),
        "successText on background" to (c.successText to c.background),
        "warningText on background" to (c.warningText to c.background),
        "errorText on background" to (c.errorText to c.background),
        // The recessed panel carries values and accents, not only labels.
        "technicalValue on technicalSurfaceAlt" to (c.technicalValue to c.technicalSurfaceAlt),
        "technicalValueAccent on technicalSurfaceAlt" to
            (c.technicalValueAccent to c.technicalSurfaceAlt),
        // The segmented control writes the unselected option straight onto its track.
        "textSecondary on segmentedTrack" to (c.textSecondary to c.segmentedTrack),
        // Cards sit on `elevated`, which is not `surface` in every appearance.
        "textPrimary on elevated" to (c.textPrimary to c.elevated),
        "textSecondary on elevated" to (c.textSecondary to c.elevated),
        "primaryText on elevated" to (c.primaryText to c.elevated),
        "textMuted on background" to (c.textMuted to c.background),
        "textMuted on surface" to (c.textMuted to c.surface),
        "textMuted on elevated" to (c.textMuted to c.elevated),
        "textPrimary on surfaceMuted" to (c.textPrimary to c.surfaceMuted),
        "onAction on action" to (c.onAction to c.action),
        "onAction on actionPressed" to (c.onAction to c.actionPressed),
        "onSuccessContainer on successContainer" to (c.onSuccessContainer to c.successContainer),
        "onAccentContainer on routeSoft" to (c.onAccentContainer to c.routeSoft),
        "routeStart on background" to (c.routeStart to c.background),
    )

    @Test
    fun `every text pair meets WCAG AA in every appearance`() {
        val failures = buildList {
            appearances.forEach { (name, colors) ->
                pairs(colors).forEach { (label, pair) ->
                    val ratio = contrastRatio(pair.first, pair.second)
                    if (ratio < AA) add("$name · $label = ${"%.2f".format(ratio)}")
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("Contrast below AA ($AA:1):\n" + failures.joinToString("\n") { "  $it" })
        }
    }

    @Test
    fun `text variants are at least as legible as the fill roles they replace`() {
        appearances.forEach { (name, c) ->
            listOf(
                "primary" to (c.primaryText to c.primary),
                "success" to (c.successText to c.success),
                "warning" to (c.warningText to c.warning),
                "error" to (c.errorText to c.error),
            ).forEach { (role, pair) ->
                val (textVariant, fill) = pair
                val background = if (role == "primary") c.background else c.surface
                assertTrue(
                    contrastRatio(textVariant, background) >= contrastRatio(fill, background) - EPSILON,
                    "$name: ${role}Text must not be less legible than $role",
                )
            }
        }
    }

    /** The disabled state, held to a lower bar on purpose and to *a* bar all the same. */
    @Test
    fun `disabled content stays visible without pretending to be enabled`() {
        val failures = buildList {
            appearances.forEach { (name, c) ->
                listOf(
                    "textDisabled on disabled" to (c.textDisabled to c.disabled),
                    "textDisabled on fieldDisabledBg" to (c.textDisabled to c.fieldDisabledBg),
                    "textDisabled on background" to (c.textDisabled to c.background),
                    "textDisabled on surface" to (c.textDisabled to c.surface),
                ).forEach { (label, pair) ->
                    val (disabled, ground) = pair
                    val ratio = contrastRatio(disabled, ground)
                    if (ratio < DisabledMinimum) add("$name · $label = ${"%.2f".format(ratio)}")
                    // Disabled is relative: compared with live text on the same ground.
                    val live = contrastRatio(c.textPrimary, ground)
                    if (ratio > live / DisabledRecession) {
                        add("$name · $label = ${"%.2f".format(ratio)} against live text at ${"%.2f".format(live)}")
                    }
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "Disabled content must clear $DisabledMinimum:1 and stay " +
                    "${DisabledRecession}× behind live text:\n" + failures.joinToString("\n") { "  $it" },
            )
        }
    }

    /** The `isDark` flag and the palette it describes have to agree, and so does the family the Appearance
     * screen files the constant under. */
    @Test
    fun `each palette is marked with the brightness it actually paints`() {
        Appearance.entries.forEach { appearance ->
            val colors = colorsFor(appearance)
            val luminance = colors.background.relativeLuminance()
            assertTrue(
                colors.isDark == (luminance < 0.1),
                "$appearance: isDark=${colors.isDark} on a background of $luminance",
            )
        }
    }

    private companion object {
        const val AA = 4.5
        const val EPSILON = 0.001

        /** See `disabled content stays visible without pretending to be enabled`. */
        const val DisabledMinimum = 2.0

        /** Disabled content must be at least this many times less legible than live text beside it. */
        const val DisabledRecession = 2.0
    }
}

internal fun Color.relativeLuminance(): Double {
    fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

internal fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
