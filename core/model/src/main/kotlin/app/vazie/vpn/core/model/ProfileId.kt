package app.vazie.vpn.core.model

/** Identifier for a VPN profile. */
@JvmInline
value class ProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProfileId must not be blank" }
    }

    override fun toString(): String = value
}
