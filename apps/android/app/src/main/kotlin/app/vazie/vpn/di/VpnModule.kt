package app.vazie.vpn.di

import android.content.Context
import app.vazie.vpn.data.profiles.ProfileStorage
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.VpnEngine
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.engine.xray.XrayEngine
import app.vazie.vpn.runtime.EngineRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Where the engine meets the runtime, and the only place in the app where that is legal. */
@Module
@InstallIn(SingletonComponent::class)
object VpnModule {

    @Provides
    @Singleton
    fun xrayEngine(): VpnEngine = XrayEngine()

    @Provides
    @Singleton
    fun engineRegistry(xray: VpnEngine): EngineRegistry = EngineRegistry(listOf(xray))

    @Provides
    @Singleton
    fun selectedProfileStore(
        @ApplicationContext context: Context,
        profiles: VpnProfileRepository,
    ): SelectedProfileStore = ProfileStorage.selectedProfileStore(
        filesDir = context.filesDir,
        profiles = profiles,
    )
}
