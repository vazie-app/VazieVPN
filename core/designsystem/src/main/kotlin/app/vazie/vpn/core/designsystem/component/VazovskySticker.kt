package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.R

/** A drawing of the person who writes Vazie. */
@Composable
fun VazovskySticker(modifier: Modifier = Modifier, height: Dp = VazovskyStickerDefaults.Height) {
    Image(
        painter = painterResource(R.drawable.vazovsky_sticker),
        contentDescription = stringResource(R.string.vazie_a11y_vazovsky_sticker),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}

/** The sticker's sizes. Feature modules take a height from here rather than writing a `dp` literal. */
object VazovskyStickerDefaults {
    /** Big enough to be a portrait, small enough not to be the screen: roughly a third of a phone's height,
     * so the reading on a screen people read stays above the fold. */
    val Height: Dp = 160.dp

    /** Beside a line of text in a card, where the sticker is a face rather than a portrait. */
    val CompactHeight: Dp = 56.dp
}
