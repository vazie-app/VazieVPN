package app.vazie.vpn.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import app.vazie.vpn.R
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.widget.WidgetConfigurationUi
import app.vazie.vpn.widget.WidgetSubjectUi
import app.vazie.vpn.widget.presentation.WidgetAction
import app.vazie.vpn.widget.presentation.WidgetArrangement
import app.vazie.vpn.widget.presentation.WidgetActionStyle
import app.vazie.vpn.widget.presentation.WidgetLayout
import app.vazie.vpn.widget.presentation.WidgetPhase
import app.vazie.vpn.widget.presentation.WidgetPresentation
import app.vazie.vpn.widget.presentation.WidgetTone
import app.vazie.vpn.widget.toGlanceAction

/** The pieces both widgets are built from: the canvas, the phase rail, the text block, the control and the
 * configuration chip. */
@Composable
internal fun WidgetCanvas(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    val dimens = VazieGlanceTheme.dimens
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VazieGlanceTheme.colors.background)
            .cornerRadius(dimens.widgetCorner),
        contentAlignment = Alignment.TopStart,
        content = content,
    )
}

/** Three segments, some of them lit. */
@Composable
internal fun WidgetPhaseRail(
    phase: WidgetPhase,
    tone: WidgetTone,
    modifier: GlanceModifier = GlanceModifier,
) {
    val dimens = VazieGlanceTheme.dimens
    val lit = tone.rail()
    val unlit = VazieGlanceTheme.colors.border
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        phase.segments.forEachIndexed { index, isLit ->
            if (index > 0) Spacer(GlanceModifier.width(dimens.railGap))
            Box(
                modifier = GlanceModifier
                    .width(dimens.railSegment)
                    .height(dimens.railHeight)
                    .cornerRadius(dimens.railHeight)
                    .background(if (isLit) lit else unlit),
                content = {},
            )
        }
    }
}

/** The rail, the state word, and - when there is room - the configuration and one reading. */
@Composable
internal fun WidgetStateBlock(
    presentation: WidgetPresentation,
    layout: WidgetLayout,
    subject: WidgetSubjectUi?,
    reading: String?,
    /** What the reading line is, when it is a list of Vazie locations rather than a sentence. */
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    val dimens = VazieGlanceTheme.dimens
    val typography = VazieGlanceTheme.typography
    val colors = VazieGlanceTheme.colors
    val context = LocalContext.current
    val status = context.getString(presentation.statusRes)
    val shortStatus = context.getString(presentation.shortStatusRes)
    val server = subject?.name
    // Spoken in full: a screen reader has no width limit.
    val spoken = when (subject) {
        null -> status
        else -> context.getString(R.string.widget_a11y_status_server, status, subject.name)
    }

    // The text opens Vazie; only the labelled control connects or disconnects.
    Column(
        modifier = modifier
            .clickable(onClick)
            .semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.Start,
    ) {
        WidgetPhaseRail(phase = presentation.phase, tone = presentation.tone)
        Spacer(GlanceModifier.height(dimens.blockGap))
        Text(
            // Narrow cells get the short state word; the width decides, and wrapping to a second line was
            // worse.
            text = if (layout.spellsStateInFull) status else shortStatus,
            style = (if (layout.statesInFull) typography.state else typography.stateCompact)
                .copy(color = presentation.tone.text()),
            maxLines = SINGLE_LINE,
        )
        if (layout.showsServer && server != null) {
            Spacer(GlanceModifier.height(dimens.lineGap))
            Text(
                text = server,
                style = typography.server.copy(
                    color = colors.textSecondary,
                ),
                maxLines = SINGLE_LINE,
            )
        }
        if (layout.showsReading && reading != null) {
            Spacer(GlanceModifier.height(dimens.lineGap))
            Text(
                text = reading,
                style = typography.reading.copy(color = colors.textSecondary),
                maxLines = SINGLE_LINE,
            )
        }
    }
}

/** The control. */
@Composable
internal fun WidgetActionButton(
    presentation: WidgetPresentation,
    onClick: Action,
    fillsWidth: Boolean,
    /** A fixed outer width, for the arrangement where the control shares a row with text. */
    width: Dp? = null,
    modifier: GlanceModifier = GlanceModifier,
) {
    val dimens = VazieGlanceTheme.dimens
    val colors = VazieGlanceTheme.colors
    val label = LocalContext.current.getString(presentation.actionRes)
    val filled = presentation.actionStyle == WidgetActionStyle.FILLED

    // `fillsWidth` is passed, not inferred: in RemoteViews a filling child can widen a wrapping parent.
    val sized = when {
        fillsWidth -> modifier.fillMaxWidth()
        width != null -> modifier.width(width)
        else -> modifier.wrapContentWidth()
    }
    val frame = sized
        .height(dimens.minTouchTarget)
        .cornerRadius(dimens.actionCorner)
        .clickable(onClick)
        .semantics { contentDescription = label }

    if (filled) {
        Box(
            modifier = frame.background(colors.primary).padding(horizontal = dimens.itemGap),
            contentAlignment = Alignment.Center,
        ) {
            WidgetActionLabel(label = label, color = colors.onPrimary)
        }
    } else {
        val fill = when {
            fillsWidth || width != null -> GlanceModifier.fillMaxWidth()
            else -> GlanceModifier.wrapContentWidth()
        }
        Box(
            modifier = frame.background(colors.border).padding(dimens.actionBorder),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = fill
                    .fillMaxHeight()
                    .cornerRadius(dimens.actionCorner)
                    .background(colors.surfaceMuted)
                    .padding(horizontal = dimens.itemGap),
                contentAlignment = Alignment.Center,
            ) {
                WidgetActionLabel(label = label, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun WidgetActionLabel(label: String, color: ColorProvider) {
    Text(
        text = label,
        style = VazieGlanceTheme.typography.action.copy(color = color, textAlign = TextAlign.Center),
        maxLines = SINGLE_LINE,
    )
}

/** One alternative configuration, as a chip that switches to it. */
@Composable
internal fun WidgetConfigurationChip(
    configuration: WidgetConfigurationUi,
    modifier: GlanceModifier = GlanceModifier,
) {
    val dimens = VazieGlanceTheme.dimens
    val colors = VazieGlanceTheme.colors
    val description = LocalContext.current.getString(R.string.widget_chip_a11y, configuration.name)
    // Outlined, on the canvas colour - so that a chip is quieter than the control above it in both palettes
    // rather than only in one.
    Box(
        modifier = modifier
            .wrapContentWidth()
            .height(dimens.rowHeight)
            .cornerRadius(dimens.rowCorner)
            .background(colors.border)
            .clickable(WidgetAction.Select(configuration.id).toGlanceAction())
            .padding(dimens.actionBorder)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .wrapContentWidth()
                .fillMaxHeight()
                .cornerRadius(dimens.rowCorner)
                .background(colors.background)
                .padding(horizontal = dimens.itemGap),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = configuration.name,
                style = VazieGlanceTheme.typography.body.copy(color = colors.textPrimary),
                maxLines = SINGLE_LINE,
            )
        }
    }
}

/** A row of chips, or nothing at all when the layout has no room for one. */
@Composable
internal fun WidgetConfigurationChips(
    shortcuts: List<WidgetConfigurationUi>,
    modifier: GlanceModifier = GlanceModifier,
) {
    val dimens = VazieGlanceTheme.dimens
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        shortcuts.forEachIndexed { index, shortcut ->
            if (index > 0) Spacer(GlanceModifier.width(dimens.itemGap))
            WidgetConfigurationChip(configuration = shortcut)
        }
    }
}

/** The state word's colour. */
@Composable
internal fun WidgetTone.text(): ColorProvider = when (this) {
    WidgetTone.NEUTRAL -> VazieGlanceTheme.colors.textPrimary
    WidgetTone.INFO -> VazieGlanceTheme.colors.primary
    WidgetTone.WARNING -> VazieGlanceTheme.colors.warning
    WidgetTone.SUCCESS -> VazieGlanceTheme.colors.success
    WidgetTone.ERROR -> VazieGlanceTheme.colors.error
}

@Composable
internal fun WidgetTone.rail(): ColorProvider = when (this) {
    WidgetTone.NEUTRAL -> VazieGlanceTheme.colors.statusDotIdle
    else -> text()
}

private const val SINGLE_LINE = 1

private const val SENTENCE_BREAK = ". "



