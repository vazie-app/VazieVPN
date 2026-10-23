package app.vazie.vpn.config.vless

/** A share link's query string, read once and then consumed parameter by parameter. */
internal class LinkQuery private constructor(private val entries: List<Entry>) {

    private class Entry(val name: String, val value: String) {
        var consumed: Boolean = false
    }

    /** The value of the first unconsumed parameter matching any of [names], in the order given, or `null`
     * when none is present. */
    fun take(vararg names: String): String? {
        names.forEach { name ->
            val entry = entries.firstOrNull { !it.consumed && it.name.equals(name, ignoreCase = true) }
            if (entry != null) {
                entry.consumed = true
                return entry.value
            }
        }
        return null
    }

    /** Everything [take] did not claim, in the order it appeared, spelled as it arrived. */
    fun remaining(): List<Pair<String, String>> =
        entries.filterNot { it.consumed }.map { it.name to it.value }

    companion object {

        /** Reads [query], or returns `null` when any name or value is malformed percent-encoding. */
        fun parse(query: String): LinkQuery? {
            if (query.isEmpty()) return LinkQuery(emptyList())
            val entries = ArrayList<Entry>()
            query.split('&').forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val separator = pair.indexOf('=')
                val rawName = if (separator < 0) pair else pair.substring(0, separator)
                val rawValue = if (separator < 0) "" else pair.substring(separator + 1)
                val name = PercentCoding.decodeStrict(rawName) ?: return null
                val value = PercentCoding.decodeStrict(rawValue) ?: return null
                if (name.isEmpty()) return@forEach
                entries += Entry(name, value)
            }
            return LinkQuery(entries)
        }
    }
}
