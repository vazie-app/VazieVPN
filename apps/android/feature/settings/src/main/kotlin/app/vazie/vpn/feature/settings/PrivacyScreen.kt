package app.vazie.vpn.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieCard
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.feature.settings.components.PrivacyBullet

/** What Vazie holds, where it is, and what it never asks for. */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(R.string.settings_privacy_title),
                onBack = onBack,
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
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.settings_privacy_body),
                style = VazieTheme.typography.body,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = spacing.xs),
            )

            Section(
                title = R.string.privacy_stores_title,
                paragraphs = listOf(
                    R.string.privacy_stores_configs,
                    R.string.privacy_stores_choices,
                    R.string.privacy_stores_usage,
                ),
            )
            Section(
                title = R.string.privacy_where_title,
                paragraphs = listOf(
                    R.string.privacy_where_encrypted,
                    R.string.privacy_where_plain,
                    R.string.privacy_where_backup,
                ),
            )

            NeverSection()

            Section(
                title = R.string.privacy_surfaces_title,
                paragraphs = listOf(
                    R.string.privacy_surfaces_widgets,
                    R.string.privacy_surfaces_shortcuts,
                    R.string.privacy_surfaces_tile,
                ),
            )
            Section(
                title = R.string.privacy_logs_title,
                paragraphs = listOf(R.string.privacy_logs_body),
            )
            Section(
                title = R.string.privacy_vpn_title,
                paragraphs = listOf(R.string.privacy_vpn_body, R.string.privacy_vpn_dns),
            )
            Section(
                title = R.string.privacy_own_title,
                paragraphs = listOf(R.string.privacy_own_body),
            )
            Section(
                title = R.string.privacy_export_title,
                paragraphs = listOf(R.string.privacy_export_body),
            )
        }
    }
}

/** A heading and its paragraphs, on a card. */
@Composable
private fun Section(
    @StringRes title: Int,
    paragraphs: List<Int>,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    VazieSectionHeader(title = stringResource(title), modifier = modifier)
    VazieCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) { Paragraphs(paragraphs) }
    }
}

@Composable
private fun NeverSection(modifier: Modifier = Modifier) {
    val spacing = VazieTheme.spacing
    val bullets: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            PrivacyBullet(stringResource(R.string.privacy_never_analytics), excluded = true)
            PrivacyBullet(stringResource(R.string.privacy_never_account), excluded = true)
            PrivacyBullet(stringResource(R.string.privacy_never_browsing), excluded = true)
        }
    }
    VazieSectionHeader(title = stringResource(R.string.privacy_never_title), modifier = modifier)
    VazieCard(modifier = Modifier.fillMaxWidth()) { bullets() }
}

@Composable
private fun Paragraphs(paragraphs: List<Int>) {
    paragraphs.forEach { paragraph ->
        Text(
            text = stringResource(paragraph),
            style = VazieTheme.typography.body,
            color = VazieTheme.colors.textSecondary,
        )
    }
}
