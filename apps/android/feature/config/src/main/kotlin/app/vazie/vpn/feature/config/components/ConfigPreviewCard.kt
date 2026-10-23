package app.vazie.vpn.feature.config.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieDetailRow
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.feature.config.ConfigPreviewUi
import app.vazie.vpn.feature.config.R

/** The card the design calls "Configuration preview": safe fields only. */
@Composable
internal fun ConfigPreviewCard(
    preview: ConfigPreviewUi,
    name: String,
    modifier: Modifier = Modifier,
) {
    VazieCard(modifier = modifier, contentPadding = PaddingValues()) {
        VazieDetailRow(
            label = stringResource(R.string.config_field_protocol),
            value = listOfNotNull(preview.protocolLabel, preview.securityLabel).joinToString(" · "),
        )
        preview.transportLabel?.let {
            VazieDivider()
            VazieDetailRow(label = stringResource(R.string.config_field_transport), value = it)
        }
        VazieDivider()
        VazieDetailRow(label = stringResource(R.string.config_field_server), value = preview.server)
        preview.serverName?.let {
            VazieDivider()
            VazieDetailRow(label = stringResource(R.string.config_field_sni), value = it)
        }
        preview.fingerprint?.let {
            VazieDivider()
            VazieDetailRow(label = stringResource(R.string.config_field_fingerprint), value = it)
        }
        preview.flow?.let {
            VazieDivider()
            VazieDetailRow(label = stringResource(R.string.config_field_flow), value = it)
        }
        VazieDivider()
        VazieDetailRow(
            label = stringResource(R.string.config_field_name),
            value = name,
            technical = false,
        )
    }
}
