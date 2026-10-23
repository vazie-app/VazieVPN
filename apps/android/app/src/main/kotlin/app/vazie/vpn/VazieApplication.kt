package app.vazie.vpn

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** The process. */
@HiltAndroidApp
class VazieApplication : Application() {

    @Inject
    lateinit var startup: VazieStartup

    override fun onCreate() {
        super.onCreate()
        startup.run()
    }
}
