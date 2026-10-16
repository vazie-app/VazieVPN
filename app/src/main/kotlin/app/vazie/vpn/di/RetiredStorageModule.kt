package app.vazie.vpn.di

import android.content.Context
import app.vazie.vpn.StartupTask
import app.vazie.vpn.data.RetiredStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Clears what earlier builds stored and this one never reads. */
@Module
@InstallIn(SingletonComponent::class)
object RetiredStorageModule {

    @Provides
    @IntoSet
    fun clearRetiredStorage(@ApplicationContext context: Context): StartupTask = StartupTask {
        withContext(Dispatchers.IO) { RetiredStorage.clear(context.filesDir, dataDir = context.dataDir) }
    }
}
