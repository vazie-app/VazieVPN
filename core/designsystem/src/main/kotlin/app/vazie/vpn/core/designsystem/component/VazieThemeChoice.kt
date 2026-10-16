package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieColors
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A palette shown as the thing it makes, rather than as the colour it starts from. */
@Composable
fun VazieThemeChoice(
    label: String,
    colors: VazieColors,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = VazieTheme.shapes.md
    val borderColor = selectionAccent(selected)
    val borderWidth = if (selected) SelectedBorder else VazieTheme.borders.hairline

    Column(
        modifier = modifier
            .width(CardWidth)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(VazieTheme.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScreenHeight)
                .clip(shape)
                .background(colors.background)
                .border(borderWidth, borderColor, shape)
                .padding(ScreenPadding),
        ) {
            MiniScreen(colors = colors)
            if (selected) {
                SelectedMark(
                    colors = colors,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        // Two lines, not an ellipsis: at large font scales long names would be cut.
        Text(
            text = label,
            style = VazieTheme.typography.labelSmall,
            color = selectionLabelColor(selected),
            maxLines = LabelLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The miniature itself: a surface on the background, a status line, two weights of text, a filled control.
 * Nothing here is interactive — it is a picture, and the whole card is the target. */
@Composable
private fun MiniScreen(colors: VazieColors) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiniGap),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MiniCorner))
                .background(colors.surface)
                .padding(MiniPadding),
            verticalArrangement = Arrangement.spacedBy(MiniGap),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MiniGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bar(color = colors.routeStart, width = DotSize, height = DotSize, corner = DotSize)
                Bar(color = colors.textPrimary, width = TitleWidth, height = TitleHeight)
            }
            Bar(color = colors.textSecondary, width = BodyWidth, height = BodyHeight)
        }
        Bar(
            color = colors.action,
            width = ActionWidth,
            height = ActionHeight,
            corner = MiniCorner,
        )
    }
}

@Composable
private fun Bar(
    color: Color,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp = BarCorner,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(color),
    )
}

/** The checkmark, drawn in the previewed palette's own `onPrimary` on its own `primary`. */
@Composable
private fun SelectedMark(colors: VazieColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MarkSize)
            .clip(RoundedCornerShape(MarkSize))
            .background(colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VazieIcons.Check,
            // The card carries the selected state in its semantics; a described icon inside it
            // would announce the selection a second time.
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.size(MarkIconSize),
        )
    }
}

private const val LabelLines = 2

private val CardWidth = 96.dp
private val ScreenHeight = 108.dp
private val ScreenPadding = 8.dp
private val SelectedBorder = 2.dp

private val MiniGap = 4.dp
private val MiniPadding = 6.dp
private val MiniCorner = 6.dp
private val BarCorner = 2.dp

private val DotSize = 6.dp
private val TitleWidth = 34.dp
private val TitleHeight = 6.dp
private val BodyWidth = 48.dp
private val BodyHeight = 4.dp
private val ActionWidth = 64.dp
private val ActionHeight = 14.dp

private val MarkSize = 18.dp
private val MarkIconSize = 12.dp

/** Selection: a chosen card is marked with `primary`. */
@Composable
internal fun selectionAccent(selected: Boolean): Color = when {
    !selected -> VazieTheme.colors.border
    else -> VazieTheme.colors.primary
}

/** The chosen option's name, coloured to match the border that marks it. */
@Composable
internal fun selectionLabelColor(selected: Boolean): Color = when {
    !selected -> VazieTheme.colors.textPrimary
    else -> VazieTheme.colors.primaryText
}
