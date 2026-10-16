package app.vazie.vpn.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.core.designsystem.component.VazieButton
import app.vazie.vpn.core.designsystem.component.VazieButtonVariant
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.settings.components.VazieDeveloperCard
import app.vazie.vpn.feature.settings.components.VazieProjectCard

/** Who made this, how to reach them, and how to help pay for it. */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onAction: (AboutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val links = state.links ?: bundledAboutLinks()
    val openLink: (String) -> Unit = { onAction(AboutAction.OpenLink(it)) }
    // Hoisted so the scaffold can be told when something has gone under the toolbar. The scaffold
    // cannot reach for it: the container is this screen's choice, and the state belongs to it.
    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(R.string.settings_about_title),
                onBack = { onAction(AboutAction.Back) },
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenHorizontal)
                .vazieScrollEdgePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            // The author's card stays even with no links: it says who makes the app.
            AboutSection(title = stringResource(R.string.settings_made_by))
            VazieDeveloperCard(links = links.author, onOpenLink = openLink)

            // The app's own page, not a link: it is here whatever the backend publishes.
            AboutSection(title = stringResource(R.string.settings_privacy_section))
            VazieListCard {
                VazieListItem(
                    title = stringResource(R.string.settings_privacy_row),
                    subtitle = stringResource(R.string.settings_privacy_row_body),
                    trailingContent = { Icon(imageVector = VazieIcons.ChevronRight, contentDescription = null) },
                    onClick = { onAction(AboutAction.OpenPrivacy) },
                    titleMaxLines = Int.MAX_VALUE,
                    subtitleMaxLines = Int.MAX_VALUE,
                )
            }

            // A card the backend left empty is not shown, heading and all.
            if (links.project.isNotEmpty()) {
                AboutSection(title = stringResource(R.string.settings_project_section))
                VazieProjectCard(links = links.project, onOpenLink = openLink)
            }

            if (links.security.isNotEmpty()) {
                AboutSection(title = stringResource(R.string.settings_security_section))
                VazieProjectCard(links = links.security, onOpenLink = openLink)
            }

            if (links.help.isNotEmpty()) {
                AboutSection(title = stringResource(R.string.settings_about_help_section))
                VazieProjectCard(links = links.help, onOpenLink = openLink)
            }

            if (links.support.isNotEmpty()) {
                AboutSection(title = stringResource(R.string.settings_support_section))
                links.support.forEach { link -> SupportCard(link = link, onOpenLink = openLink) }
            }

            if (links.feedbackLinks.isNotEmpty()) {
                AboutSection(title = stringResource(R.string.settings_feedback_section))
                VazieProjectCard(links = links.feedbackLinks, onOpenLink = openLink)
            }

            BuildSignature(about = state.about, modifier = Modifier.padding(top = spacing.xl))
        }
    }
}

/** A section heading on About. */
@Composable
private fun AboutSection(title: String, modifier: Modifier = Modifier) {
    VazieSectionHeader(title = title, modifier = modifier)
}

/** A way to help with the bills: a title, why, and a button. Without its own words the button says the title. */
@Composable
private fun SupportCard(link: AboutLinkUi, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = VazieTheme.spacing
    VazieCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = link.title,
                style = VazieTheme.typography.title,
                color = VazieTheme.colors.textPrimary,
            )
            link.subtitle?.let { body ->
                Text(
                    text = body,
                    style = VazieTheme.typography.body,
                    color = VazieTheme.colors.textSecondary,
                )
            }
            VazieButton(
                text = link.action ?: link.title,
                onClick = { onOpenLink(link.url) },
                variant = VazieButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BuildSignature(about: AboutUi, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            R.string.settings_about,
            about.versionName,
            about.versionCode,
            about.buildType,
        ),
        style = VazieTheme.typography.caption,
        color = VazieTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}