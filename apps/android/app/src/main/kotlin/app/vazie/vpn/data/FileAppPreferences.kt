package app.vazie.vpn.data

import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.SplitTunnel
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.core.model.VazieGuideId
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** [AppPreferences] as one small `key=value` file under `filesDir`. */
internal class FileAppPreferences(
    private val file: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppPreferences {

    private val mutex = Mutex()
    private val cache = MutableStateFlow(readFromDisk())

    override fun snapshot(): AppPreferencesState = cache.value

    // A StateFlow already drops values equal to the previous one, which is the whole of what a
    // collector here needs.
    override fun observe(): Flow<AppPreferencesState> = cache

    // Two facts, one update, one write, one rename. See `AppPreferences.setOnboardingCompleted`.
    override suspend fun setOnboardingCompleted(completed: Boolean) = update {
        it.copy(
            onboardingCompleted = completed,
            tourState = if (completed) TourState.PENDING else it.tourState,
        )
    }

    override suspend fun setTourDone() = update { it.copy(tourState = TourState.DONE) }

    override suspend fun setConfigurationGuidanceSeen() =
        update { it.copy(configurationGuidanceSeen = true) }

    override suspend fun setSettingsGuidanceSeen() =
        update { it.copy(settingsGuidanceSeen = true) }

    override suspend fun setAppIcon(icon: VazieAppIcon) = update { it.copy(appIcon = icon) }

    override suspend fun setAppIconPlate(plate: VazieAppIconPlate) =
        update { it.copy(appIconPlate = plate) }

    override suspend fun setAppearance(appearance: Appearance) =
        update { it.copy(appearance = appearance) }

    override suspend fun acknowledgeGuide(guide: VazieGuideId) =
        update { it.copy(acknowledgedGuides = it.acknowledgedGuides + guide) }

    override suspend fun setNotificationExplained(explained: Boolean) =
        update { it.copy(notificationExplained = explained) }

    override suspend fun setSplitTunnel(split: SplitTunnel) = update { it.copy(splitTunnel = split) }

    private suspend fun update(transform: (AppPreferencesState) -> AppPreferencesState) {
        mutex.withLock {
            val updated = transform(cache.value)
            if (updated == cache.value) return@withLock
            // The in-memory value moves first, so the screen that asked for the change repaints on
            // this frame rather than after a disk write it has no reason to wait for.
            cache.value = updated
            withContext(dispatcher) { writeToDisk(updated) }
        }
    }

    private fun readFromDisk(): AppPreferencesState {
        val entries = runCatching {
            file.takeIf { it.exists() }
                ?.readLines()
                ?.mapNotNull { line ->
                    val separator = line.indexOf(SEPARATOR)
                    if (separator <= 0) return@mapNotNull null
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
                ?.toMap()
        }.getOrNull().orEmpty()
        return AppPreferencesState(
            onboardingCompleted = entries[KEY_ONBOARDING_COMPLETED] == TRUE,
            appIcon = VazieAppIcon.fromId(entries[KEY_APP_ICON]),
            appIconPlate = VazieAppIconPlate.fromId(entries[KEY_APP_ICON_PLATE]),
            appearance = Appearance.fromStoredName(entries[KEY_APPEARANCE]),
            acknowledgedGuides = entries[KEY_ACKNOWLEDGED_GUIDES]
                .orEmpty()
                .split(LIST_SEPARATOR)
                .mapNotNull { VazieGuideId.fromId(it.trim()) }
                .toSet(),
            notificationExplained = entries[KEY_NOTIFICATION_EXPLAINED] == TRUE,
            tourState = TourState.entries.byNameOr(entries[KEY_TOUR_STATE], TourState.DONE),
            configurationGuidanceSeen = entries[KEY_CONFIG_GUIDANCE_SEEN] == TRUE,
            settingsGuidanceSeen = entries[KEY_SETTINGS_GUIDANCE_SEEN] == TRUE,
            splitTunnel = SplitTunnel(
                mode = SplitTunnel.Mode.fromId(entries[KEY_SPLIT_TUNNEL_MODE]),
                packages = entries[KEY_SPLIT_TUNNEL_APPS].orEmpty().split(LIST_SEPARATOR)
                    .map { it.trim() }
                    .filter { SplitTunnel.isPackageName(it) }
                    .toSet(),
            ),
        )
    }

    private fun writeToDisk(state: AppPreferencesState) {
        runCatching {
            file.parentFile?.mkdirs()
            val text = buildString {
                line(KEY_ONBOARDING_COMPLETED, if (state.onboardingCompleted) TRUE else FALSE)
                line(KEY_APP_ICON, state.appIcon.id)
                line(KEY_APP_ICON_PLATE, state.appIconPlate.id)
                line(KEY_APPEARANCE, state.appearance.name)
                line(
                    KEY_ACKNOWLEDGED_GUIDES,
                    // Sorted, so the file does not churn because a set iterated in a different
                    // order.
                    state.acknowledgedGuides.map { it.id }.sorted().joinToString(LIST_SEPARATOR.toString()),
                )
                line(KEY_NOTIFICATION_EXPLAINED, if (state.notificationExplained) TRUE else FALSE)
                line(KEY_TOUR_STATE, state.tourState.name)
                line(KEY_CONFIG_GUIDANCE_SEEN, if (state.configurationGuidanceSeen) TRUE else FALSE)
                line(KEY_SETTINGS_GUIDANCE_SEEN, if (state.settingsGuidanceSeen) TRUE else FALSE)
                line(KEY_SPLIT_TUNNEL_MODE, state.splitTunnel.mode.id)
                line(KEY_SPLIT_TUNNEL_APPS, state.splitTunnel.packages.sorted().joinToString(LIST_SEPARATOR.toString()))
            }
            val temporary = File(file.parentFile, file.name + TEMPORARY_SUFFIX)
            temporary.writeText(text)
            if (!temporary.renameTo(file)) {
                file.writeText(text)
                temporary.delete()
            }
        }
    }

    private fun StringBuilder.line(key: String, value: String) {
        append(key).append(SEPARATOR).append(value).append('\n')
    }

    private companion object {
        const val SEPARATOR = '='
        const val TEMPORARY_SUFFIX = ".tmp"
        const val TRUE = "true"
        const val FALSE = "false"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_APP_ICON = "app_icon"
        const val KEY_APP_ICON_PLATE = "app_icon_plate"
        const val KEY_APPEARANCE = "appearance"

        /** The key predates the vocabulary, and is deliberately not renamed. */
        const val KEY_ACKNOWLEDGED_GUIDES = "dismissed_hints"
        const val KEY_NOTIFICATION_EXPLAINED = "notification_explained"
        const val KEY_TOUR_STATE = "tour_state"
        const val KEY_CONFIG_GUIDANCE_SEEN = "config_guidance_seen"
        const val KEY_SETTINGS_GUIDANCE_SEEN = "settings_guidance_seen"
        const val KEY_SPLIT_TUNNEL_MODE = "split_tunnel_mode"
        const val KEY_SPLIT_TUNNEL_APPS = "split_tunnel_apps"
        const val LIST_SEPARATOR = ','
    }
}

/** A stored enum name, or the default when the file says something this build has never heard of. */
private fun <T : Enum<T>> List<T>.byNameOr(name: String?, fallback: T): T =
    firstOrNull { it.name == name } ?: fallback

/** Where the preference file lives, in one place, so a test and the app agree on it. */
object AppStorage {
    const val DIRECTORY: String = "vazie-app"
    private const val FILE_NAME = "preferences"

    /** [dispatcher] is a parameter because a test needs writes to finish on a scheduler it controls. Reads
     * take no dispatcher at all — see [FileAppPreferences] for why they are synchronous. */
    fun preferences(
        filesDir: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): AppPreferences = FileAppPreferences(
        file = File(File(filesDir, DIRECTORY), FILE_NAME),
        dispatcher = dispatcher,
    )
}
