package app.vazie.vpn.widget

import android.content.Context
import app.vazie.vpn.core.model.LastUsed
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.data.AppPreferences
import app.vazie.vpn.connection.mark
import app.vazie.vpn.connection.recentConnections
import app.vazie.vpn.api.VpnConnectionController
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.VpnProfileRepository
import app.vazie.vpn.api.connectionTicks
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The collector that finally gives `VazieWidgetUpdater` something true to publish. */
@Singleton
class VazieWidgetPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val controller: VpnConnectionController,
    private val selected: SelectedProfileStore,
    private val profiles: VpnProfileRepository,
    private val preferences: AppPreferences,
    private val clock: Clock,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            frames()
                // Traffic ticks every second; the widget repaints only when its words change.
                .distinctUntilChanged()
                .collect { (state, appearance) -> VazieWidgetUpdater.publish(context, state, appearance) }
        }
    }

    /** Push the current state once, for a widget that was not there when it last changed. */
    fun refresh(onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                val (state, appearance) = frames().first()
                VazieWidgetUpdater.publish(context, state, appearance)
            } finally {
                onFinished()
            }
        }
    }

    /** The state together with the appearance chosen in the app, so switching Milk and Night Indigo repaints
     * the widgets as well. */
    private fun frames(): Flow<Pair<VazieWidgetState, Appearance>> = combine(
        states(),
        preferences.observe().map { it.appearance }.distinctUntilChanged(),
    ) { state, appearance -> state to appearance }

    /** The widgets' view of the world, recomputed whenever any part of it moves. */
    private fun states(): Flow<VazieWidgetState> = combine(
        controller.snapshot,
        selected.observeSelected(),
        profiles.observeSummaries(),
        // No clock: the widget changes only when the connection does.
    ) { snapshot, selection, summaries ->
        widgetState(
            snapshot = snapshot,
            selected = summaries.firstOrNull { it.id == selection },
            summaries = summaries,
            now = clock.instant(),
        )
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

/** The runtime's view of the world as the two widgets' view of it. */
/** The snapshot and the two selections, as the one thing a widget draws. */
internal fun widgetState(
    snapshot: VpnConnectionSnapshot,
    selected: ProfileSummary?,
    summaries: List<ProfileSummary> = emptyList(),
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): VazieWidgetState {
    val current = snapshot.subject?.toWidgetSubject()
        ?: selected?.toWidgetSubject()
        ?: return VazieWidgetState.NoConfiguration
    val currentId = current.id
    return VazieWidgetState.Configured(
        selected = current,
        connection = snapshot.toWidgetConnection(),
        shortcuts = recentConnections(own = summaries.filterNot { it.id.value == currentId })
            .take(SHORTCUT_LIMIT)
            .map { it.toWidgetUi() },
        lastUsed = LastUsed.of(
            lastUsedAt = summaries.firstOrNull { it.id.value == currentId }?.lastUsedAt,
            now = now,
            zone = zone,
        ),
    )
}

/** The live tunnel's subject, reduced to what a widget may publish. */
private fun ConnectionSubject.toWidgetSubject(): WidgetSubjectUi = profile.toWidgetSubject()



private fun ProfileSummary.toWidgetSubject(): WidgetSubjectUi = toWidgetUi().toSubject()


private fun ProfileSummary.toWidgetUi(): WidgetConfigurationUi = WidgetConfigurationUi(
    id = id.value,
    name = name,
    mark = mark,
    protocolLabel = protocolLabel,
)

private fun VpnConnectionSnapshot.toWidgetConnection(): WidgetConnectionUi =
    when (state) {
        VpnConnectionState.Idle -> WidgetConnectionUi.Idle
        VpnConnectionState.Preparing -> WidgetConnectionUi.Preparing
        is VpnConnectionState.Connecting -> WidgetConnectionUi.Connecting
        VpnConnectionState.Disconnecting -> WidgetConnectionUi.Disconnecting
        is VpnConnectionState.Failed -> WidgetConnectionUi.Failed
        VpnConnectionState.NoInternet -> WidgetConnectionUi.NoInternet
        is VpnConnectionState.Connected -> WidgetConnectionUi.Connected
    }

/** How many configuration chips the dashboard will ever show. */
/** How many Vazie locations the wide widget names before it starts counting instead. */

private const val SHORTCUT_LIMIT = 3
