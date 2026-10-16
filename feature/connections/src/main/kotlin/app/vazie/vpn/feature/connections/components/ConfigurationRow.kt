package app.vazie.vpn.feature.connections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.core.designsystem.component.VazieBadgeTone
import app.vazie.vpn.core.designsystem.component.VazieIconButton
import app.vazie.vpn.core.designsystem.theme.VazieTheme as Tokens
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.component.VazieMark
import app.vazie.vpn.core.designsystem.component.VazieSignal
import app.vazie.vpn.core.designsystem.component.VazieStatusChip
import app.vazie.vpn.core.designsystem.component.VazieStatusTone
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.connections.ConfigurationRowUi
import app.vazie.vpn.feature.connections.ConfigurationStatusUi
import app.vazie.vpn.feature.connections.LatencyUi
import app.vazie.vpn.feature.connections.R

/** A configuration in a list: mark, name, what it is, what it is doing. */
/** A configuration row: a mark, a name, a status chip and a way in. */
@Composable
internal fun ConfigurationRow(
    row: ConfigurationRowUi,
    onSelect: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the row offers its details screen. */
    showDetails: Boolean = true,
) {
    VazieListItem(
        title = row.name,
        modifier = modifier,
        subtitle = row.subtitle(),
        subtitleMaxLines = 1,
        subtitleAccessory = row.latency.accessory(),
        enabled = row.enabled,
        leadingContent = {
            VazieMark(tone = VazieBadgeTone.Muted) {
                Text(
                    text = row.mark,
                    style = VazieTheme.typography.monoSmall,
                    color = if (row.enabled) {
                        VazieTheme.colors.engineBadgeFg
                    } else {
                        VazieTheme.colors.textDisabled
                    },
                    textAlign = TextAlign.Center,
                )
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VazieStatusChip(
                    label = stringResource(row.status.labelRes()),
                    tone = row.status.tone(),
                )
                if (showDetails) {
                    VazieIconButton(
                        onClick = onOpenDetails,
                        contentDescription = stringResource(R.string.connections_details),
                        enabled = row.enabled,
                    ) {
                        Icon(imageVector = VazieIcons.ChevronRight, contentDescription = null)
                    }
                }
            }
        },
        onClick = onSelect,
    )
}

internal fun ConfigurationStatusUi.tone(): VazieStatusTone = when (this) {
    ConfigurationStatusUi.CONNECTED -> VazieStatusTone.Success
    ConfigurationStatusUi.SELECTED -> VazieStatusTone.Info
    ConfigurationStatusUi.IDLE -> VazieStatusTone.Neutral
    ConfigurationStatusUi.ERROR -> VazieStatusTone.Error
    ConfigurationStatusUi.UNAVAILABLE -> VazieStatusTone.Warning
}

internal fun ConfigurationStatusUi.labelRes(): Int = when (this) {
    ConfigurationStatusUi.CONNECTED -> R.string.connections_status_connected
    ConfigurationStatusUi.SELECTED -> R.string.connections_status_selected
    ConfigurationStatusUi.IDLE -> R.string.connections_status_idle
    ConfigurationStatusUi.ERROR -> R.string.connections_status_error
    ConfigurationStatusUi.UNAVAILABLE -> R.string.connections_status_unavailable
}

/** The line under the name. */
private fun LatencyUi.accessory(): (@Composable () -> Unit)? = when (this) {
    LatencyUi.Unknown -> null
    LatencyUi.NoAnswer -> {
        {
            VazieSignal(
                bars = 0,
                label = stringResource(R.string.connections_latency_no_answer),
                contentDescription = stringResource(R.string.connections_latency_no_answer_a11y),
            )
        }
    }
    is LatencyUi.Answered -> {
        {
            VazieSignal(
                bars = bars,
                label = stringResource(R.string.connections_latency_ms, millis),
                contentDescription = stringResource(R.string.connections_latency_a11y, millis),
            )
        }
    }
}

@Composable
private fun ConfigurationRowUi.subtitle(): String =
    listOfNotNull(protocolLabel, detailRes?.let { stringResource(it) }).joinToString(SEPARATOR)

private const val SEPARATOR = " · "
