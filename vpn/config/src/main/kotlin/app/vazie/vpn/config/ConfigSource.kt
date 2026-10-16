package app.vazie.vpn.config

import app.vazie.vpn.core.model.Secret

/** Something to parse. */
sealed interface ConfigSource {

    /** Pasted text, a decoded QR payload, or the contents of an imported file. */
    data class PlainText(val raw: Secret<String>) : ConfigSource
}
