package app.vazie.vpn.icon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.vazie.vpn.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Puts the launcher entry back right after an update, before anybody opens the app. */
@AndroidEntryPoint
class LauncherIconRepairReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: AppPreferences

    @Inject
    lateinit var icons: AppIconSwitcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val stored = preferences.snapshot()
                icons.reconcile(
                    VazieLauncherFace(
                        icon = stored.appIcon,
                        plate = LauncherIconArt.resolvePlate(stored.appIconPlate, stored.appIcon),
                    ),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
