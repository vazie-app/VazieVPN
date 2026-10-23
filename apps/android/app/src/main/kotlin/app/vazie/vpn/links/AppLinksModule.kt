package app.vazie.vpn.links

import android.content.Context
import app.vazie.vpn.di.VazieApi
import app.vazie.vpn.data.links.AppLinksRepository
import app.vazie.vpn.data.links.FaqRepository
import app.vazie.vpn.data.links.LinksStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The About screen's links and the FAQ screen's questions, bound in every build type. One repository per process, so every visit to Settings
 * shares what was last learned and how recently it was asked. No session, no tunnel. */
@Module
@InstallIn(SingletonComponent::class)
object AppLinksModule {

    @Provides
    @Singleton
    fun appLinks(@ApplicationContext context: Context): AppLinksRepository = LinksStorage.repository(
        context = context,
        baseUrl = VazieApi.SHARED_BASE_URL,
        filesDir = context.filesDir,
    )

    /** The FAQ screen's questions; one per process, like the links. */
    @Provides
    @Singleton
    fun faq(@ApplicationContext context: Context): FaqRepository = LinksStorage.faqRepository(
        context = context,
        baseUrl = VazieApi.SHARED_BASE_URL,
        filesDir = context.filesDir,
    )
}
