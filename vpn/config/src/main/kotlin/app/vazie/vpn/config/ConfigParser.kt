package app.vazie.vpn.config

/** One protocol's reader. */
interface ConfigParser {

    /** Stable machine name, matching the protocol descriptor's id. */
    val id: String

    /** Whether this parser is the one that should answer for [raw]. Cheap; looks at the scheme. */
    fun accepts(raw: String): Boolean

    fun parse(raw: String): ParseResult
}
