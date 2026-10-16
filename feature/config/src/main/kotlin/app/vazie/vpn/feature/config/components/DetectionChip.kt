package app.vazie.vpn.feature.config.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieStatusChip
import app.vazie.vpn.core.designsystem.component.VazieStatusTone
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.config.DetectionUiState
import app.vazie.vpn.feature.config.R
import app.vazie.vpn.feature.config.presentation.detail

/** What Vazie made of the input: a labelled chip and, when there is one, a monospace detail. */
@Composable
internal fun DetectionChip(
    detection: DetectionUiState,
    modifier: Modifier = Modifier,
) {
    val label = when (detection) {
        DetectionUiState.AwaitingInput -> stringResource(R.string.config_detection_waiting)
        is DetectionUiState.Recognized -> detection.preview.protocolLabel
        is DetectionUiState.Unsupported -> stringResource(R.string.config_detection_unsupported)
        is DetectionUiState.Invalid -> stringResource(R.string.config_detection_invalid)
        is DetectionUiState.SubscriptionLoading,
        is DetectionUiState.Subscription -> stringResource(R.string.config_detection_subscription)

        is DetectionUiState.SubscriptionFailed -> stringResource(R.string.config_detection_invalid)
    }
    val detail = when (detection) {
        DetectionUiState.AwaitingInput -> null
        is DetectionUiState.Recognized -> listOfNotNull(
            detection.preview.securityLabel,
            detection.preview.transportLabel,
        ).joinToString(" · ").lowercase().ifEmpty { null }

        is DetectionUiState.Unsupported -> detection.feature.detail
        is DetectionUiState.Invalid -> null
        is DetectionUiState.SubscriptionLoading -> detection.host
        is DetectionUiState.Subscription -> detection.host
        is DetectionUiState.SubscriptionFailed -> detection.host
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
    ) {
        VazieStatusChip(label = label, tone = detection.tone())
        if (detail != null) {
            Text(
                text = detail,
                style = VazieTheme.typography.mono,
                color = VazieTheme.colors.textSecondary,
            )
        }
    }
}

internal fun DetectionUiState.tone(): VazieStatusTone = when (this) {
    DetectionUiState.AwaitingInput -> VazieStatusTone.Info
    is DetectionUiState.Recognized -> VazieStatusTone.Success
    is DetectionUiState.Unsupported -> VazieStatusTone.Warning
    is DetectionUiState.Invalid -> VazieStatusTone.Error
    is DetectionUiState.SubscriptionLoading -> VazieStatusTone.Info
    is DetectionUiState.Subscription -> VazieStatusTone.Success
    is DetectionUiState.SubscriptionFailed -> VazieStatusTone.Error
}
