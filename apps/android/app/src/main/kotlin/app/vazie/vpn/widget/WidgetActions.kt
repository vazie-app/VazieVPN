package app.vazie.vpn.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import app.vazie.vpn.MainActivity
import app.vazie.vpn.connection.LaunchOutcome
import app.vazie.vpn.connection.VazieConnectionLauncher
import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.feature.connections.CONNECTIONS_DEEP_LINK
import app.vazie.vpn.feature.home.HOME_DEEP_LINK
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.runtime.VpnPermission
import app.vazie.vpn.widget.presentation.WidgetAction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Turns a [WidgetAction] into something a tap can do. */
@Composable
internal fun WidgetAction.toGlanceAction(): Action = when (this) {
    WidgetAction.Connect -> actionRunCallback<VazieWidgetActionCallback>(
        actionParametersOf(VazieWidgetActionCallback.Command to COMMAND_CONNECT)
    )

    WidgetAction.Disconnect -> actionRunCallback<VazieWidgetActionCallback>(
        actionParametersOf(VazieWidgetActionCallback.Command to COMMAND_DISCONNECT)
    )

    WidgetAction.OpenHome -> openApp(HOME_DEEP_LINK)
    WidgetAction.OpenConnections -> openApp(CONNECTIONS_DEEP_LINK)
    is WidgetAction.Select -> actionRunCallback<VazieWidgetActionCallback>(
        actionParametersOf(
            VazieWidgetActionCallback.Command to COMMAND_SELECT,
            VazieWidgetActionCallback.ProfileId to profileId,
        )
    )
}

/** The deep links are the ones the features already declare and the manifest already filters, so these are
 * the same URLs a browser or another app would use — not a private back door. */
@Composable
private fun openApp(deepLink: String): Action {
    val context = LocalContext.current
    return actionStartActivity(
        Intent(Intent.ACTION_VIEW, deepLink.toUri())
            // Named explicitly so the tap cannot open a chooser or resolve to anything but Vazie.
            .setClass(context, MainActivity::class.java)
    )
}

/** What a widget's Connect or Disconnect actually runs. */
internal class VazieWidgetActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetControlEntryPoint::class.java,
        )
        when (parameters[Command]) {
            COMMAND_SELECT -> parameters[ProfileId]?.let { id ->
                entryPoint.selected().select(ProfileId(id))
            }

            COMMAND_DISCONNECT -> entryPoint.launcher().disconnect()

            COMMAND_CONNECT -> connect(context, entryPoint.launcher())

            else -> Unit
        }
    }

    /** Connect to the selected configuration through the one seam every surface uses. */
    private suspend fun connect(context: Context, launcher: VazieConnectionLauncher) {
        if (VpnPermission.consentIntent(context) != null) return openHome(context)
        when (launcher.connect()) {
            LaunchOutcome.Started -> Unit
            // Everything a widget cannot explain goes to the screen that can.
            LaunchOutcome.PermissionRequired,
            LaunchOutcome.Refused,
                -> openHome(context)

            LaunchOutcome.NothingSelected -> openConnections(context)
        }
    }

    private fun openHome(context: Context) = open(context, HOME_DEEP_LINK)

    private fun openConnections(context: Context) = open(context, CONNECTIONS_DEEP_LINK)

    private fun open(context: Context, deepLink: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, deepLink.toUri())
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        val Command = ActionParameters.Key<String>("vazie.widget.command")
        val ProfileId = ActionParameters.Key<String>("vazie.widget.profile")
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetControlEntryPoint {
    fun launcher(): VazieConnectionLauncher
    fun selected(): SelectedProfileStore
}

private const val COMMAND_CONNECT = "connect"
private const val COMMAND_DISCONNECT = "disconnect"
private const val COMMAND_SELECT = "select"
