package app.vazie.vpn.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.net.toUri
import app.vazie.vpn.MainActivity
import app.vazie.vpn.connection.LaunchOutcome
import app.vazie.vpn.connection.VazieConnectionLauncher
import app.vazie.vpn.feature.config.ADD_CONFIG_DEEP_LINK
import app.vazie.vpn.feature.home.HOME_CONNECT_DEEP_LINK
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.runtime.VpnPermission
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Vazie in the Quick Settings panel: a view of the connection, and one command into it. */
@AndroidEntryPoint
class VazieTileService : TileService() {

    @Inject
    lateinit var controller: VpnConnectionController

    /** The tile asks one object what Connect means and how to do it, and that object is the same one the
     * widgets ask. */
    @Inject
    lateinit var launcher: VazieConnectionLauncher

    private var scope: CoroutineScope? = null
    private var clickJob: Job? = null
    private var presentation: VazieTilePresentation? = null

    override fun onStartListening() {
        super.onStartListening()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        scope.launch {
            combine(
                controller.snapshot,
                launcher.observeSelected(),
            ) { snapshot, selection -> snapshot to (selection != null) }
                .collect { (snapshot, hasSelection) -> render(snapshot, hasSelection) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        // Whatever the panel is currently showing is what the user just acted on. Re-deriving the
        // action here from a fresh read could act on a state they never saw.
        when (val action = presentation?.action ?: VazieTileAction.OpenApp(TileDestination.CONNECT)) {
            VazieTileAction.Disconnect -> runInBackground { launcher.disconnect() }
            is VazieTileAction.OpenApp -> openApp(action.destination)
            VazieTileAction.Connect -> connect()
        }
    }

    /** Connect to whatever is selected, by whichever route that kind of connection takes. */
    private fun connect() {
        if (VpnPermission.consentIntent(this) != null) {
            openApp(TileDestination.CONNECT)
            return
        }
        runInBackground {
            when (launcher.connect()) {
                LaunchOutcome.Started -> Unit
                LaunchOutcome.PermissionRequired, LaunchOutcome.Refused ->
                    openApp(TileDestination.CONNECT)

                LaunchOutcome.NothingSelected -> openApp(TileDestination.ADD_CONFIGURATION)
            }
        }
    }

    /** Work that has to outlive the click but not the panel. */
    private fun runInBackground(block: suspend () -> Unit) {
        val scope = scope ?: return
        clickJob?.cancel()
        clickJob = scope.launch { block() }
    }

    /** The subtitle needs API 29; on 28 the state is carried by the tile state and the content
     * description. */
    private fun render(snapshot: VpnConnectionSnapshot, hasSelection: Boolean) {
        val next = tilePresentation(snapshot, hasSelection)
        presentation = next
        val tile = qsTile ?: return
        val label = getString(next.labelRes)
        val status = getString(next.subtitleRes)
        tile.state = next.tileState
        tile.label = label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = status
        tile.contentDescription = "$label · $status"
        tile.updateTile()
    }

    private fun openApp(destination: TileDestination) {
        val deepLink = when (destination) {
            TileDestination.CONNECT -> HOME_CONNECT_DEEP_LINK
            TileDestination.ADD_CONFIGURATION -> ADD_CONFIG_DEEP_LINK
        }
        val intent = Intent(Intent.ACTION_VIEW, deepLink.toUri())
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // A locked device must not be handed a VPN consent dialog behind the lock screen. `unlockAndRun` is
        // the platform's own answer, and on an unlocked device it runs immediately.
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pending)
            } else {
                // The deprecated `Intent` overload is the only one below API 34.
                @Suppress("DEPRECATION")
                @SuppressLint("StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        }
    }

    private companion object {
        const val REQUEST_OPEN = 3
    }
}
