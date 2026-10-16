package app.vazie.vpn.core.model

/** Russian always; English when the operator wrote it. A device in any other language than Russian gets the English,
 * or the Russian when there is none. */
data class VazieFaqText(val ru: String, val en: String? = null) {

    fun resolve(language: String): String = if (language == RUSSIAN) ru else en ?: ru

    private companion object {
        const val RUSSIAN = "ru"
    }
}

/** Where an answer points: an `https` page, or a path on the Vazie site (`/legal/refunds`). */
data class VazieFaqLink(val url: String, val label: VazieFaqText) {

    /** A path on the site, opened against the site this build links to. */
    val isSitePath: Boolean get() = url.startsWith("/")
}

/** One question and its answer, which may run to several lines. */
data class VazieFaqItem(
    val key: String,
    val question: VazieFaqText,
    val answer: VazieFaqText,
    val link: VazieFaqLink? = null,
)

/** The questions the backend publishes for the FAQ screen, in the order to show them. */
data class VazieFaq(val items: List<VazieFaqItem>) {

    companion object {
        private const val URL_MAX_LENGTH = 512
        private val SITE_PATH = Regex("^/[A-Za-z0-9._~/-]*(#[A-Za-z0-9_-]+)?$")

        /** Whether [url] is something the screen will open: what [VazieLinks.isOpenable] opens minus `mailto:`, or
         * a path on the site that is not `//host`. */
        fun isOpenable(url: String): Boolean = when {
            url.length > URL_MAX_LENGTH -> false
            url.startsWith("//") -> false
            url.startsWith("/") -> SITE_PATH.matches(url)
            else -> url.startsWith("https://") && VazieLinks.isOpenable(url)
        }
    }
}
