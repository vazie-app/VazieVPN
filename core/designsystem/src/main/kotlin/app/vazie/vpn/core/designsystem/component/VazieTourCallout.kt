package app.vazie.vpn.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** One step's worth of words: what this control is, where you are in the sequence, and the way on. */
@Immutable
data class VazieTourCalloutLabels(
    val next: String,
    val back: String,
    val skip: String,
    /** `2 / 4` as drawn. */
    val progress: String,
    /** `Step 2 of 4` as spoken. */
    val progressSpoken: String,
)

@Composable
fun VazieTourCallout(
    title: String,
    body: String,
    labels: VazieTourCalloutLabels,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val spacing = VazieTheme.spacing
    val colors = VazieTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            // One traversal group, announced politely, so a step change is spoken as one step.
            .semantics {
                isTraversalGroup = true
                liveRegion = LiveRegionMode.Polite
            }
            .clip(VazieTheme.shapes.lg)
            .background(colors.elevated)
            .then(
                Modifier.border(VazieTheme.borders.hairline, colors.border, VazieTheme.shapes.lg),
            )
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // The words scroll; the way out does not.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = VazieTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = body,
                    style = VazieTheme.typography.body,
                    color = VazieTheme.colors.textSecondary,
                )
            }
        }

        // Progress and the early way out share a line, so the row under them only ever holds Back
        // and Next and has the width for both on one line each.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = labels.progress,
                style = VazieTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                // `2 / 4` is punctuation when it is read aloud. The spoken form is a sentence.
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = labels.progressSpoken },
            )
            VazieButton(text = labels.skip, onClick = onSkip, variant = VazieButtonVariant.Text)
        }

        TourActions(
            labels = labels,
            onNext = onNext,
            onBack = onBack,
        )
    }
}

/** Back and Next, at the end of the callout. Skip lives on the progress line above, apart from Next on
 * purpose: the two ways out of the tour should not sit side by side. */
@Composable
private fun TourActions(
    labels: VazieTourCalloutLabels,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs, Alignment.End),
    ) {
        // Absent rather than disabled on the first step: there is no step before it, so there is
        // nothing to grey out. A disabled control claims something exists that does not.
        if (onBack != null) {
            VazieButton(text = labels.back, onClick = onBack, variant = VazieButtonVariant.Text)
        }
        VazieButton(text = labels.next, onClick = onNext)
    }
}
