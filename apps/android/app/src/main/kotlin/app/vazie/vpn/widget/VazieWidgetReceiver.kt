package app.vazie.vpn.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** The base every Vazie widget receiver shares: ask for state whenever a widget appears or changes shape. */
abstract class VazieWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        refresh(context)
    }

    private fun refresh(context: Context) {
        val publisher = runCatching {
            EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetPublisherEntryPoint::class.java)
                .publisher()
        }.getOrNull() ?: return
        val pending = goAsync()
        publisher.refresh { runCatching { pending.finish() } }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetPublisherEntryPoint {
    fun publisher(): VazieWidgetPublisher
}
