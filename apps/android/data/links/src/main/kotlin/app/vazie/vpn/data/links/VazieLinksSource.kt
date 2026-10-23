package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieLink
import app.vazie.vpn.core.model.VazieLinkSection
import app.vazie.vpn.core.model.VazieLinks
import app.vazie.vpn.core.model.VazieLocalizedText
import app.vazie.vpn.core.network.ApiResult
import app.vazie.vpn.core.network.VazieApiClient
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** Where the About screen's links come from. `null` is "nothing new": keep what is shown. */
internal fun interface LinksSource {
    suspend fun fetch(): VazieLinks?
}

/**
 * `GET /apps/vpn/links` on the shared API, through a client that carries no session. A timeout, an error, an
 * answer that is not the contract, or one with no usable link is `null`. A link the app cannot show — an unknown
 * section, a URL that is not `https` or `mailto:`, a blank title — is dropped on its own; the rest stay.
 */
internal class VazieLinksSource(
    private val api: VazieApiClient,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : LinksSource {

    override suspend fun fetch(): VazieLinks? {
        val result = withTimeoutOrNull(timeoutMillis) { api.get(PATH, LinksDto.serializer()) } ?: return null
        val dto = (result as? ApiResult.Success)?.value ?: return null
        return dto.toLinks()
    }

    private companion object {
        const val PATH = "/apps/vpn/links"

        /** Short: the screen is already showing links, and a slow backend must not hold anything up. */
        const val TIMEOUT_MILLIS = 8_000L
    }
}

/** The backend's `AppLinksResponse`; every field optional, so a bad link is dropped by [toLinks], not thrown. */
@Serializable
internal data class LinksDto(val links: List<LinkDto>? = null) {

    /** `null` when nothing in it can be shown. */
    fun toLinks(): VazieLinks? = links.orEmpty().mapNotNull { it.toLink() }.takeIf { it.isNotEmpty() }?.let(::VazieLinks)
}

@Serializable
internal data class LinkDto(
    val key: String? = null,
    val section: String? = null,
    val url: String? = null,
    val title: TextDto? = null,
    val subtitle: TextDto? = null,
    val action: TextDto? = null,
) {
    fun toLink(): VazieLink? {
        val section = VazieLinkSection.entries.firstOrNull { it.name == section } ?: return null
        val url = url?.takeIf(VazieLinks::isOpenable) ?: return null
        return VazieLink(
            key = key?.takeIf { it.isNotBlank() } ?: return null,
            section = section,
            url = url,
            title = title?.toText() ?: return null,
            subtitle = subtitle?.toText(),
            action = action?.toText(),
        )
    }
}

@Serializable
internal data class TextDto(val ru: String? = null, val en: String? = null) {

    /** Both halves, each one line of at most [MAX_LENGTH] characters, or nothing. */
    fun toText(): VazieLocalizedText? {
        val ru = ru?.takeIf(::isText) ?: return null
        val en = en?.takeIf(::isText) ?: return null
        return VazieLocalizedText(ru, en)
    }

    private fun isText(text: String) = text.isNotBlank() && text.length <= MAX_LENGTH && text.none { it.isISOControl() }

    private companion object {
        const val MAX_LENGTH = 400
    }
}
