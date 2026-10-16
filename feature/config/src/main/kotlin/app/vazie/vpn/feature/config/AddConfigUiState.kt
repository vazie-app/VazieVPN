package app.vazie.vpn.feature.config

import androidx.compose.runtime.Immutable
import app.vazie.vpn.config.InvalidReason
import app.vazie.vpn.config.UnsupportedFeature
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The in-flight import. */
@Immutable
data class AddConfigUiState(
    val method: ImportMethodUi? = null,
    val detection: DetectionUiState = DetectionUiState.AwaitingInput,
    val name: String = "",
    val saving: Boolean = false,
    /** The formats Vazie can read, as one already-formatted phrase. */
    val supportedFormats: String = "",
    /** How many configurations the last save stored at once; zero until a subscription has been added. */
    val importedCount: Int = 0,
) {
    val canSave: Boolean
        get() = detection is DetectionUiState.Recognized && name.isNotBlank() && !saving

    val canImportSubscription: Boolean
        get() = detection is DetectionUiState.Subscription && !saving
}

/** How a configuration comes in. */
enum class ImportMethodUi {
    PASTE,
}

/** What Vazie made of what the user gave it. */
@Immutable
sealed interface DetectionUiState {

    /** Nothing has been given to Vazie yet. */
    data object AwaitingInput : DetectionUiState

    data class Recognized(val preview: ConfigPreviewUi) : DetectionUiState

    /** Read correctly, and Vazie has no support for part of it — its own outcome, not an error. */
    data class Unsupported(
        val protocolLabel: String,
        val feature: UnsupportedFeature,
    ) : DetectionUiState

    data class Invalid(val reason: InvalidReason) : DetectionUiState

    /** A subscription link, and its list is being downloaded from [host]. */
    data class SubscriptionLoading(val host: String) : DetectionUiState

    /** A subscription's list, read. [host] is all of the link that is kept: the rest of it is a token. */
    data class Subscription(
        val host: String,
        val configs: ImmutableList<ConfigPreviewUi>,
        /** Links in the list that are not configurations Vazie can run. */
        val skipped: Int = 0,
        /** The list was longer than Vazie adds at once, and [configs] is its beginning. */
        val truncated: Boolean = false,
    ) : DetectionUiState

    /** A subscription link that gave no list. [host] is `null` when the link was never asked. */
    data class SubscriptionFailed(
        val host: String?,
        val reason: SubscriptionFailureUi,
    ) : DetectionUiState
}

/** Why a subscription link added nothing. */
enum class SubscriptionFailureUi {
    /** Plain `http`: never downloaded. */
    INSECURE,
    UNREACHABLE,
    REFUSED,
    TOO_LARGE,

    /** An answer with no configuration in it that Vazie can run. */
    EMPTY,
}

/** Safe fields only. There is no field here that could hold a user id, a key or a short id, so the preview
 * card cannot draw one however it is written — the type is the guarantee. */
@Immutable
data class ConfigPreviewUi(
    val protocolLabel: String,
    val securityLabel: String?,
    val transportLabel: String?,
    val server: String,
    val endpointHost: String,
    val endpointPort: Int,
    val serverName: String?,
    val fingerprint: String?,
    val flow: String?,
    val suggestedName: String,
    val mark: String,
    val keptParameters: ImmutableList<String> = persistentListOf(),
)

sealed interface AddConfigAction {
    data class SelectMethod(val method: ImportMethodUi) : AddConfigAction

    /** The user asked for the clipboard to be read. The screen never touches the clipboard itself — see
     * `AddConfigRoutes`. */
    data object PasteRequested : AddConfigAction
    data object Continue : AddConfigAction
    data class NameChanged(val name: String) : AddConfigAction
    data object Save : AddConfigAction

    /** Store every configuration the subscription's list held. */
    data object ImportSubscription : AddConfigAction
    data object Close : AddConfigAction
}

sealed interface AddConfigEffect {
    data object OpenPreview : AddConfigEffect
    data object OpenReview : AddConfigEffect
    data object OpenDone : AddConfigEffect
    data object Close : AddConfigEffect

    /** Read the clipboard and hand the text back through `AddConfigViewModel.onPasted`. */
    data object ReadClipboard : AddConfigEffect
}
