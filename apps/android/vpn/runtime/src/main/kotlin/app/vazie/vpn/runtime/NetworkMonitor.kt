package app.vazie.vpn.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether there is a usable network **underneath** the tunnel. */
@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkStatus {

    private val connectivity: ConnectivityManager?
        get() = context.getSystemService<ConnectivityManager>()

    /** `true` while at least one validated non-VPN network exists, whichever one this app currently routes
     * through. */
    override fun hasUsableUnderlyingNetwork(): Boolean =
        connectivity?.usableUnderlyingNetworks()?.isNotEmpty() ?: true

    /** The same question, answered again whenever the answer changes. */
    override fun observeUsableUnderlyingNetwork(): Flow<Boolean> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            // Nothing to watch and nothing to claim: a device with no ConnectivityManager is not a
            // device that is offline, so the optimistic answer is the honest one.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        val known = manager.usableUnderlyingNetworks()
        val usable = known?.toMutableSet() ?: mutableSetOf()
        val callback = object : ConnectivityManager.NetworkCallback() {
            // Not `onAvailable`: it fires before validation, so a captive portal would look usable.
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                if (capabilities.isUsableUnderlying()) usable += network else usable -= network
                trySend(usable.isNotEmpty())
            }

            override fun onLost(network: Network) {
                usable -= network
                trySend(usable.isNotEmpty())
            }
        }
        trySend(known?.isNotEmpty() ?: true)
        runCatching { manager.registerNetworkCallback(underlyingRequest(), callback) }
        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    /** Every network the system currently holds, VPNs included — [isUsableUnderlying] filters those out by
     * capability rather than this function by guesswork. */
    @Suppress("DEPRECATION")
    private fun ConnectivityManager.underlyingCandidates(): Array<Network> = allNetworks

    /** `null` when the system would not say, which callers read as "assume a network". */
    private fun ConnectivityManager.usableUnderlyingNetworks(): Set<Network>? =
        runCatching { underlyingCandidates() }.getOrNull()
            ?.filterTo(mutableSetOf()) { isUsableUnderlying(it) }

    private fun ConnectivityManager.isUsableUnderlying(network: Network): Boolean =
        runCatching { getNetworkCapabilities(network) }.getOrNull()?.isUsableUnderlying() == true

    private fun NetworkCapabilities.isUsableUnderlying(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            // The one that mattered. Without it, Vazie's own tunnel counts as the network it is
            // supposed to be running over.
            hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

    private fun underlyingRequest(): NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()
}
