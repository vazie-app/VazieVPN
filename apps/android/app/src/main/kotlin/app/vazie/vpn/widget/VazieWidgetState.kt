package app.vazie.vpn.widget

import androidx.compose.runtime.Immutable
import app.vazie.vpn.core.model.LastUsed
import app.vazie.vpn.core.model.ProfileId

/** Everything both widgets draw from, and the boundary where "no secrets leave the store" stops being a habit
 * and becomes a shape. */
@Immutable
sealed interface VazieWidgetState {

    data object NoConfiguration : VazieWidgetState

    data class Configured(
        val selected: WidgetSubjectUi,
        val connection: WidgetConnectionUi,
        /** The configurations the dashboard offers as one-tap switches. */
        val shortcuts: List<WidgetConfigurationUi> = emptyList(),
        val lastUsed: LastUsed = LastUsed.Never,
    ) : VazieWidgetState
}

/** Whether anything is up. Nothing configured is nothing running. */
internal val VazieWidgetState.tunnelIsUp: Boolean
    get() = (this as? VazieWidgetState.Configured)?.connection?.tunnelIsUp == true

@Immutable
sealed interface WidgetConnectionUi {

    /** Whether something is up, coming up or going down. */
    val tunnelIsUp: Boolean get() = this is Preparing || this is Connecting ||
        this is Connected || this is Disconnecting

    data object Idle : WidgetConnectionUi
    data object Preparing : WidgetConnectionUi
    data object Connecting : WidgetConnectionUi
    /** Through, and that is the whole of it. */
    data object Connected : WidgetConnectionUi
    data object Disconnecting : WidgetConnectionUi
    data object Failed : WidgetConnectionUi
    data object NoInternet : WidgetConnectionUi
}

/** What the widget says it is connected to. */
@Immutable
data class WidgetSubjectUi(
    val id: String,
    val name: String,
    val mark: String,
    val protocolLabel: String,
)

@Immutable
data class WidgetConfigurationUi(
    val id: String,
    val name: String,
    val mark: String,
    val protocolLabel: String,
)


internal fun WidgetConfigurationUi.toSubject(): WidgetSubjectUi = WidgetSubjectUi(
    id = id,
    name = name,
    mark = mark,
    protocolLabel = protocolLabel,
)
