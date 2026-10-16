package app.vazie.vpn.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.component.VazovskySticker
import app.vazie.vpn.core.designsystem.component.VazovskyStickerDefaults
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.settings.AboutLinkUi
import app.vazie.vpn.feature.settings.R
import kotlinx.collections.immutable.ImmutableList

/** Who makes Vazie, where to reach them, and where the rest of their work is: the [links] the backend put in the
 * author's card. */
@Composable
internal fun VazieDeveloperCard(
    links: ImmutableList<AboutLinkUi>,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    VazieCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardPadding)
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VazovskySticker(height = VazovskyStickerDefaults.CompactHeight)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.settings_made_by_title),
                    style = VazieTheme.typography.title,
                    color = VazieTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.settings_made_by_body),
                    style = VazieTheme.typography.caption,
                    color = VazieTheme.colors.textSecondary,
                )
            }
        }
        links.forEach { link ->
            VazieDivider()
            LinkRow(link = link, onOpenLink = onOpenLink)
        }
    }
}

/** Vazie itself: the channel, the support address, the source - whatever the backend put in the project's card.
 * Where a question about the app or a payment goes, kept apart from the author's own contacts above it. */
@Composable
internal fun VazieProjectCard(
    links: ImmutableList<AboutLinkUi>,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    VazieCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues()) {
        links.forEachIndexed { index, link ->
            if (index > 0) VazieDivider()
            LinkRow(link = link, onOpenLink = onOpenLink)
        }
    }
}

/** A link as a row; with a button text from the admin panel ([AboutLinkUi.action]) it also gets a button under it,
 * which opens the same address. Any link may have one. */
@Composable
private fun LinkRow(link: AboutLinkUi, onOpenLink: (String) -> Unit) {
    VazieListItem(
        title = link.title,
        subtitle = link.subtitle,
        trailingContent = {
            Icon(imageVector = VazieIcons.ChevronRight, contentDescription = null)
        },
        onClick = { onOpenLink(link.url) },
    )
    link.action?.let { action ->
        val spacing = VazieTheme.spacing
        VazieButton(
            text = action,
            onClick = { onOpenLink(link.url) },
            variant = VazieButtonVariant.Secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.cardPadding, end = spacing.cardPadding, bottom = spacing.cardPadding),
        )
    }
}
