package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vazie.vpn.core.designsystem.theme.VazieTheme

private val RingSize = 28.dp
private val RingStroke = 3.dp
private val TrackHeight = 6.dp

@Composable
fun VazieCircularProgress(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(RingSize),
        color = VazieTheme.colors.primary,
        trackColor = VazieTheme.colors.border,
        strokeWidth = RingStroke,
    )
}

/** Pass [progress] for a known amount of work, leave it null while the total is unknown. */
@Composable
fun VazieLinearProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val shape = VazieTheme.shapes.pill
    val color = VazieTheme.colors.primary
    val track = VazieTheme.colors.border
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(TrackHeight).clip(shape),
            color = color,
            trackColor = track,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth().height(TrackHeight).clip(shape),
            color = color,
            trackColor = track,
            drawStopIndicator = {},
        )
    }
}

/** The pill the design uses for transient messages, with an optional action. */
@Composable
fun VazieSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = VazieTheme.colors
    Row(
        modifier = modifier
            .clip(VazieTheme.shapes.md)
            .background(colors.inverseSurface)
            .padding(horizontal = VazieTheme.spacing.md, vertical = VazieTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.md),
    ) {
        Text(
            text = message,
            style = VazieTheme.typography.body,
            color = colors.onInverseSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onAction != null) {
            VazieFieldAction(label = actionLabel, onClick = onAction, color = colors.inverseAccent)
        }
    }
}

/** Centred mark, title and explanation — the shape the design uses for both empty and failed states. */
@Composable
fun VazieMessageState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    mark: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VazieTheme.spacing.lg, vertical = VazieTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
    ) {
        if (mark != null) {
            Box(Modifier.padding(bottom = VazieTheme.spacing.xs)) { mark() }
        }
        Text(
            text = title,
            style = VazieTheme.typography.title,
            color = VazieTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Box(Modifier.padding(top = VazieTheme.spacing.xs)) { action() }
        }
    }
}
