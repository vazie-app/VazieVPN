package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.theme.vazieShadow

/** How a surface separates itself from the background. */
enum class VazieSurfaceStyle { Floating, Outlined, Flat }

@Composable
fun VazieCard(
    modifier: Modifier = Modifier,
    style: VazieSurfaceStyle = VazieSurfaceStyle.Floating,
    shape: Shape = VazieTheme.shapes.lg,
    contentPadding: PaddingValues = PaddingValues(VazieTheme.spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .surfaceDecoration(style, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** A card whose children are rows separated by dividers, with the corner radius clipping the first and last
 * row. Padding belongs to the rows, so this takes none. */
@Composable
fun VazieListCard(
    modifier: Modifier = Modifier,
    style: VazieSurfaceStyle = VazieSurfaceStyle.Floating,
    shape: Shape = VazieTheme.shapes.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.surfaceDecoration(style, shape),
        content = content,
    )
}

@Composable
private fun Modifier.surfaceDecoration(style: VazieSurfaceStyle, shape: Shape): Modifier {
    val colors = VazieTheme.colors
    return when (style) {
        VazieSurfaceStyle.Floating -> this
            .vazieShadow(shape, alpha = VazieTheme.elevation.restingAlpha)
            .clip(shape)
            .background(colors.surface)

        VazieSurfaceStyle.Outlined -> this
            .clip(shape)
            .background(colors.surface)
            .border(VazieTheme.borders.hairline, colors.border, shape)

        VazieSurfaceStyle.Flat -> this
            .clip(shape)
            .background(colors.surface)
    }
}

@Composable
fun VazieDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = VazieTheme.borders.hairline,
        color = VazieTheme.colors.dividerSubtle,
    )
}

/** A label for a group *inside* a section — quieter than [VazieSectionHeader], because it divides one choice
 * rather than starting a new one. */
@Composable
fun VazieGroupLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        // Sentence case, as the "Маршрут" design draws group labels.
        text = title,
        style = VazieTheme.typography.sectionLabel,
        color = VazieTheme.colors.textSecondary,
        modifier = modifier.semantics { heading() },
    )
}

/** Section label, optionally with a trailing action such as "See all" or "+ Add". */
@Composable
fun VazieSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VazieTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Sentence case, as the "Маршрут" settings draw their sections.
            text = title,
            style = VazieTheme.typography.sectionLabel,
            color = VazieTheme.colors.textSecondary,
            // A heading, so TalkBack can move section by section.
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}
