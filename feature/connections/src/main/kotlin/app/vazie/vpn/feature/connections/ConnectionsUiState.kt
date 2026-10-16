package app.vazie.vpn.feature.connections

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.vazie.vpn.core.model.LastUsed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** While [loading], only the header shows, so "no configurations" is said only when true. */
@Immutable
data class ConnectionsUiState(
    val loading: Boolean = false,
    /** The configuration a person has asked to delete, or `null`. */
    val deleting: ConfigurationRowUi? = null,
    val configurations: ImmutableList<ConfigurationRowUi> = persistentListOf(),

) {
    val isEmpty: Boolean get() = configurations.isEmpty() && !loading
}

/** One row of the list: display fields only, no endpoint, key or raw configuration. */
@Immutable
data class ConfigurationRowUi(
    val id: String,
    val name: String,
    val mark: String,
    val protocolLabel: String,
    val status: ConfigurationStatusUi,
    /** Optional line under the name — a handshake age, a last-used note. */
    @param:StringRes val detailRes: Int? = null,
    val enabled: Boolean = true,
    /** When this configuration last carried traffic. */
    val lastUsed: LastUsed = LastUsed.Never,
    val latency: LatencyUi = LatencyUi.Unknown,
)

/** How the row's server answered the latency check. [Unknown] draws nothing: no address to dial, or no answer
 * yet. */
@Immutable
sealed interface LatencyUi {
    data object Unknown : LatencyUi
    data object NoAnswer : LatencyUi
    data class Answered(val millis: Long) : LatencyUi {
        /** 4 bars up to 150 ms, 3 up to 300, 2 up to 600, then 1. */
        val bars: Int
            get() = when {
                millis <= 150 -> 4
                millis <= 300 -> 3
                millis <= 600 -> 2
                else -> 1
            }
    }
}

/** Row variants; `Unavailable` stays visible, configs are never hidden. */
enum class ConfigurationStatusUi {
    CONNECTED, SELECTED, IDLE, ERROR, UNAVAILABLE,
}

sealed interface ConnectionsAction {
    /** Make this configuration the one Connect would use. Tapping the row does it, which is the shortest true
     * reading of what a list of configurations is for. */
    data class SelectConfiguration(val id: String) : ConnectionsAction


    /** Ask to delete, which is not the same as deleting. */
    data class RequestDelete(val id: String) : ConnectionsAction
    data object ConfirmDelete : ConnectionsAction
    data object DismissDelete : ConnectionsAction

    data class OpenConfiguration(val id: String) : ConnectionsAction
    data object AddConfiguration : ConnectionsAction
}

sealed interface ConnectionsEffect {
    data class OpenConfiguration(val id: String) : ConnectionsEffect
    data object OpenAddConfiguration : ConnectionsEffect
}
