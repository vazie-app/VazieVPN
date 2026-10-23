package app.vazie.vpn.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.core.designsystem.component.VazieIconChoice
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieThemeChoice
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.designsystem.theme.colorsFor
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate

/** How Vazie looks: the palette, and the launcher icon on its plate. */
@Composable
fun AppearanceScreen(
    state: AppearanceUiState,
    appIconArt: (VazieAppIcon, VazieAppIconPlate) -> AppIconArt,
    resolvePlate: (VazieAppIcon, VazieAppIconPlate) -> VazieAppIconPlate,
    onAction: (AppearanceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val appearanceLabels = Appearance.entries.associateWith { stringResource(it.labelRes) }
    val iconLabels = VazieAppIcon.entries.associateWith { stringResource(it.labelRes) }
    val plateLabels = VazieAppIconPlate.entries.associateWith { stringResource(it.labelRes) }
    val selectedPlate = resolvePlate(state.appIcon, state.appIconPlate)

    val scroll = rememberScrollState()
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = scroll.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(R.string.settings_appearance_title),
                onBack = { onAction(AppearanceAction.Back) },
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .vazieScrollEdgePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            VazieSectionHeader(
                title = stringResource(R.string.settings_theme),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Appearance.entries.forEach { option ->
                    VazieThemeChoice(
                        label = appearanceLabels.getValue(option),
                        colors = colorsFor(option),
                        selected = option == state.appearance,
                        onSelect = { onAction(AppearanceAction.SelectAppearance(option)) },
                    )
                }
            }

            VazieSectionHeader(
                title = stringResource(R.string.settings_app_icon),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            IconChoiceFlow(
                options = VazieAppIcon.byField,
                columns = MarkColumns,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            ) { option ->
                // The chosen mark on the plate in force; every other mark as tapping it would set it, on its signature.
                val plate = if (option == state.appIcon) selectedPlate else option.signaturePlate
                val art = appIconArt(option, plate)
                VazieIconChoice(
                    label = iconLabels.getValue(option),
                    plate = art.plate,
                    foreground = art.foreground,
                    foregroundTint = art.foregroundTint,
                    selected = option == state.appIcon,
                    onSelect = { onAction(AppearanceAction.SelectAppIcon(option)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.settings_app_icon_body),
                style = VazieTheme.typography.caption,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            VazieSectionHeader(
                title = stringResource(R.string.settings_app_icon_plate),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            IconChoiceFlow(
                options = state.appIcon.plates,
                columns = MarkColumns,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            ) { option ->
                val art = appIconArt(state.appIcon, option)
                VazieIconChoice(
                    label = plateLabels.getValue(option),
                    plate = art.plate,
                    foreground = art.foreground,
                    foregroundTint = art.foregroundTint,
                    selected = option == selectedPlate,
                    onSelect = { onAction(AppearanceAction.SelectAppIconPlate(option)) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Themed icons repaint every icon in one colour; only a flat or pale mark survives that.
            Text(
                text = stringResource(R.string.settings_app_icon_themed_hint),
                style = VazieTheme.typography.caption,
                color = VazieTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
    }
}

/** A grid of choices, [columns] to a row, announced as one selectable group. */
@Composable
private fun <T> IconChoiceFlow(
    options: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    choice: @Composable FlowRowScope.(T) -> Unit,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(VazieTheme.spacing.xs),
        maxItemsInEachRow = columns,
    ) {
        options.forEach { option -> choice(option) }
    }
}

private const val MarkColumns = 4
