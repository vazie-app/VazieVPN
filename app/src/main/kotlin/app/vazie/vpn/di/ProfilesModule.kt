package app.vazie.vpn.di

import android.content.Context
import app.vazie.vpn.data.profiles.ProfileStorage
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.config.subscription.SubscriptionReader
import app.vazie.vpn.config.subscription.SubscriptionSource
import app.vazie.vpn.data.subscriptions.HttpSubscriptionSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProfilesModule {

    @Provides
    @Singleton
    fun profileRepository(@ApplicationContext context: Context): VpnProfileRepository =
        ProfileStorage.repository(filesDir = context.filesDir)

    @Provides
    @Singleton
    fun configParsers(): ConfigParserRegistry = ConfigParserRegistry.default()

    /** A subscription's list is read by the same parsers as a single link, so it can hold nothing they
     * would not accept on their own. */
    @Provides
    fun subscriptionReader(parsers: ConfigParserRegistry): SubscriptionReader = SubscriptionReader(parsers)

    @Provides
    @Singleton
    fun subscriptionSource(): SubscriptionSource = HttpSubscriptionSource.create()
}
