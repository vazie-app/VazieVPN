package app.vazie.vpn.system

import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import app.vazie.vpn.R
import app.vazie.vpn.tile.VazieTileService
import app.vazie.vpn.widget.quickconnect.QuickConnectWidgetReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The two places Vazie can put itself, and whether this device will let it. */
@Singleton
class SystemIntegration @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Whether the launcher on this device implements the pin-widget request. */
    fun canAddWidget(): Boolean = runCatching {
        AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true
    }.getOrDefault(false)

    /** Ask the launcher to place the Quick Connect widget. */
    fun requestAddWidget(): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        manager.requestPinAppWidget(
            ComponentName(context, QuickConnectWidgetReceiver::class.java),
            /* extras = */ null,
            /* successCallback = */ null as PendingIntent?,
        )
    }.getOrDefault(false)

    /** Whether there is a notification-settings screen to send somebody to. */
    fun canOpenNotificationSettings(): Boolean = runCatching {
        notificationSettingsIntent().resolveActivity(context.packageManager) != null
    }.getOrDefault(false)

    /** Whether Android will currently let Vazie post its status notification. */
    fun notificationsEnabled(): Boolean = runCatching {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }.getOrDefault(false)

    /** Open Android's notification settings for Vazie. */
    fun openNotificationSettings(): Boolean = runCatching {
        context.startActivity(notificationSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    private fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Whether this Android can be asked to add a Quick Settings tile. */
    fun canAddQuickSettingsTile(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { context.getSystemService<StatusBarManager>() != null }.getOrDefault(false)

    /** Ask System UI to add the Vazie tile. */
    fun requestAddQuickSettingsTile(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return requestTile()
    }

    /** Split out only so the version check lint traces by data flow sits directly above the call. Behaviour
     * is entirely [requestAddQuickSettingsTile]'s. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestTile(): Boolean = runCatching {
        val manager = context.getSystemService<StatusBarManager>() ?: return false
        manager.requestAddTileService(
            ComponentName(context, VazieTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile_vazie),
            context.mainExecutor,
        ) { /* System UI has already told them. */ }
        true
    }.getOrDefault(false)
}
