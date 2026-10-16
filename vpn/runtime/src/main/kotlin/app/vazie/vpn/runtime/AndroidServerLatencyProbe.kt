package app.vazie.vpn.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import app.vazie.vpn.api.ServerEndpoint
import app.vazie.vpn.api.ServerLatencyProbe
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A TCP handshake to the server, timed, over the phone's own network rather than the tunnel. */
internal class AndroidServerLatencyProbe(context: Context) : ServerLatencyProbe {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun measure(endpoint: ServerEndpoint): Long? = withContext(Dispatchers.IO) {
        val network = connectivity?.underlyingNetwork() ?: return@withContext null
        val address = runCatching { network.getAllByName(endpoint.host).firstOrNull() }.getOrNull()
            ?: return@withContext null
        (1..ATTEMPTS).mapNotNull { connectMillis(network, address, endpoint.port) }.minOrNull()
    }

    private fun connectMillis(network: Network, address: InetAddress, port: Int): Long? = runCatching {
        network.socketFactory.createSocket().use { socket ->
            val started = SystemClock.elapsedRealtime()
            socket.connect(InetSocketAddress(address, port), TIMEOUT_MILLIS)
            SystemClock.elapsedRealtime() - started
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun ConnectivityManager.underlyingNetwork(): Network? = allNetworks.firstOrNull { network ->
        val capabilities = runCatching { getNetworkCapabilities(network) }.getOrNull()
            ?: return@firstOrNull false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private companion object {
        const val ATTEMPTS = 2
        const val TIMEOUT_MILLIS = 3_000
    }
}
