package app.vazie.vpn.widget.presentation

import android.content.Context
import app.vazie.vpn.R
import app.vazie.vpn.core.model.LastUsed
import app.vazie.vpn.widget.WidgetConnectionUi

/** What the one supporting line says, if there is one. */
internal enum class ReadingsKind { LAST_USED, NONE }

/** What the supporting line says, decided by what the subject *is*. */
internal fun readingsKind(
    connection: WidgetConnectionUi,
    layout: WidgetLayout,
): ReadingsKind = when {
    !layout.showsReading -> ReadingsKind.NONE
    // A connected widget shows the session, not a fact about the past.
    connection is WidgetConnectionUi.Connected -> ReadingsKind.NONE
    else -> ReadingsKind.LAST_USED
}

/** When a saved configuration last carried traffic. */
internal fun lastUsedText(context: Context, lastUsed: LastUsed): String = when (lastUsed) {
    LastUsed.Never -> context.getString(R.string.widget_last_used_never)
    LastUsed.JustNow -> context.getString(R.string.widget_last_used_just_now)
    is LastUsed.Today -> context.getString(
        R.string.widget_last_used_today,
        "%02d:%02d".format(lastUsed.hour, lastUsed.minute),
    )

    LastUsed.Yesterday -> context.getString(R.string.widget_last_used_yesterday)
    is LastUsed.Earlier -> context.getString(
        R.string.widget_last_used_earlier,
        "%02d.%02d.%04d".format(lastUsed.day, lastUsed.month, lastUsed.year),
    )
}

/** How many characters the row has before the launcher starts cutting it. */

/** A count chip costs room too, so the budget is smaller when there is going to be one. */

