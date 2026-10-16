package app.vazie.vpn.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.tour.VazieTourTargetId
import app.vazie.vpn.core.designsystem.tour.vazieTourTarget
import app.vazie.vpn.core.model.SplitTunnel
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate

/** Settings, after the parts that were screens in disguise became screens. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = VazieTheme.typography.headline,
                    color = VazieTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenHorizontal)
                .vazieScrollEdgePadding(),
        ) {
            SettingsSection(title = stringResource(R.string.settings_look_and_feel), first = true) {
                SettingsRow(
                    title = stringResource(R.string.settings_appearance),
                    subtitle = stringResource(state.appearance.labelRes) + SUMMARY_SEPARATOR +
                        stringResource(state.appIcon.labelRes),
                    onClick = { onAction(SettingsAction.OpenAppearance) },
                    modifier = Modifier.vazieTourTarget(VazieTourTargetId.SETTINGS_APPEARANCE),
                )
            }

            SettingsSection(title = stringResource(R.string.settings_vpn_behavior)) {
                SettingsRow(
                    title = stringResource(R.string.settings_split_tunneling),
                    subtitle = splitTunnelStatus(state.splitTunnel),
                    onClick = { onAction(SettingsAction.OpenSplitTunnel) },
                    modifier = Modifier.vazieTourTarget(VazieTourTargetId.SETTINGS_SPLIT_TUNNEL),
                )
            }

            SettingsSection(title = stringResource(R.string.settings_help_section)) {
                // Above everything else: something missing is more common than something broken.
                SettingsRow(
                    title = stringResource(R.string.settings_guides_row),
                    subtitle = stringResource(R.string.settings_guides_row_body),
                    onClick = { onAction(SettingsAction.OpenGuides) },
                    modifier = Modifier.vazieTourTarget(VazieTourTargetId.SETTINGS_GUIDES),
                )
                VazieDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_faq_row),
                    subtitle = stringResource(R.string.settings_faq_row_body),
                    onClick = { onAction(SettingsAction.OpenFaq) },
                )
                VazieDivider()
                // The words are the app's; where the row leads is the backend's (`bug_report`).
                SettingsRow(
                    title = stringResource(R.string.settings_feedback_row),
                    subtitle = stringResource(R.string.settings_feedback_row_body),
                    onClick = { onAction(SettingsAction.ReportBug) },
                    modifier = Modifier.vazieTourTarget(VazieTourTargetId.SETTINGS_REPORT_BUG),
                )
                // No published address, no row: nothing here would open.
                if (state.canContactSupport) {
                    VazieDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_contact_support),
                        subtitle = stringResource(R.string.settings_contact_support_body),
                        onClick = { onAction(SettingsAction.ContactSupport) },
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_about_section)) {
                SettingsRow(
                    title = stringResource(R.string.settings_about_row),
                    subtitle = stringResource(R.string.settings_about_row_body),
                    onClick = { onAction(SettingsAction.OpenAbout) },
                )
                VazieDivider()
                // An action, not a place: no chevron. The subtitle appears only while a check runs.
                SettingsRow(
                    title = stringResource(R.string.settings_check_updates),
                    subtitle = if (state.checkingForUpdates) {
                        stringResource(R.string.settings_check_updates_checking)
                    } else {
                        null
                    },
                    // One check at a time; the row says it is under way.
                    onClick = { if (!state.checkingForUpdates) onAction(SettingsAction.CheckForUpdates) },
                    opensSomething = false,
                )
            }

            SettingsFooter(versionName = state.versionName)
        }
    }
}

/** A section: its heading, then one card holding its rows. */
@Composable
private fun SettingsSection(
    title: String,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The heading sits close to its card and a little further from the card above.
    VazieSectionHeader(
        title = title,
        modifier = if (first) Modifier else Modifier.padding(top = VazieTheme.spacing.xs),
    )
    VazieListCard(content = content)
}

/** One settings row. Title and subtitle wrap rather than cut, so a large system font loses nothing; the row is
 * never shorter than the design's list row, which is above the 48dp touch minimum. */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    opensSomething: Boolean = true,
) {
    VazieListItem(
        title = title,
        subtitle = subtitle,
        trailingContent = if (opensSomething) ({ ChevronIcon() }) else null,
        onClick = onClick,
        titleMaxLines = Int.MAX_VALUE,
        subtitleMaxLines = Int.MAX_VALUE,
        modifier = modifier,
    )
}

/** The app and its version, and who made it. Quiet, centred, and not a button. */
@Composable
private fun SettingsFooter(versionName: String, modifier: Modifier = Modifier) {
    val spacing = VazieTheme.spacing
    val name = stringResource(R.string.settings_footer_name)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.lg, bottom = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = if (versionName.isEmpty()) name else name + SUMMARY_SEPARATOR + versionName,
            style = VazieTheme.typography.caption,
            color = VazieTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.settings_footer_made_by),
            style = VazieTheme.typography.caption,
            color = VazieTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** What split tunnelling is doing now, in the row's subtitle. */
@Composable
private fun splitTunnelStatus(split: SplitTunnel): String = when {
    !split.isActive -> stringResource(R.string.split_tunnel_status_off)
    split.mode == SplitTunnel.Mode.EXCLUDE ->
        pluralStringResource(R.plurals.split_tunnel_status_exclude, split.packages.size, split.packages.size)
    else -> pluralStringResource(R.plurals.split_tunnel_status_include, split.packages.size, split.packages.size)
}

@Composable
private fun ChevronIcon() {
    Icon(imageVector = VazieIcons.ChevronRight, contentDescription = null)
}

internal val VazieAppIcon.labelRes: Int
    get() = when (this) {
        VazieAppIcon.ORBIT -> R.string.settings_app_icon_orbit
        VazieAppIcon.LIME -> R.string.settings_app_icon_lime
        VazieAppIcon.EMERALD -> R.string.settings_app_icon_emerald
        VazieAppIcon.MAGENTA -> R.string.settings_app_icon_magenta
        VazieAppIcon.COTTON_CANDY -> R.string.settings_app_icon_cotton_candy
        VazieAppIcon.CRIMSON -> R.string.settings_app_icon_crimson
        VazieAppIcon.GOLD -> R.string.settings_app_icon_gold
        VazieAppIcon.PEARL -> R.string.settings_app_icon_pearl
        VazieAppIcon.ONYX -> R.string.settings_app_icon_onyx
        VazieAppIcon.MONOCHROME -> R.string.settings_app_icon_monochrome
    }

internal val VazieAppIconPlate.labelRes: Int
    get() = when (this) {
        VazieAppIconPlate.DEEP -> R.string.settings_app_icon_plate_deep
        VazieAppIconPlate.LIGHT -> R.string.settings_app_icon_plate_light
        VazieAppIconPlate.INK -> R.string.settings_app_icon_plate_ink
        VazieAppIconPlate.FOREST -> R.string.settings_app_icon_plate_forest
        VazieAppIconPlate.ROSE -> R.string.settings_app_icon_plate_rose
        VazieAppIconPlate.AMBER -> R.string.settings_app_icon_plate_amber
        VazieAppIconPlate.MIST -> R.string.settings_app_icon_plate_mist
    }

internal val Appearance.labelRes: Int
    get() = when (this) {
        Appearance.NIGHT_INDIGO -> R.string.settings_appearance_night_indigo
        Appearance.MILK -> R.string.settings_appearance_milk
    }

private const val SUMMARY_SEPARATOR = " · "
