package app.vazie.vpn

import app.vazie.vpn.data.AppPreferences
import android.content.Context
import app.vazie.vpn.icon.AppIconSwitcher
import app.vazie.vpn.icon.LauncherIconArt
import app.vazie.vpn.icon.VazieLauncherFace
import app.vazie.vpn.usage.ProfileUsageRecorder
import app.vazie.vpn.shortcuts.VazieShortcuts
import app.vazie.vpn.widget.VazieWidgetPublisher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Everything the application does once, at process start, in one place. */
@Singleton
class VazieStartup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val widgets: VazieWidgetPublisher,
    private val preferences: AppPreferences,
    private val icons: AppIconSwitcher,
    private val shortcuts: VazieShortcuts,
    private val usage: ProfileUsageRecorder,
    // One-off work contributed through Hilt (`RetiredStorageModule`): clearing what earlier builds left behind.
    private val tasks: Set<@JvmSuppressWildcards StartupTask>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun run() {
        // The widget publisher is expensive, so it starts only when system integrations are enabled.
        if (BuildConfig.SYSTEM_INTEGRATIONS_ENABLED) widgets.start()
        // Keeps the launcher shortcuts in step with the stored profiles.
        shortcuts.start()
        // The one writer of "last used", whichever surface raised the tunnel. See ProfileUsageRecorder.
        usage.start()
        // Nothing else: a first launch writes no profile, selects nothing and opens no socket.
        launcherFace()
        tasks.forEach { task -> scope.launch { task.run() } }
    }

    /** Keep the launcher entry in step with the stored mark and plate. */
    private fun launcherFace() {
        scope.launch {
            preferences.observe()
                .map { stored ->
                    VazieLauncherFace(
                        icon = stored.appIcon,
                        plate = LauncherIconArt.resolvePlate(stored.appIconPlate, stored.appIcon),
                    )
                }
                .distinctUntilChanged()
                .collect { face -> icons.reconcile(face) }
        }
    }

}
