package app.vazie.vpn.core.model

/** Wrapper for values that must not be logged, printed or serialized by accident: WireGuard private keys,
 * VLESS UUIDs, Trojan passwords, Reality secrets. */
class Secret<out T : Any> private constructor(private val value: T) {

    fun expose(): T = value

    override fun toString(): String = REDACTED

    companion object {
        const val REDACTED: String = "Secret(***)"

        fun <T : Any> of(value: T): Secret<T> = Secret(value)
    }
}
