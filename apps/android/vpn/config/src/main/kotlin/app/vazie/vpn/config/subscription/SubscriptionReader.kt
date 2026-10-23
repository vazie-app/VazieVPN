package app.vazie.vpn.config.subscription

import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.config.ConfigSource
import app.vazie.vpn.config.ParseResult
import app.vazie.vpn.core.model.Secret
import java.util.Base64

/** What a subscription's answer came to. */
data class SubscriptionContent(
    /** The configurations Vazie can store, in the order the list gave them. */
    val recognized: List<ParseResult.Recognized>,
    /** Links in the list that are not configurations Vazie can run. */
    val skipped: Int,
    /** Whether the list went on past [SubscriptionReader.MAX_CONFIGURATIONS]. */
    val truncated: Boolean,
)

/** Reads a subscription's answer: share links one per line, as they are or Base64-encoded as a whole. */
class SubscriptionReader(private val parsers: ConfigParserRegistry) {

    fun read(body: Secret<String>): SubscriptionContent {
        val recognized = mutableListOf<ParseResult.Recognized>()
        var skipped = 0
        var truncated = false
        for (line in body.expose().decoded().lineSequence().map { it.trim() }) {
            // Only something link-shaped is a link Vazie failed to read; a blank line, a comment or
            // an error page is not counted as a configuration that was left out.
            if (LINK_MARK !in line) continue
            if (recognized.size == MAX_CONFIGURATIONS) {
                truncated = true
                break
            }
            when (val result = parsers.parse(ConfigSource.PlainText(Secret.of(line)))) {
                is ParseResult.Recognized -> recognized += result
                is ParseResult.Unsupported, is ParseResult.Invalid -> skipped++
            }
        }
        return SubscriptionContent(recognized = recognized, skipped = skipped, truncated = truncated)
    }

    /** The list as text. Links in the clear are taken as they are; anything else is tried as Base64, in
     * either alphabet and with or without padding, because servers disagree on all three. */
    private fun String.decoded(): String {
        if (LINK_MARK in this) return this
        val compact = filterNot { it.isWhitespace() }.trimEnd('=').replace('-', '+').replace('_', '/')
        return try {
            String(Base64.getDecoder().decode(compact), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            this
        }
    }

    companion object {

        /** More than anyone picks a server from, and few enough to store and show at once. */
        const val MAX_CONFIGURATIONS = 100

        private const val LINK_MARK = "://"
    }
}
