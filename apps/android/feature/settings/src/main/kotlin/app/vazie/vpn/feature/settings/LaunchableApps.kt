package app.vazie.vpn.feature.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.vazie.vpn.core.model.SplitTunnel
import java.text.Collator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Apps with a launcher icon, other than Vazie, sorted by name. Only launcher apps are visible to Vazie
 * (a `<queries>` intent in the manifest), so no broad package-visibility permission is needed. */
internal suspend fun Context.launchableApps(): ImmutableList<LaunchableApp> = withContext(Dispatchers.IO) {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val collator = Collator.getInstance()
    runCatching { packageManager.queryIntentActivities(launcher, 0) }.getOrDefault(emptyList())
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != packageName && SplitTunnel.isPackageName(it.packageName) }
        .map { LaunchableApp(it.packageName, packageManager.getApplicationLabel(it).toString()) }
        .sortedWith { a, b -> collator.compare(a.label, b.label) }
        .toImmutableList()
}

/** An app's icon as a bitmap, loaded off the main thread; `null` until it arrives or if there is none. */
@Composable
internal fun rememberAppIcon(packageName: String, sizePx: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, packageName, sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = (drawable as? BitmapDrawable)?.bitmap?.let { Bitmap.createScaledBitmap(it, sizePx, sizePx, true) }
                    ?: Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { target ->
                        drawable.setBounds(0, 0, sizePx, sizePx)
                        drawable.draw(Canvas(target))
                    }
                bitmap.asImageBitmap()
            }.getOrNull()
        }
    }
}
