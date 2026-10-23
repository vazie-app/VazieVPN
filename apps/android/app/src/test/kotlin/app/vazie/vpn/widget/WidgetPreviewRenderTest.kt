package app.vazie.vpn.widget

import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.glance.VazieGlanceTheme
import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.widget.dashboard.components.DashboardContent
import app.vazie.vpn.widget.presentation.WidgetVariant
import app.vazie.vpn.widget.presentation.widgetLayout
import app.vazie.vpn.widget.quickconnect.components.QuickConnectContent
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** Renders the widget picker previews from the real widgets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class WidgetPreviewRenderTest {

    private val out = File("build/widget-previews").apply { mkdirs() }

    @Test
    fun `both widgets render in both themes and both languages`() {
        for (language in listOf("en", "ru")) {
            RuntimeEnvironment.setQualifiers("$language-xxhdpi")
            for (subject in PreviewSubject.entries) {
                val file = File(out, "${subject.fileName}-$language.png")
                render(subject, file)
                assertTrue(file.length() > 1_000, "${file.name} is empty")
            }
        }
    }

    private fun render(subject: PreviewSubject, file: File) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Internal to Kotlin, public in bytecode: reached the way Android Studio's preview reaches it.
        val adapter = Class.forName("androidx.glance.appwidget.preview.GlanceAppWidgetViewAdapter")
        val view = adapter.getConstructor(android.content.Context::class.java, android.util.AttributeSet::class.java)
            .newInstance(context, org.robolectric.Robolectric.buildAttributeSet().build()) as View
        val init = adapter.declaredMethods.first { it.name.startsWith("init-") }
        init.isAccessible = true
        init.invoke(view, PreviewSubjectsKt, subject.function, packedDpSize(subject.widthDp, subject.heightDp))

        val density = context.resources.displayMetrics.density
        val width = (subject.widthDp * density).toInt()
        val height = (subject.heightDp * density).toInt()

        // Hardware rendering: only it applies Glance's outline clip, so corners come out rounded.
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        // Transparent outside the card, as the launcher shows it: a preview on an opaque window would
        // carry white corners onto a dark picker.
        activity.window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        val frame = android.widget.FrameLayout(activity)
        frame.addView(view, android.widget.FrameLayout.LayoutParams(width, height))
        activity.setContentView(frame, android.view.ViewGroup.LayoutParams(width, height))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val location = IntArray(2).also(view::getLocationInWindow)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var result = -1
        android.view.PixelCopy.request(
            activity.window,
            android.graphics.Rect(location[0], location[1], location[0] + width, location[1] + height),
            bitmap,
            { code -> result = code },
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        check(result == android.view.PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
        check(android.graphics.Color.alpha(bitmap.getPixel(0, 0)) == 0) { "${file.name}: the corner outside the card is not transparent" }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** `DpSize` is a value class over two packed floats; the adapter's init takes the raw long. */
    private fun packedDpSize(width: Float, height: Float): Long =
        (java.lang.Float.floatToRawIntBits(width).toLong() shl 32) or
            (java.lang.Float.floatToRawIntBits(height).toLong() and 0xffffffffL)

    private companion object {
        const val PreviewSubjectsKt = "app.vazie.vpn.widget.WidgetPreviewRenderTestKt"
    }
}

/** Each preview: which composable, at which cell (VazieWidgetSizes' targets), into which file. */
internal enum class PreviewSubject(val function: String, val fileName: String, val widthDp: Float, val heightDp: Float) {
    QUICK_MILK("quickConnectMilk", "quick_connect-day", VazieWidgetSizes.squareTarget.width.value, VazieWidgetSizes.squareTarget.height.value),
    QUICK_NIGHT("quickConnectNight", "quick_connect-night", VazieWidgetSizes.squareTarget.width.value, VazieWidgetSizes.squareTarget.height.value),
    DASHBOARD_MILK("dashboardMilk", "dashboard-day", VazieWidgetSizes.wideTarget.width.value, VazieWidgetSizes.wideTarget.height.value),
    DASHBOARD_NIGHT("dashboardNight", "dashboard-night", VazieWidgetSizes.wideTarget.width.value, VazieWidgetSizes.wideTarget.height.value),
}

@Composable
fun quickConnectMilk() = QuickConnectPreview(Appearance.MILK)

@Composable
fun quickConnectNight() = QuickConnectPreview(Appearance.NIGHT_INDIGO)

@Composable
fun dashboardMilk() = DashboardPreview(Appearance.MILK)

@Composable
fun dashboardNight() = DashboardPreview(Appearance.NIGHT_INDIGO)

@Composable
private fun QuickConnectPreview(appearance: Appearance) = VazieGlanceTheme(appearance = appearance) {
    QuickConnectContent(
        state = WidgetFixtures.idle,
        layout = widgetLayout(
            size = LocalSize.current,
            variant = WidgetVariant.QUICK_CONNECT,
            dimens = VazieGlanceTheme.dimens,
            fontScale = LocalContext.current.resources.configuration.fontScale,
            tunnelIsUp = false,
        ),
    )
}

@Composable
private fun DashboardPreview(appearance: Appearance) = VazieGlanceTheme(appearance = appearance) {
    DashboardContent(
        state = WidgetFixtures.idle,
        layout = widgetLayout(
            size = LocalSize.current,
            variant = WidgetVariant.DASHBOARD,
            dimens = VazieGlanceTheme.dimens,
            fontScale = LocalContext.current.resources.configuration.fontScale,
            tunnelIsUp = false,
        ),
    )
}
