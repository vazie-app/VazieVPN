package app.vazie.vpn.links

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vazie.vpn.core.model.VazieLinks
import app.vazie.vpn.data.links.AppLinksRepository
import app.vazie.vpn.data.links.FaqRepository
import app.vazie.vpn.feature.settings.FaqUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The About screen's links and the FAQ screen's questions for Settings. [refresh] is asked when Settings or About opens; the repository decides
 * whether the backend is due, so nothing is sent from a screen that does not show the links. */
@HiltViewModel
class AppLinksViewModel @Inject constructor(
    private val repository: AppLinksRepository,
    private val faqRepository: FaqRepository,
) : ViewModel() {

    val links: StateFlow<VazieLinks?> = repository.links

    /** The FAQ screen: the questions as the backend last published them, or why there are none to show. */
    val faq: StateFlow<FaqUiState> = combine(faqRepository.faq, faqRepository.loading) { faq, loading ->
        when {
            faq != null -> FaqUiState.Ready(faq.items)
            loading -> FaqUiState.Loading
            else -> FaqUiState.Unavailable
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FaqUiState.Loading)

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    /** Asked when the FAQ screen opens; the repository decides whether the backend is due. */
    fun refreshFaq() {
        viewModelScope.launch { faqRepository.refresh() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
