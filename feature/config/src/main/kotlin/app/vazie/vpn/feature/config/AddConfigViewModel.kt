package app.vazie.vpn.feature.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.feature.config.presentation.toDetection
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.config.ConfigSource
import app.vazie.vpn.config.ParseResult
import app.vazie.vpn.config.subscription.SubscriptionContent
import app.vazie.vpn.config.subscription.SubscriptionFailure
import app.vazie.vpn.config.subscription.SubscriptionFetch
import app.vazie.vpn.config.subscription.SubscriptionLink
import app.vazie.vpn.config.subscription.SubscriptionReader
import app.vazie.vpn.config.subscription.SubscriptionSource
import app.vazie.vpn.feature.config.presentation.toPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The wizard's draft, scoped to the add-configuration graph so the four steps share it. */
@HiltViewModel
class AddConfigViewModel @Inject constructor(
    private val parsers: ConfigParserRegistry,
    private val profiles: VpnProfileRepository,
    private val selected: SelectedProfileStore,
    private val subscriptions: SubscriptionSource,
    private val subscriptionReader: SubscriptionReader,
) : ViewModel() {

    // The on-screen formats come from the registry. See `ConfigParserRegistry.advertisedFormats`.
    private val _state = MutableStateFlow(
        AddConfigUiState(supportedFormats = parsers.advertisedFormats()),
    )
    val state: StateFlow<AddConfigUiState> = _state.asStateFlow()

    private val _effects = Channel<AddConfigEffect>(Channel.BUFFERED)
    val effects: Flow<AddConfigEffect> = _effects.receiveAsFlow()

    private var draft: ProfileDraft? = null

    /** What a subscription's list held, in the order of the previews in the state. */
    private var subscriptionDrafts: List<ProfileDraft> = emptyList()
    private var subscriptionDownload: Job? = null

    fun onAction(action: AddConfigAction) {
        when (action) {
            is AddConfigAction.SelectMethod -> selectMethod(action.method)
            AddConfigAction.PasteRequested -> emit(AddConfigEffect.ReadClipboard)
            AddConfigAction.Continue -> continueToReview()
            is AddConfigAction.NameChanged -> _state.update { it.copy(name = action.name) }
            AddConfigAction.Save -> save()
            AddConfigAction.ImportSubscription -> importSubscription()
            AddConfigAction.Close -> emit(AddConfigEffect.Close)
        }
    }

    /** A link typed or pasted into the first step's field: read it and show what came out. */
    fun onLinkSubmitted(text: Secret<String>) {
        if (text.expose().isBlank()) return
        forget()
        _state.update { AddConfigUiState(method = ImportMethodUi.PASTE, supportedFormats = it.supportedFormats) }
        onPasted(text)
        emit(AddConfigEffect.OpenPreview)
    }

    /** The clipboard's text, read by the route because that is where a `Context` belongs. */
    fun onPasted(text: Secret<String>) {
        forget()
        val subscription = SubscriptionLink.parse(text.expose())
        when {
            subscription != null -> download(subscription)
            SubscriptionLink.isInsecure(text.expose()) -> show(
                DetectionUiState.SubscriptionFailed(host = null, reason = SubscriptionFailureUi.INSECURE),
            )

            else -> read(text)
        }
    }

    private fun read(text: Secret<String>) {
        val result = parsers.parse(ConfigSource.PlainText(text))
        draft = (result as? ParseResult.Recognized)?.draft
        val detection = result.toDetection()
        _state.update { it.copy(detection = detection, name = detection.suggestedName()) }
    }

    /** Asks the link's own server for its list. The link itself goes no further than this call: the state
     * keeps the host and what the list held. */
    private fun download(link: SubscriptionLink) {
        show(DetectionUiState.SubscriptionLoading(link.host))
        subscriptionDownload = viewModelScope.launch {
            val detection = when (val fetch = subscriptions.fetch(link)) {
                is SubscriptionFetch.Failed ->
                    DetectionUiState.SubscriptionFailed(link.host, fetch.reason.toUi())

                is SubscriptionFetch.Fetched -> subscriptionReader.read(fetch.body).toDetection(link.host)
            }
            show(detection)
        }
    }

    private fun SubscriptionContent.toDetection(host: String): DetectionUiState {
        if (recognized.isEmpty()) {
            return DetectionUiState.SubscriptionFailed(host, SubscriptionFailureUi.EMPTY)
        }
        subscriptionDrafts = recognized.map { it.draft }
        return DetectionUiState.Subscription(
            host = host,
            configs = recognized.map { it.draft.toPreview(it.descriptor) }.toImmutableList(),
            skipped = skipped,
            truncated = truncated,
        )
    }

    private fun show(detection: DetectionUiState) {
        _state.update { it.copy(detection = detection, name = "") }
    }

    /** Drops whatever the last input came to, and stops waiting for a list nobody is looking at any more. */
    private fun forget() {
        subscriptionDownload?.cancel()
        subscriptionDownload = null
        subscriptionDrafts = emptyList()
        draft = null
    }


    private fun selectMethod(method: ImportMethodUi) {
        // No availability check any more: every value of `ImportMethodUi` is a way in that exists.
        // The check used to guard against a disabled row being tapped; there are no disabled rows.
        forget()
        _state.update { AddConfigUiState(method = method, supportedFormats = it.supportedFormats) }
        emit(AddConfigEffect.OpenPreview)
    }

    private fun continueToReview() {
        if (_state.value.detection !is DetectionUiState.Recognized) return
        emit(AddConfigEffect.OpenReview)
    }

    /** Stores the draft under the name the user confirmed. */
    private fun save() {
        val draft = draft ?: return
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(saving = true, importedCount = 0) }
        viewModelScope.launch {
            val id = profiles.create(
                draft = draft,
                name = state.name.trim(),
                origin = ProfileOrigin.IMPORTED_LINK,
            )
            // A newly imported configuration becomes the selected one.
            selected.select(id)
            this@AddConfigViewModel.draft = null
            _state.update { it.copy(saving = false) }
            _effects.send(AddConfigEffect.OpenDone)
        }
    }

    /** Stores every configuration of the subscription, each under the name its link gave it. */
    private fun importSubscription() {
        val detection = _state.value.detection as? DetectionUiState.Subscription ?: return
        val drafts = subscriptionDrafts
        if (!_state.value.canImportSubscription || drafts.size != detection.configs.size) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ids = drafts.zip(detection.configs) { draft, preview ->
                profiles.create(
                    draft = draft,
                    name = preview.suggestedName,
                    origin = ProfileOrigin.IMPORTED_LINK,
                )
            }
            // The list's first entry is the one its provider put first; that is the one to arm.
            selected.select(ids.first())
            subscriptionDrafts = emptyList()
            _state.update { it.copy(saving = false, importedCount = ids.size) }
            _effects.send(AddConfigEffect.OpenDone)
        }
    }

    private fun emit(effect: AddConfigEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

private fun SubscriptionFailure.toUi(): SubscriptionFailureUi = when (this) {
    SubscriptionFailure.UNREACHABLE -> SubscriptionFailureUi.UNREACHABLE
    SubscriptionFailure.REFUSED -> SubscriptionFailureUi.REFUSED
    SubscriptionFailure.TOO_LARGE -> SubscriptionFailureUi.TOO_LARGE
}

internal fun DetectionUiState.suggestedName(): String =
    (this as? DetectionUiState.Recognized)?.preview?.suggestedName.orEmpty()
