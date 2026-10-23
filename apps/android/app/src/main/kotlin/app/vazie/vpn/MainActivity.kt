package app.vazie.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.vazie.vpn.core.designsystem.site.LocalVazieSite
import app.vazie.vpn.core.designsystem.site.VazieSite
import app.vazie.vpn.system.SystemIntegration
import app.vazie.vpn.ui.VazieApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Whether this device will let Vazie put a widget on a home screen or a tile in the panel. */
    @Inject
    lateinit var systemIntegration: SystemIntegration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The site from the build (`vazie.site.url`), for the legal links and for paying on the site.
            CompositionLocalProvider(LocalVazieSite provides VazieSite(BuildConfig.SITE_URL)) {
                VazieApp(systemIntegration = systemIntegration)
            }
        }
    }
}
