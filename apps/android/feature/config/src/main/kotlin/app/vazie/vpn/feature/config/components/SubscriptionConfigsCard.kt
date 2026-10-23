package app.vazie.vpn.feature.config.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.feature.config.ConfigPreviewUi
import kotlinx.collections.immutable.ImmutableList

/** A subscription's configurations, one row each: the name its link gave it, and what it is. Safe fields
 * only, as in `ConfigPreviewCard`. */
@Composable
internal fun SubscriptionConfigsCard(
    configs: ImmutableList<ConfigPreviewUi>,
    modifier: Modifier = Modifier,
) {
    VazieCard(modifier = modifier, contentPadding = PaddingValues()) {
        configs.forEachIndexed { index, config ->
            if (index > 0) VazieDivider()
            VazieListItem(
                title = config.suggestedName,
                subtitle = listOfNotNull(
                    config.protocolLabel,
                    config.securityLabel,
                    config.transportLabel,
                    config.server,
                ).joinToString(" · "),
            )
        }
    }
}
