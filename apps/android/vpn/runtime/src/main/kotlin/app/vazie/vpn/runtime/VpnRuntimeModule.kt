package app.vazie.vpn.runtime

import android.content.Context
import app.vazie.vpn.api.ServerLatencyProbe
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.SplitTunnelSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Where the one connection owner is created. */
@Module
@InstallIn(SingletonComponent::class)
object VpnRuntimeModule {

    /** Internal types are built inside the one public binding, so Dagger needs no mangled names. */
    @Provides
    @Singleton
    fun controller(
        @ApplicationContext context: Context,
        profiles: VpnProfileRepository,
        engines: EngineRegistry,
        network: NetworkMonitor,
        traffic: TrafficMeter,
        splitTunnel: SplitTunnelSource,
    ): VazieVpnConnectionController = VazieVpnConnectionController(
        profiles = profiles,
        engines = engines,
        launcher = AndroidTunnelLauncher(context),
        probe = TunnelReadinessProbe(),
        network = network,
        traffic = traffic,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        splitTunnel = splitTunnel,
    )

    /** The same object under the interface every other module sees. Two bindings, one instance — anything
     * else would be two connection states. */
    @Provides
    @Singleton
    fun latencyProbe(@ApplicationContext context: Context): ServerLatencyProbe = AndroidServerLatencyProbe(context)

    @Provides
    @Singleton
    fun connectionController(
        controller: VazieVpnConnectionController,
    ): VpnConnectionController = controller
}
