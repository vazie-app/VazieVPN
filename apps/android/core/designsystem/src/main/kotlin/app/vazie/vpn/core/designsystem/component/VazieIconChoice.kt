package app.vazie.vpn.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A launcher icon shown as a launcher icon. */
@Composable
fun VazieIconChoice(
    label: String,
    plate: Brush,
    @DrawableRes foreground: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    foregroundTint: Color? = null,
    stateDescription: String? = null,
) {
    val shape = RoundedCornerShape(TileCorner)
    val borderColor = selectionAccent(selected)
    val borderWidth = if (selected) SelectedBorder else VazieTheme.borders.hairline

    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .then(
                // The receiver has to be explicit: inside `semantics` the bare name resolves to
                // this function's parameter, which is a `val`, and the assignment does not compile.
                stateDescription?.let { description ->
                    Modifier.semantics { this.stateDescription = description }
                } ?: Modifier,
            )
            .padding(VazieTheme.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(TileSize)
                .clip(shape)
                .background(plate, shape)
                .border(borderWidth, borderColor, shape),
        ) {
            Image(
                painter = painterResource(foreground),
                // Decorative: the label below names the icon, and the card's semantics carry the
                // selection.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Flat marks are one alpha mask inked at draw time; illustrated marks carry their own colour
                // (null).
                colorFilter = foregroundTint?.let(ColorFilter::tint),
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(MarkInset)
                        .size(MarkSize)
                        .clip(RoundedCornerShape(MarkSize))
                        .background(VazieTheme.colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VazieIcons.Check,
                        contentDescription = null,
                        tint = VazieTheme.colors.onPrimary,
                        modifier = Modifier.size(MarkIconSize),
                    )
                }
            }
        }
        Text(
            text = label,
            style = VazieTheme.typography.labelSmall,
            color = selectionLabelColor(selected),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val TileSize = 72.dp

/** A generous radius rather than a circle or a square. */
private val TileCorner = 20.dp
private val SelectedBorder = 2.dp
private val MarkInset = 4.dp
private val MarkSize = 20.dp
private val MarkIconSize = 13.dp
