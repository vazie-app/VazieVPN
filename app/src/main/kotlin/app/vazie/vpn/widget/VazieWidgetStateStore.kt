package app.vazie.vpn.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.LastUsed

/** [VazieWidgetState] as flat preferences, because that is the only shape a Glance widget can be handed state
 * in. */
internal object VazieWidgetStateStore {

    fun read(preferences: Preferences): VazieWidgetState {
        val selected = preferences.readSubject() ?: return VazieWidgetState.NoConfiguration
        return VazieWidgetState.Configured(
            selected = selected,
            connection = preferences.readConnection(),
            shortcuts = preferences.readShortcuts(),
            lastUsed = preferences.readLastUsed(),
        )
    }

    /** The app's appearance the widget draws in. Unknown or absent reads as the default. */
    fun readAppearance(preferences: Preferences): Appearance =
        preferences[AppearanceKey]?.let { raw -> Appearance.entries.firstOrNull { it.name == raw } } ?: Appearance.Default

    fun write(preferences: MutablePreferences, state: VazieWidgetState, appearance: Appearance = Appearance.Default) {
        preferences.clear()
        preferences[AppearanceKey] = appearance.name
        val configured = state as? VazieWidgetState.Configured ?: return
        configured.selected.write(preferences)
        preferences[Connection] = configured.connection.key
        preferences[ShortcutCount] = configured.shortcuts.size
        configured.shortcuts.forEachIndexed { index, shortcut ->
            preferences[shortcutId(index)] = shortcut.id
            preferences[shortcutName(index)] = shortcut.name
            preferences[shortcutMark(index)] = shortcut.mark
            preferences[shortcutProtocol(index)] = shortcut.protocolLabel
        }
        configured.lastUsed.write(preferences)
    }

    /** The subject, written flat. */
    private fun WidgetSubjectUi.write(preferences: MutablePreferences) {
        preferences[ConfigName] = name
        preferences[ConfigId] = id
        preferences[ConfigMark] = mark
        preferences[ConfigProtocol] = protocolLabel
    }

    private fun Preferences.readSubject(): WidgetSubjectUi? {
        val id = this[ConfigId] ?: return null
        val name = this[ConfigName] ?: return null
        return WidgetSubjectUi(
            id = id,
            name = name,
            mark = this[ConfigMark].orEmpty(),
            protocolLabel = this[ConfigProtocol].orEmpty(),
        )
    }

    private fun Preferences.readConnection(): WidgetConnectionUi = when (this[Connection]) {
        PREPARING -> WidgetConnectionUi.Preparing
        CONNECTING -> WidgetConnectionUi.Connecting
        CONNECTED -> WidgetConnectionUi.Connected
        DISCONNECTING -> WidgetConnectionUi.Disconnecting
        FAILED -> WidgetConnectionUi.Failed
        NO_INTERNET -> WidgetConnectionUi.NoInternet
        else -> WidgetConnectionUi.Idle
    }


    private fun Preferences.readShortcuts(): List<WidgetConfigurationUi> {
        val count = this[ShortcutCount] ?: 0
        return (0 until count).mapNotNull { index ->
            val id = this[shortcutId(index)] ?: return@mapNotNull null
            val name = this[shortcutName(index)] ?: return@mapNotNull null
            WidgetConfigurationUi(
                id = id,
                name = name,
                mark = this[shortcutMark(index)].orEmpty(),
                protocolLabel = this[shortcutProtocol(index)].orEmpty(),
            )
        }
    }

    /** The bucket travels, not a formatted string. */
    private fun LastUsed.write(preferences: MutablePreferences) {
        preferences[LastUsedKind] = when (this) {
            LastUsed.Never -> LAST_USED_NEVER
            LastUsed.JustNow -> LAST_USED_JUST_NOW
            is LastUsed.Today -> LAST_USED_TODAY
            LastUsed.Yesterday -> LAST_USED_YESTERDAY
            is LastUsed.Earlier -> LAST_USED_EARLIER
        }
        when (this) {
            is LastUsed.Today -> {
                preferences[LastUsedHour] = hour
                preferences[LastUsedMinute] = minute
            }

            is LastUsed.Earlier -> {
                preferences[LastUsedYear] = year
                preferences[LastUsedMonth] = month
                preferences[LastUsedDay] = day
            }

            else -> Unit
        }
    }

    private fun Preferences.readLastUsed(): LastUsed = when (this[LastUsedKind]) {
        LAST_USED_JUST_NOW -> LastUsed.JustNow
        LAST_USED_TODAY -> LastUsed.Today(
            hour = this[LastUsedHour] ?: 0,
            minute = this[LastUsedMinute] ?: 0,
        )

        LAST_USED_YESTERDAY -> LastUsed.Yesterday
        LAST_USED_EARLIER -> LastUsed.Earlier(
            year = this[LastUsedYear] ?: 0,
            month = this[LastUsedMonth] ?: 1,
            day = this[LastUsedDay] ?: 1,
        )

        else -> LastUsed.Never
    }

    private val ConfigId = stringPreferencesKey("config_id")
    private val ConfigName = stringPreferencesKey("config_name")
    private val ConfigMark = stringPreferencesKey("config_mark")
    private val ConfigProtocol = stringPreferencesKey("config_protocol")
    private val Connection = stringPreferencesKey("connection")
    private val ShortcutCount = intPreferencesKey("shortcut_count")
    private val LastUsedKind = stringPreferencesKey("last_used_kind")
    private val LastUsedHour = intPreferencesKey("last_used_hour")
    private val LastUsedMinute = intPreferencesKey("last_used_minute")
    private val LastUsedYear = intPreferencesKey("last_used_year")
    private val LastUsedMonth = intPreferencesKey("last_used_month")
    private val LastUsedDay = intPreferencesKey("last_used_day")


    private fun shortcutId(index: Int) = stringPreferencesKey("shortcut_${index}_id")
    private fun shortcutName(index: Int) = stringPreferencesKey("shortcut_${index}_name")
    private fun shortcutMark(index: Int) = stringPreferencesKey("shortcut_${index}_mark")
    private fun shortcutProtocol(index: Int) = stringPreferencesKey("shortcut_${index}_protocol")


    private const val IDLE = "idle"
    private const val PREPARING = "preparing"
    private const val CONNECTING = "connecting"
    private const val CONNECTED = "connected"
    private const val DISCONNECTING = "disconnecting"
    private const val FAILED = "failed"
    private const val NO_INTERNET = "no_internet"

    private const val LAST_USED_NEVER = "never"
    private const val LAST_USED_JUST_NOW = "just_now"
    private const val LAST_USED_TODAY = "today"
    private const val LAST_USED_YESTERDAY = "yesterday"
    private const val LAST_USED_EARLIER = "earlier"

    private val WidgetConnectionUi.key: String
        get() = when (this) {
            WidgetConnectionUi.Idle -> IDLE
            WidgetConnectionUi.Preparing -> PREPARING
            WidgetConnectionUi.Connecting -> CONNECTING
            WidgetConnectionUi.Connected -> CONNECTED
            WidgetConnectionUi.Disconnecting -> DISCONNECTING
            WidgetConnectionUi.Failed -> FAILED
            WidgetConnectionUi.NoInternet -> NO_INTERNET
        }

    private val AppearanceKey = stringPreferencesKey("appearance")
}
