package app.vazie.vpn.update

import android.content.Context
import app.vazie.vpn.BuildConfig
import app.vazie.vpn.di.VazieApi
import app.vazie.vpn.data.update.UpdateStorage
import app.vazie.vpn.update.api.AppUpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The update check, bound in every build type. One checker per process, so the automatic checks and Settings'
 * "Check for updates" share what was last learned. It has no connection to the account or the tunnel. */
@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {

    @Provides
    @Singleton
    fun updateChecker(@ApplicationContext context: Context): AppUpdateChecker = UpdateStorage.checker(
        context = context,
        baseUrl = VazieApi.SHARED_BASE_URL,
        filesDir = context.filesDir,
        installedVersionCode = BuildConfig.VERSION_CODE,
    )
}
