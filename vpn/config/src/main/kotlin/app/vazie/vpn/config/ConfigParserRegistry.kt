package app.vazie.vpn.config

import app.vazie.vpn.config.vless.VlessLinkParser

/** The ordered list of parsers; the first one that accepts the input answers for it. */
class ConfigParserRegistry(private val parsers: List<ConfigParser>) {

    /** The ids of every parser this registry holds, in the order it tries them. */
    fun parserIds(): List<String> = parsers.map { it.id }

    /** The formats a screen may tell a person Vazie reads, as one already-formatted phrase. */
    fun advertisedFormats(): String =
        parserIds().joinToString(separator = FORMAT_SEPARATOR) { it.uppercase() }

    fun parse(source: ConfigSource): ParseResult = when (source) {
        is ConfigSource.PlainText -> parse(source.raw.expose())
    }

    private fun parse(raw: String): ParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParseResult.Invalid(InvalidReason.Empty)
        val parser = parsers.firstOrNull { it.accepts(trimmed) }
            ?: return ParseResult.Invalid(InvalidReason.UnknownScheme(trimmed.schemeOrNull()))
        return parser.parse(trimmed)
    }

    /** A scheme, if the text starts with one. Deliberately conservative: anything that is not `word://`
     * reports no scheme at all rather than guessing at a prefix of the user's text. */
    private fun String.schemeOrNull(): String? =
        SCHEME.matchEntire(substringBefore("://", missingDelimiterValue = ""))?.value?.lowercase()

    companion object {

        private val SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]{0,31}")

        /** What separates two format names in a sentence. Today there is never a second one. */
        private const val FORMAT_SEPARATOR = " · "

        /** Every parser Vazie ships. One entry today; the list is the extension point. */
        fun default(): ConfigParserRegistry = ConfigParserRegistry(listOf(VlessLinkParser()))
    }
}
