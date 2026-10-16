package app.vazie.vpn.config.vless

import java.io.ByteArrayOutputStream

/** Percent-decoding, in the two strictnesses a share link needs. */
internal object PercentCoding {

    /** Decoded text, or `null` when a `%` is not followed by two hexadecimal digits. */
    fun decodeStrict(text: String): String? = decode(text, lenient = false)

    /** Decoded text, with any malformed `%` kept as a literal `%`. */
    fun decodeLenient(text: String): String = decode(text, lenient = true)!!

    /** Percent-encode everything outside [safe], as UTF-8. */
    fun encode(text: String, safe: String): String = buildString {
        text.toByteArray(Charsets.UTF_8).forEach { byte ->
            val char = (byte.toInt() and 0xFF).toChar()
            if (char.isUnreserved() || char in safe) {
                append(char)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr 4) and 0xF])
                append(HEX[byte.toInt() and 0xF])
            }
        }
    }

    /** A UUID needs nothing escaped, but the user-info position still forbids `@` and `:`. */
    const val USER_INFO: String = "-._~"

    /** Inside a query value: `/` and `:` are legal and common (a WebSocket path, an IPv6 SNI), while `&`,
     * `=`, `?` and `#` would end the value. */
    const val QUERY: String = "-._~/:,"

    /** A display name is the last thing in the link, so only `#` itself has to go. */
    const val FRAGMENT: String = "-._~/:?&=+,!$'()*;@"

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

    private val HEX = "0123456789ABCDEF".toCharArray()

    private fun decode(text: String, lenient: Boolean): String? {
        if ('%' !in text) return text
        val bytes = ByteArrayOutputStream(text.length * BYTES_PER_CHAR_UPPER_BOUND)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char != '%') {
                // Written as UTF-8 rather than as a code unit: the text may already contain
                // characters above ASCII beside the escapes, and half of one is not a byte.
                bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                index++
                continue
            }
            val high = text.getOrNull(index + 1)?.hexValue()
            val low = text.getOrNull(index + 2)?.hexValue()
            if (high == null || low == null) {
                if (!lenient) return null
                bytes.write('%'.code)
                index++
                continue
            }
            bytes.write((high shl 4) or low)
            index += 3
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun Char.hexValue(): Int? = when (this) {
        in '0'..'9' -> code - '0'.code
        in 'a'..'f' -> code - 'a'.code + 10
        in 'A'..'F' -> code - 'A'.code + 10
        else -> null
    }

    private const val BYTES_PER_CHAR_UPPER_BOUND = 3
}
