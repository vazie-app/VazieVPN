package app.vazie.vpn.feature.config

import androidx.compose.runtime.Immutable
import app.vazie.vpn.core.model.LastUsed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class ConfigDetailsUiState(
    val name: String = "",
    val protocolLabel: String = "",
    val securityLabel: String? = null,
    val transportLabel: String? = null,
    val server: String = "",
    /** The endpoint's two halves, kept apart as well as joined. */
    val endpointHost: String = "",
    val endpointPort: Int = 0,
    val serverName: String? = null,
    val fingerprint: String? = null,
    val flow: String? = null,
    val transportDetail: String? = null,
    val keptParameters: ImmutableList<String> = persistentListOf(),
    val secretLabel: String = "",
    val secret: String = "",
    val lastUsed: LastUsed = LastUsed.Never,
    /** Where this configuration came from and what will run it. */
    val engineLabel: String = "",
    val secretRevealed: Boolean = false,
    val confirmingDelete: Boolean = false,
    /** The rename sheet's draft, or null when it is closed. */
    val renameDraft: String? = null,
    val confirmingExport: Boolean = false,
    val loading: Boolean = true,
    val missing: Boolean = false,
) {

    /** A rename is only worth applying if it says something. */
    val renameValid: Boolean get() = !renameDraft.isNullOrBlank()
}

sealed interface ConfigDetailsAction {
    data object ToggleSecret : ConfigDetailsAction
    data object HideSecret : ConfigDetailsAction
    data object RequestRename : ConfigDetailsAction
    data class EditRename(val name: String) : ConfigDetailsAction
    data object ConfirmRename : ConfigDetailsAction
    data object DismissRename : ConfigDetailsAction
    data object Duplicate : ConfigDetailsAction
    data object RequestExport : ConfigDetailsAction
    data object ConfirmExport : ConfigDetailsAction
    data object DismissExport : ConfigDetailsAction
    data object RequestDelete : ConfigDetailsAction
    data object ConfirmDelete : ConfigDetailsAction
    data object DismissDelete : ConfigDetailsAction
    data object Back : ConfigDetailsAction
}

sealed interface ConfigDetailsEffect {

    data object Close : ConfigDetailsEffect

    /** A duplicate was made; the screen says so and stays where it is. */
    data object Duplicated : ConfigDetailsEffect

    /** The share link, for exactly one hand-off to the system share sheet. */
    data class Share(val link: app.vazie.vpn.core.model.Secret<String>) : ConfigDetailsEffect
}
