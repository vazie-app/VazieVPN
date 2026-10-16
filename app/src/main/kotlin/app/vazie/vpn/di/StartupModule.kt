package app.vazie.vpn.di

import app.vazie.vpn.StartupTask
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the set of [StartupTask]s, so it exists — empty — in a build that contributes none. */
@Module
@InstallIn(SingletonComponent::class)
abstract class StartupModule {

    @Multibinds
    abstract fun startupTasks(): Set<StartupTask>
}
