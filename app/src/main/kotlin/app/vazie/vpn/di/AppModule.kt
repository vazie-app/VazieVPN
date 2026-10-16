package app.vazie.vpn.di

import android.content.Context
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.SplitTunnelSource
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.data.AppPreferences
import app.vazie.vpn.data.AppStorage
import app.vazie.vpn.icon.AppIconSwitcher
import app.vazie.vpn.icon.ComponentAppIconSwitcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun appPreferences(@ApplicationContext context: Context): AppPreferences =
        AppStorage.preferences(filesDir = context.filesDir)

    @Provides
    @Singleton
    fun appIconSwitcher(switcher: ComponentAppIconSwitcher): AppIconSwitcher = switcher

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    /** The runtime reads the split-tunnel choice from preferences at each connection. */
    @Provides
    @Singleton
    fun splitTunnel(preferences: AppPreferences): SplitTunnelSource =
        SplitTunnelSource { preferences.snapshot().splitTunnel }

}
