package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieFaq
import app.vazie.vpn.core.model.VazieFaqItem
import app.vazie.vpn.core.model.VazieFaqLink
import app.vazie.vpn.core.model.VazieFaqText
import app.vazie.vpn.core.network.ApiResult
import app.vazie.vpn.core.network.VazieApiClient
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** Where the FAQ screen's questions come from. `null` is "nothing new": keep what is shown. */
internal fun interface FaqSource {
    suspend fun fetch(): VazieFaq?
}

/**
 * `GET /faq/vpn` on the shared API, through a client that carries no session. A timeout, an error, an answer that is
 * not the contract, or questions none of which can be shown are `null`. An empty list is the operator's own answer
 * (every question switched off) and is believed. A question the app cannot show — no Russian text, a
 * text too long — is dropped on its own; a link that is not `https` or a site path is dropped and its question stays.
 */
internal class VazieFaqSource(
    private val api: VazieApiClient,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : FaqSource {

    override suspend fun fetch(): VazieFaq? {
        val result = withTimeoutOrNull(timeoutMillis) { api.get(PATH, FaqDto.serializer()) } ?: return null
        val dto = (result as? ApiResult.Success)?.value ?: return null
        return dto.toFaq()
    }

    private companion object {
        const val PATH = "/faq/vpn"

        /** Short: the screen is already showing questions, and a slow backend must not hold anything up. */
        const val TIMEOUT_MILLIS = 8_000L
    }
}

/** The backend's `FaqResponse`; every field optional, so a bad question is dropped by [toFaq], not thrown. */
@Serializable
internal data class FaqDto(val items: List<FaqItemDto>? = null) {

    /** `null` when it is not a list, or when it has questions and none of them can be shown. */
    fun toFaq(): VazieFaq? {
        val raw = items ?: return null
        val usable = raw.mapNotNull { it.toItem() }
        return if (raw.isNotEmpty() && usable.isEmpty()) null else VazieFaq(usable)
    }
}

@Serializable
internal data class FaqItemDto(
    val key: String? = null,
    val question: FaqTextDto? = null,
    val answer: FaqTextDto? = null,
    val link: FaqLinkDto? = null,
) {
    fun toItem(): VazieFaqItem? {
        return VazieFaqItem(
            key = key?.takeIf { it.isNotBlank() } ?: return null,
            question = question?.toText(QUESTION_MAX, multiline = false) ?: return null,
            answer = answer?.toText(ANSWER_MAX, multiline = true) ?: return null,
            link = link?.toLink(),
        )
    }

    private companion object {
        const val QUESTION_MAX = 200
        const val ANSWER_MAX = 2000
    }
}

@Serializable
internal data class FaqLinkDto(val url: String? = null, val label: FaqTextDto? = null) {
    fun toLink(): VazieFaqLink? {
        val url = url?.takeIf(VazieFaq::isOpenable) ?: return null
        return VazieFaqLink(url, label?.toText(LABEL_MAX, multiline = false) ?: return null)
    }

    private companion object {
        const val LABEL_MAX = 80
    }
}

@Serializable
internal data class FaqTextDto(val ru: String? = null, val en: String? = null) {

    /** The Russian, and the English when it is usable; no Russian is nothing. */
    fun toText(maxLength: Int, multiline: Boolean): VazieFaqText? {
        fun usable(text: String) = text.isNotBlank() && text.length <= maxLength &&
            text.none { it.isISOControl() && !(multiline && it == '\n') }
        val ru = ru?.takeIf(::usable) ?: return null
        return VazieFaqText(ru, en?.takeIf(::usable))
    }
}
