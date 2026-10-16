package app.vazie.vpn.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.vazie.vpn.core.designsystem.component.VazieDivider
import app.vazie.vpn.core.designsystem.component.VazieListCard
import app.vazie.vpn.core.designsystem.component.VazieListItem
import app.vazie.vpn.core.designsystem.component.VazieScreenScaffold
import app.vazie.vpn.core.designsystem.component.VazieSectionHeader
import app.vazie.vpn.core.designsystem.component.VazieSwitch
import app.vazie.vpn.core.designsystem.component.VazieTextField
import app.vazie.vpn.core.designsystem.component.VazieToolbar
import app.vazie.vpn.core.designsystem.component.scrolledUnderToolbar
import app.vazie.vpn.core.designsystem.component.vazieScrollEdgePadding
import app.vazie.vpn.core.designsystem.icon.VazieIcons
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.SplitTunnel

/** Split tunnelling: whether every app uses the VPN, all but some, or only some, and which. */
@Composable
fun SplitTunnelScreen(
    state: SplitTunnelUiState,
    onAction: (SplitTunnelAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VazieTheme.spacing
    val list = rememberLazyListState()
    val iconSize = VazieTheme.spacing.xxl
    val iconPx = with(LocalDensity.current) { iconSize.roundToPx() }
    VazieScreenScaffold(
        modifier = modifier,
        contentScrolledUnderToolbar = list.scrolledUnderToolbar(),
        topBar = {
            VazieToolbar(
                title = stringResource(R.string.split_tunnel_title),
                onBack = { onAction(SplitTunnelAction.Back) },
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth().weight(1f).vazieScrollEdgePadding(),
            contentPadding = PaddingValues(horizontal = spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "note") {
                // First and set apart: a change here does nothing to a tunnel that is already up.
                Text(
                    text = stringResource(R.string.split_tunnel_note),
                    style = VazieTheme.typography.label,
                    color = VazieTheme.colors.onAccentContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VazieTheme.shapes.md)
                        .background(VazieTheme.colors.accentContainer)
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }
            item(key = "modes") {
                VazieListCard {
                    SplitTunnel.Mode.entries.forEachIndexed { index, mode ->
                        if (index > 0) VazieDivider()
                        ModeRow(mode = mode, selected = state.split.mode == mode) {
                            onAction(SplitTunnelAction.SelectMode(mode))
                        }
                    }
                }
            }
            if (state.split.mode != SplitTunnel.Mode.OFF) {
                item(key = "header") {
                    VazieSectionHeader(
                        title = pluralStringResource(
                            R.plurals.split_tunnel_selected,
                            state.split.packages.size,
                            state.split.packages.size,
                        ),
                    )
                }
                item(key = "search") {
                    VazieTextField(
                        value = state.query,
                        onValueChange = { onAction(SplitTunnelAction.QueryChanged(it)) },
                        placeholder = stringResource(R.string.split_tunnel_search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val apps = state.apps
                if (apps == null) {
                    item(key = "loading") { Hint(stringResource(R.string.split_tunnel_loading)) }
                } else if (state.visibleApps.isEmpty()) {
                    item(key = "empty") { Hint(stringResource(R.string.split_tunnel_nothing_found)) }
                } else {
                    items(state.visibleApps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in state.split.packages,
                            iconPx = iconPx,
                            onToggle = { onAction(SplitTunnelAction.ToggleApp(app.packageName)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mode: SplitTunnel.Mode, selected: Boolean, onSelect: () -> Unit) {
    val (title, subtitle) = when (mode) {
        SplitTunnel.Mode.OFF -> R.string.split_tunnel_mode_off to R.string.split_tunnel_mode_off_body
        SplitTunnel.Mode.EXCLUDE -> R.string.split_tunnel_mode_exclude to R.string.split_tunnel_mode_exclude_body
        SplitTunnel.Mode.INCLUDE -> R.string.split_tunnel_mode_include to R.string.split_tunnel_mode_include_body
    }
    VazieListItem(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        subtitleMaxLines = Int.MAX_VALUE,
        trailingContent = if (selected) {
            { Icon(imageVector = VazieIcons.Check, contentDescription = null, tint = VazieTheme.colors.primary) }
        } else {
            null
        },
        onClick = onSelect,
        modifier = Modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    )
}

@Composable
private fun AppRow(app: LaunchableApp, checked: Boolean, iconPx: Int, onToggle: () -> Unit) {
    val icon by rememberAppIcon(app.packageName, iconPx)
    VazieListCard {
        VazieListItem(
            title = app.label,
            leadingContent = {
                Box(modifier = Modifier.size(VazieTheme.spacing.xxl)) {
                    icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(VazieTheme.spacing.xxl)) }
                }
            },
            trailingContent = { VazieSwitch(checked = checked, onCheckedChange = null) },
            onClick = onToggle,
            modifier = Modifier.semantics {
                role = Role.Switch
                selected = checked
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = VazieTheme.spacing.md)) {
        Text(text = text, style = VazieTheme.typography.body, color = VazieTheme.colors.textSecondary)
    }
}
