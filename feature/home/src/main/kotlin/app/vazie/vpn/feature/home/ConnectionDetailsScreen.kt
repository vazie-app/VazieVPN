package app.vazie.vpn.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieDetailRow
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.component.VazieMessageState
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme

/** The technical details of the live session, as rows. Opened from the traffic line on Home. */
@Composable
fun ConnectionDetailsScreen(
    session: SessionUi?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing

    // Hoisted so the scaffold can be told when something has gone under the toolbar. The scaffold
    // cannot reach for it: the container is this screen's choice, and the state belongs to it.
    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(R.string.home_details_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.home_back),
            )
        },
    ) {
        if (session == null) {
            VazieMessageState(
                title = stringResource(R.string.home_details_empty_title),
                description = stringResource(R.string.home_details_empty_body),
            )
            return@VazieScreenScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenHorizontal)
                // The bottom-bar allowance used to hang off the mascot, which is gone; the
                // scrolling column carries it now, which is where it belonged anyway.
                .padding(top = spacing.sm)
                .vazieScrollEdgePadding(extra = spacing.sm),
        ) {
            StandardDetails(session = session)
        }
    }
}

@Composable
private fun StandardDetails(session: SessionUi, modifier: Modifier = Modifier) {
    val diagnostics = session.diagnostics
    VazieListCard(modifier = modifier) {
        VazieDetailRow(stringResource(R.string.home_diagnostics_engine), diagnostics.engine)
        VazieDivider()
        VazieDetailRow(stringResource(R.string.home_diagnostics_protocol), diagnostics.protocol)
        diagnostics.security?.let {
            VazieDivider()
            VazieDetailRow(stringResource(R.string.home_diagnostics_security), it)
        }
        diagnostics.transport?.let {
            VazieDivider()
            VazieDetailRow(stringResource(R.string.home_diagnostics_transport), it)
        }
        diagnostics.flow?.let {
            VazieDivider()
            VazieDetailRow(stringResource(R.string.home_diagnostics_flow), it)
        }
        VazieDivider()
        VazieDetailRow(stringResource(R.string.home_diagnostics_endpoint), diagnostics.endpointHost)
        VazieDivider()
        VazieDetailRow(stringResource(R.string.home_diagnostics_port), diagnostics.endpointPort.toString())
        VazieDivider()
        VazieDetailRow(
            label = stringResource(R.string.home_diagnostics_latency),
            // A dash rather than `0 ms` — nothing has measured it yet; see
            // `DiagnosticsUi.latencyMs`. `DiagnosticsUi.latencyMs`.
            value = diagnostics.latencyMs
                ?.let { stringResource(R.string.home_value_milliseconds, it) }
                ?: stringResource(R.string.home_value_unknown),
        )
        VazieDivider()
        VazieDetailRow(
            label = stringResource(R.string.home_diagnostics_traffic),
            value = stringResource(
                R.string.home_value_traffic,
                trafficText(session.rxBytes),
                trafficText(session.txBytes),
            ),
        )
        VazieDivider()
        VazieDetailRow(stringResource(R.string.home_diagnostics_dns), diagnostics.dnsMode)
        VazieDivider()
        VazieDetailRow(
            label = stringResource(R.string.home_diagnostics_ip),
            value = stringResource(
                R.string.home_value_traffic,
                stringResource(if (diagnostics.ipv4) R.string.home_value_on else R.string.home_value_off),
                stringResource(if (diagnostics.ipv6) R.string.home_value_on else R.string.home_value_off),
            ),
        )
    }
}

/** The pair of counters as words, or a dash when the device keeps none. See `trafficReading`. */
@Composable
private fun trafficText(bytes: Long): String {
    val reading = trafficReading(bytes) ?: return stringResource(R.string.home_value_unknown)
    return stringResource(R.string.home_value_amount, reading.amount, stringResource(reading.unitRes))
}
