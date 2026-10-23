package app.vazie.vpn.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** The regression test for the bug that broke the first real device run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkMonitorTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val monitor = NetworkMonitor(context)

    @Test
    fun `a validated wifi network is a usable underlying network`() {
        reset()
        addNetwork(id = 100, wifi(validated = true))

        assertTrue(monitor.hasUsableUnderlyingNetwork())
    }

    @Test
    fun `a VPN as the default network is not the absence of a network`() {
        reset()
        // This is the device's state one millisecond after `establish()`: Wi-Fi is still there,
        // still validated, and no longer the default — the tunnel is.
        addNetwork(id = 100, wifi(validated = true))
        addNetwork(id = 101, vpn())

        assertTrue(
            monitor.hasUsableUnderlyingNetwork(),
            "the tunnel becoming the default network was read as the Wi-Fi disappearing",
        )
    }

    @Test
    fun `a VPN with no network under it is the absence of a network`() {
        reset()
        addNetwork(id = 101, vpn())

        assertFalse(
            monitor.hasUsableUnderlyingNetwork(),
            "a tunnel over nothing was counted as its own upstream",
        )
    }

    @Test
    fun `losing every non-VPN network is the absence of a network`() {
        reset()
        addNetwork(id = 100, wifi(validated = true))
        assertTrue(monitor.hasUsableUnderlyingNetwork())

        reset()

        assertFalse(monitor.hasUsableUnderlyingNetwork())
    }

    @Test
    fun `a connected but unvalidated network does not count`() {
        reset()
        // A captive portal: connected transport, no uplink. Starting a tunnel over one produces a
        // handshake failure that reads as the user's server being at fault.
        addNetwork(id = 100, wifi(validated = false))

        assertFalse(monitor.hasUsableUnderlyingNetwork())
    }

    @Test
    fun `a network with no internet capability does not count`() {
        reset()
        addNetwork(id = 100, capabilities(validated = true, notVpn = true, internet = false))

        assertFalse(monitor.hasUsableUnderlyingNetwork())
    }

    @Test
    fun `an unvalidated network replayed first is not a moment without a network`() = runTest {
        reset()
        val mobile = addNetwork(id = 100, wifi(validated = false))
        val wifi = addNetwork(id = 101, wifi(validated = true))
        val seen = mutableListOf<Boolean>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.observeUsableUnderlyingNetwork().toList(seen)
        }

        // What registration replays, in the order that used to flash NoInternet.
        val callback = shadowOf(manager).networkCallbacks.single()
        callback.onCapabilitiesChanged(mobile, manager.getNetworkCapabilities(mobile)!!)
        callback.onCapabilitiesChanged(wifi, manager.getNetworkCapabilities(wifi)!!)
        callback.onLost(wifi)
        collecting.cancel()

        assertEquals(listOf(true, false), seen)
    }

    private fun reset() {
        shadowOf(manager).clearAllNetworks()
    }

    private fun addNetwork(id: Int, capabilities: NetworkCapabilities): Network {
        val network = ShadowNetwork.newInstance(id)
        shadowOf(manager).addNetwork(network, null)
        shadowOf(manager).setNetworkCapabilities(network, capabilities)
        return network
    }

    private fun wifi(validated: Boolean): NetworkCapabilities =
        capabilities(validated = validated, notVpn = true, internet = true)

    private fun vpn(): NetworkCapabilities =
        capabilities(validated = true, notVpn = false, internet = true)

    private fun capabilities(
        validated: Boolean,
        notVpn: Boolean,
        internet: Boolean,
    ): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadow = shadowOf(capabilities)
        if (internet) shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        // `NET_CAPABILITY_NOT_VPN` is present by default, so a VPN is described by removing it.
        if (notVpn) {
            shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        } else {
            shadow.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        return capabilities
    }
}
