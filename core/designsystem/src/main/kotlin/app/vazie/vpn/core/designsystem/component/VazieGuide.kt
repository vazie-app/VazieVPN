package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The one thing a guide can do besides be read. */
@Immutable
data class VazieGuideAction(val label: String, val onClick: () -> Unit)

/** One thing Vazie can do that you would not find on your own. */
@Composable
fun VazieGuide(
    title: String,
    body: String,
    stepsTitle: String,
    steps: List<String>,
    modifier: Modifier = Modifier,
    action: VazieGuideAction? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = VazieTheme.typography.title,
            color = VazieTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = VazieTheme.typography.body,
            color = VazieTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            VazieButton(
                text = action.label,
                onClick = action.onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = VazieTheme.spacing.xs),
            )
        }
        // Left-aligned, so numbered steps line up even when one wraps.
        Steps(
            stepsTitle = stepsTitle,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = VazieTheme.spacing.xs),
        )
    }
}

/** Numbered here rather than in the strings. A translator who renumbers, or who drops a step, cannot make the
 * list disagree with itself if the list never carried its own numbers. */
@Composable
private fun Steps(
    stepsTitle: String,
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xxs),
    ) {
        Text(
            text = stepsTitle,
            style = VazieTheme.typography.labelSmall,
            color = VazieTheme.colors.textSecondary,
        )
        steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
            )
        }
    }
}
