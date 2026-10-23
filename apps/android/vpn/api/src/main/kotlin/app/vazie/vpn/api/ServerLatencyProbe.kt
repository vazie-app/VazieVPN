package app.vazie.vpn.api

/** A server's public address: what a latency check dials. */
data class ServerEndpoint(val host: String, val port: Int)

/** How long a server takes to answer, measured from this device. */
fun interface ServerLatencyProbe {

    /** Milliseconds to connect, or null when it did not answer in time. */
    suspend fun measure(endpoint: ServerEndpoint): Long?

    companion object {
        val None: ServerLatencyProbe = ServerLatencyProbe { null }
    }
}
