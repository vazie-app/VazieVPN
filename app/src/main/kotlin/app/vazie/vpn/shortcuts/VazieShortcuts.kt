package app.vazie.vpn.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import app.vazie.vpn.MainActivity
import app.vazie.vpn.R
import app.vazie.vpn.core.designsystem.R as DesignSystemR
import app.vazie.vpn.feature.config.ADD_CONFIG_DEEP_LINK
import app.vazie.vpn.feature.home.homeConnectDeepLink
import app.vazie.vpn.api.VpnProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The list a launcher shows when somebody holds down the Vazie icon. */
@Singleton
class VazieShortcuts @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: VpnProfileRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            profiles.observeSummaries()
                .map { summaries ->
                    shortcutPlan(
                        profiles = summaries,
                        maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context),
                    )
                }
                .distinctUntilChanged()
                .collect(::publish)
        }
    }

    private fun publish(plan: List<VazieShortcut>) {
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, plan.map(::describe))
        }
    }

    private fun describe(shortcut: VazieShortcut): ShortcutInfoCompat = when (shortcut) {
        is VazieShortcut.Connect -> build(
            id = shortcut.id,
            shortLabel = shortcut.label,
            longLabel = context.getString(R.string.shortcut_connect_to, shortcut.label),
            icon = markIcon(shortcut.mark),
            deepLink = homeConnectDeepLink(shortcut.profileId.value),
        )

        VazieShortcut.AddConfiguration -> build(
            id = shortcut.id,
            shortLabel = context.getString(R.string.shortcut_add_config_short),
            longLabel = context.getString(R.string.shortcut_add_config_long),
            icon = IconCompat.createWithResource(context, R.drawable.ic_shortcut_add_config),
            deepLink = ADD_CONFIG_DEEP_LINK,
        )
    }

    /** A configuration's shortcut icon: its row mark - `VL` for a VLESS configuration - on the shortcut plate,
     * the way the lists draw it. */
    private fun markIcon(mark: String): IconCompat {
        val size = (ADAPTIVE_CANVAS_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(context, R.color.ic_shortcut_background))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK
            textAlign = Paint.Align.CENTER
            textSize = size * MARK_TEXT_FRACTION
            typeface = runCatching { ResourcesCompat.getFont(context, DesignSystemR.font.martian_mono) }
                .getOrNull()
                ?: Typeface.MONOSPACE
        }
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(mark.ifEmpty { "VL" }, size / 2f, baseline, paint)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    private fun build(
        id: String,
        shortLabel: String,
        longLabel: String,
        icon: IconCompat,
        deepLink: String,
    ): ShortcutInfoCompat = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(shortLabel)
        .setLongLabel(longLabel)
        .setIcon(icon)
        .setIntent(intentFor(deepLink))
        .build()

    /** `NEW_TASK` + `SINGLE_TOP` reuse an open Vazie; no `CLEAR_TASK`, so an open wizard is not thrown
     * away. */
    private fun intentFor(deepLink: String): Intent =
        Intent(Intent.ACTION_VIEW, deepLink.toUri())
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private companion object {
        /** The adaptive-icon canvas; the launcher masks it to its shape and keeps the middle 66dp. */
        const val ADAPTIVE_CANVAS_DP = 108

        /** Two monospaced letters sit comfortably inside the 66dp safe zone at this size. */
        const val MARK_TEXT_FRACTION = 0.22f

        /** Plain white, as on the widget. */
        const val MARK = 0xFFFFFFFF.toInt()
    }
}
