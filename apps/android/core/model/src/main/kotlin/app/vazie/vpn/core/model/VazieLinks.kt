package app.vazie.vpn.core.model

/**
 * Where a link sits in the app, in the order the About screen shows the cards. The app owns the layout; the backend
 * only says which card: [AUTHOR], [PROJECT] («Vazie и поддержка»), [SECURITY] («Безопасность и прозрачность»), [HELP],
 * [SUPPORT] (a card with a button each) and [FEEDBACK], whose first link is also the "report a bug" row in Settings.
 */
enum class VazieLinkSection { AUTHOR, PROJECT, SECURITY, HELP, SUPPORT, FEEDBACK }

/** One text in both languages the app speaks. */
data class VazieLocalizedText(val ru: String, val en: String) {

    /** Russian for a Russian-speaking device, English for every other. */
    fun resolve(language: String): String = if (language == RUSSIAN) ru else en

    private companion object {
        const val RUSSIAN = "ru"
    }
}

/** A link the app opens, and the words around it. Only ever an `https` page or a `mailto:` address. */
data class VazieLink(
    val key: String,
    val section: VazieLinkSection,
    val url: String,
    val title: VazieLocalizedText,
    val subtitle: VazieLocalizedText? = null,
    /** The button's words in [VazieLinkSection.SUPPORT]. */
    val action: VazieLocalizedText? = null,
)

/** The links the backend publishes, in the order to show them. */
data class VazieLinks(val links: List<VazieLink>) {

    fun inSection(section: VazieLinkSection): List<VazieLink> = links.filter { it.section == section }

    companion object {
        private const val URL_MAX_LENGTH = 512
        private val HTTPS = Regex("^https://[^/?#@\\s]+(?:[/?#][^\\s]*)?$")
        private val MAILTO = Regex("^mailto:[^\\s@?/]+@[^\\s@?/]+\\.[^\\s@?/]+$")

        /** Whether [url] is something the app will open: `https` with a host and no user info, or one `mailto:`. */
        fun isOpenable(url: String): Boolean =
            url.length <= URL_MAX_LENGTH && url.none { it.isISOControl() } && (HTTPS.matches(url) || MAILTO.matches(url))
    }
}
