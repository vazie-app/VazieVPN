package app.vazie.vpn.runtime

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** The three blocking things a readiness check actually does, separated from the rules about how long they
 * may take. */
internal interface ProbeOperations {

    /** A TLS session to a **literal address**, so it needs no name resolution. */
    fun tlsToAddress(): Boolean

    /** A name resolved through whatever resolver the tunnel's interface advertised. */
    fun resolveName(): Boolean

}

internal class AndroidProbeOperations : ProbeOperations {

    /** `Socket.connect` has a native timeout, so this stage returns even when nothing can interrupt it. */
    override fun tlsToAddress(): Boolean = TLS_TARGETS.any(::completesTlsSession)

    override fun resolveName(): Boolean =
        runCatching { InetAddress.getAllByName(PROBE_HOST).isNotEmpty() }.getOrDefault(false)

    /** A TLS session that got far enough to be answered, and no body read. */
    private fun completesTlsSession(url: String): Boolean = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = CONNECT_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.responseCode in HTTP_OK_RANGE
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private companion object {
        /** The two addresses a TLS session is attempted to, and the one name that is resolved. */
        val TLS_TARGETS = listOf("https://1.1.1.1/", "https://8.8.8.8/")

        const val PROBE_HOST = "example.com"

        /** A redirect counts. `1.1.1.1/` answers `301` to its own help page, and a redirect that arrived is a
         * TLS session that completed — which is the question being asked. */
        val HTTP_OK_RANGE = 200..399

        const val CONNECT_TIMEOUT_MILLIS = 8_000
    }
}
