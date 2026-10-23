package app.vazie.vpn.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The notification that has to be there while a tunnel is. */
@Singleton
class VpnNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun build(snapshot: VpnConnectionSnapshot?): Notification {
        ensureChannel()
        val state = snapshot?.state ?: VpnConnectionState.Preparing
        val builder = base(state)
            // The configuration name, so the notification is private: it shows on the lock screen and in
            // history.
            .setSubText(snapshot?.subject?.displayName)
            // Private with a public counterpart, rather than a decision made on the user's behalf.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base(state).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build())
        return builder.build()
    }

    /** Everything both versions say: what the tunnel is doing, how to open Vazie, how to stop it. */
    private fun base(state: VpnConnectionState): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vazie_status)
            .setContentTitle(context.getString(R.string.vpn_notification_title))
            .setContentText(context.getString(state.textRes()))
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp())

        if (state.isStoppable()) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    /* icon = */ 0,
                    context.getString(R.string.vpn_notification_disconnect),
                    disconnect(),
                ).build()
            )
        }
        return builder
    }

    /** Redraws the notification, if the user allowed one. */
    fun update(context: Context, snapshot: VpnConnectionSnapshot) {
        // Inline so lint can trace the permission check; `runCatching` covers a revoke in between.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(snapshot))
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = context.getString(R.string.vpn_notification_channel_description)
            }
        )
    }

    /** Opens Vazie through its public deep link: `:vpn:runtime` must not see `:app`. */
    private fun openApp(): PendingIntent? {
        val intent = Intent(Intent.ACTION_VIEW, HOME_DEEP_LINK.toUri())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, REQUEST_OPEN, intent, IMMUTABLE)
    }

    private fun disconnect(): PendingIntent {
        val intent = Intent(context, VazieVpnService::class.java)
            .setAction(VazieVpnService.ACTION_DISCONNECT)
        return PendingIntent.getService(context, REQUEST_DISCONNECT, intent, IMMUTABLE)
    }

    private fun VpnConnectionState.textRes(): Int = when (this) {
        VpnConnectionState.Idle -> R.string.vpn_state_idle
        VpnConnectionState.Preparing -> R.string.vpn_state_preparing
        is VpnConnectionState.Connecting -> R.string.vpn_state_connecting
        is VpnConnectionState.Connected -> R.string.vpn_state_connected
        VpnConnectionState.Disconnecting -> R.string.vpn_state_disconnecting
        is VpnConnectionState.Failed -> R.string.vpn_state_failed
        VpnConnectionState.NoInternet -> R.string.vpn_state_no_internet
    }

    private fun VpnConnectionState.isStoppable(): Boolean = when (this) {
        VpnConnectionState.Idle,
        VpnConnectionState.Disconnecting,
        is VpnConnectionState.Failed,
        -> false

        else -> true
    }

    companion object {
        const val NOTIFICATION_ID: Int = 0x5A1E

        private const val CHANNEL_ID = "vazie.vpn.tunnel"
        private const val HOME_DEEP_LINK = "vazie-vpn://home"
        private const val REQUEST_OPEN = 1
        private const val REQUEST_DISCONNECT = 2
        private const val IMMUTABLE =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
