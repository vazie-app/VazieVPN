package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** A short piece of machine-readable text, shown so a person can recognise the shape of one. */
@Composable
fun VazieExampleBlock(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = VazieTheme.typography.mono,
        color = VazieTheme.colors.technicalValue,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .fillMaxWidth()
            .clip(VazieTheme.shapes.sm)
            .background(VazieTheme.colors.technicalSurface)
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = VazieTheme.spacing.sm,
                vertical = VazieTheme.spacing.xs,
            )
            .semantics { contentDescription = description },
    )
}
