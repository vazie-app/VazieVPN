package app.vazie.vpn.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.R
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieTourCallout
import app.vazie.vpn.core.designsystem.component.VazieTourCalloutLabels
import app.vazie.vpn.core.designsystem.component.VazieTourOverlay
import app.vazie.vpn.core.designsystem.theme.VazieGuideDefaults
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.tour.VazieTourTargets
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

/** The tour, drawn over the live application. */
@Composable
fun VazieTourHost(
    targets: VazieTourTargets,
    onFinished: () -> Unit,
    onOpenGuides: () -> Unit,
    steps: List<VazieTourStep> = vazieTourSequence(),
    completion: Boolean = true,
) {
    val sequence = remember(steps) { steps }
    val ready = rememberTourReadiness(targets = targets, sequence = sequence)

    // Nothing is drawn until the screen underneath has something to point at; see `rememberTourReadiness`.
    when (ready) {
        TourReadiness.Waiting -> return
        TourReadiness.NothingToShow -> {
            // A tour with nothing to point at ends, rather than sitting invisible forever.
            LaunchedEffect(Unit) { onFinished() }
            return
        }

        TourReadiness.Ready -> Unit
    }

    val controller = rememberVazieTourController(
        targets = targets,
        sequence = sequence,
        onDone = onFinished,
    )

    when (val phase = controller.phase) {
        is VazieTourPhase.Step -> {
            // A target further down a scrolling screen is brought on screen before it is pointed at.
            LaunchedEffect(phase.step.target) { targets.bringIntoView(phase.step.target) }
            VazieTourOverlay(target = targets.bounds(phase.step.target)) {
            VazieTourCallout(
                title = stringResource(phase.step.titleRes),
                body = stringResource(phase.step.bodyRes),
                labels = tourLabels(number = phase.number, of = phase.of),
                onNext = controller::next,
                onBack = if (controller.canGoBack) controller::back else null,
                onSkip = controller::finish,
            )
            }
        }

        // Only a sequence with a completion card shows one; a one-step coach mark just ends.
        VazieTourPhase.Completion -> if (!completion) {
            LaunchedEffect(Unit) { controller.finish() }
        } else TourCompletionCard(
            onGuides = {
                controller.finish()
                onOpenGuides()
            },
            onDismiss = controller::finish,
            onBack = controller::back,
        )

        VazieTourPhase.Finished -> Unit
    }
}

@Composable
private fun tourLabels(number: Int, of: Int) = VazieTourCalloutLabels(
    next = stringResource(R.string.tour_next),
    back = stringResource(R.string.tour_back),
    skip = stringResource(R.string.tour_skip),
    progress = stringResource(R.string.tour_progress, number, of),
    progressSpoken = stringResource(R.string.tour_progress_a11y, number, of),
)

/** The card after the last step, which is not a step. */
@Composable
private fun TourCompletionCard(
    onGuides: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = VazieTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VazieTheme.colors.scrim.copy(alpha = VazieGuideDefaults.SCRIM_ALPHA))
            // Modal for the same reason every step is. See `VazieTourOverlay`. `VazieTourOverlay`.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        VazieCard(modifier = Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.tour_done_title),
                    style = VazieTheme.typography.title,
                    color = VazieTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.tour_done_body),
                    style = VazieTheme.typography.body,
                    color = VazieTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                VazieButton(
                    text = stringResource(R.string.tour_done_guides),
                    onClick = onGuides,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                )
                VazieButton(
                    text = stringResource(R.string.tour_done_dismiss),
                    onClick = onDismiss,
                    variant = VazieButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                VazieButton(
                    text = stringResource(R.string.tour_back),
                    onClick = onBack,
                    variant = VazieButtonVariant.Text,
                )
            }
        }
    }
}

/** Whether the screen underneath has produced anything to point at yet. */
internal enum class TourReadiness { Waiting, Ready, NothingToShow }

/** Waits for the tour's own targets, and never for a clock. */
@Composable
private fun rememberTourReadiness(
    targets: VazieTourTargets,
    sequence: List<VazieTourStep>,
): TourReadiness {
    var readiness by remember(targets, sequence) { mutableStateOf(TourReadiness.Waiting) }

    LaunchedEffect(targets, sequence) {
        val wanted = sequence.map { it.target }.toSet()
        snapshotFlow { targets.registered() intersect wanted }
            .distinctUntilChanged()
            .collectLatest { present ->
                // `collectLatest` restarts on each registration, so this runs once the anchors stop changing.
                if (present.isEmpty()) {
                    // A replay starts during the navigation transition, so an empty set gets a grace period.
                    delay(EmptyGraceMillis)
                    readiness = TourReadiness.NothingToShow
                } else {
                    withFrameNanos { }
                    readiness = TourReadiness.Ready
                }
            }
    }
    return readiness
}

/** Longer than a navigation transition; short enough not to hold a modal layer over nothing. */
private const val EmptyGraceMillis = 1_000L
