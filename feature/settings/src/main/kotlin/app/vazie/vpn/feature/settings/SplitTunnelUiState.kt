package app.vazie.vpn.feature.settings

import androidx.compose.runtime.Immutable
import app.vazie.vpn.core.model.SplitTunnel
import kotlinx.collections.immutable.ImmutableList

/** The split-tunnel screen: the choice, the apps to choose from ([apps] is `null` while they load) and the
 * search typed so far. */
@Immutable
data class SplitTunnelUiState(
    val split: SplitTunnel,
    val apps: ImmutableList<LaunchableApp>? = null,
    val query: String = "",
) {
    /** The apps matching [query], by name or package. */
    val visibleApps: List<LaunchableApp>
        get() {
            val all = apps.orEmpty()
            val needle = query.trim()
            if (needle.isEmpty()) return all
            return all.filter { it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true) }
        }
}

/** An app with a launcher icon, as the list shows it. */
@Immutable
data class LaunchableApp(val packageName: String, val label: String)

sealed interface SplitTunnelAction {
    data class SelectMode(val mode: SplitTunnel.Mode) : SplitTunnelAction
    data class ToggleApp(val packageName: String) : SplitTunnelAction
    data class QueryChanged(val query: String) : SplitTunnelAction
    data object Back : SplitTunnelAction
}

/** The choice after [action]: switching an app in or out, or changing the mode and keeping the apps. */
internal fun SplitTunnel.after(action: SplitTunnelAction): SplitTunnel = when (action) {
    is SplitTunnelAction.SelectMode -> copy(mode = action.mode)
    is SplitTunnelAction.ToggleApp ->
        copy(packages = if (action.packageName in packages) packages - action.packageName else packages + action.packageName)
    is SplitTunnelAction.QueryChanged, SplitTunnelAction.Back -> this
}
