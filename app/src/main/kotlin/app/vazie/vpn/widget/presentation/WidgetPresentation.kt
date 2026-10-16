package app.vazie.vpn.widget.presentation

import androidx.annotation.StringRes
import app.vazie.vpn.R
import app.vazie.vpn.widget.VazieWidgetState
import app.vazie.vpn.widget.WidgetConnectionUi

/** Everything a widget needs to know about a connection in order to draw it, and the only place the mapping
 * is written. */
internal data class WidgetPresentation(
    @param:StringRes val statusRes: Int,
    /** The same state in the words a narrow cell can hold, defaulting to the full one where it already
     * fits. */
    @param:StringRes val shortStatusRes: Int = statusRes,
    val phase: WidgetPhase,
    val tone: WidgetTone,
    @param:StringRes val actionRes: Int,
    val actionStyle: WidgetActionStyle,
    val action: WidgetAction,
)

/** The phase rail: three segments, and which of them are lit. */
internal enum class WidgetPhase(val segments: List<Boolean>) {
    /** Nothing is running. */
    IDLE(listOf(false, false, false)),

    /** Something has started. */
    STARTING(listOf(true, false, false)),

    /** Most of the way. */
    LINKING(listOf(true, true, false)),

    /** Through. */
    LINKED(listOf(true, true, true)),

    /** Through at both ends and not in the middle. */
    BROKEN(listOf(true, false, true)),
}

/** What colour the state is said in - on the rail and on the word, which are not always the same. */
internal enum class WidgetTone { NEUTRAL, INFO, WARNING, SUCCESS, ERROR }

/** Filled for the action a person is most likely to want next; outlined for the one that undoes something. */
internal enum class WidgetActionStyle { FILLED, OUTLINED }

internal sealed interface WidgetAction {
    data object Connect : WidgetAction
    data object Disconnect : WidgetAction
    data object OpenHome : WidgetAction
    data object OpenConnections : WidgetAction

    data class Select(val profileId: String) : WidgetAction
}

/** The mapping. */
internal fun widgetPresentation(state: VazieWidgetState): WidgetPresentation = when (state) {
    VazieWidgetState.NoConfiguration -> WidgetPresentation(
        statusRes = R.string.connection_status_no_configuration,
        shortStatusRes = R.string.widget_state_no_configuration_short,
        phase = WidgetPhase.IDLE,
        tone = WidgetTone.NEUTRAL,
        actionRes = R.string.widget_action_add,
        actionStyle = WidgetActionStyle.FILLED,
        action = WidgetAction.OpenConnections,
    )

    is VazieWidgetState.Configured -> when (state.connection) {
        WidgetConnectionUi.Idle -> WidgetPresentation(
            statusRes = R.string.connection_status_disconnected,
            phase = WidgetPhase.IDLE,
            tone = WidgetTone.NEUTRAL,
            actionRes = R.string.widget_action_connect,
            actionStyle = WidgetActionStyle.FILLED,
            action = WidgetAction.Connect,
        )

        WidgetConnectionUi.Preparing -> WidgetPresentation(
            statusRes = R.string.connection_status_preparing,
            phase = WidgetPhase.STARTING,
            tone = WidgetTone.INFO,
            actionRes = R.string.widget_action_cancel,
            actionStyle = WidgetActionStyle.OUTLINED,
            action = WidgetAction.Disconnect,
        )

        WidgetConnectionUi.Connecting -> WidgetPresentation(
            statusRes = R.string.connection_status_connecting,
            phase = WidgetPhase.LINKING,
            tone = WidgetTone.INFO,
            actionRes = R.string.widget_action_cancel,
            actionStyle = WidgetActionStyle.OUTLINED,
            action = WidgetAction.Disconnect,
        )

        is WidgetConnectionUi.Connected -> WidgetPresentation(
            statusRes = R.string.connection_status_protected,
            phase = WidgetPhase.LINKED,
            tone = WidgetTone.SUCCESS,
            actionRes = R.string.widget_action_disconnect,
            actionStyle = WidgetActionStyle.OUTLINED,
            action = WidgetAction.Disconnect,
        )

        WidgetConnectionUi.Disconnecting -> WidgetPresentation(
            statusRes = R.string.connection_status_disconnecting,
            phase = WidgetPhase.LINKING,
            tone = WidgetTone.WARNING,
            actionRes = R.string.widget_action_open,
            actionStyle = WidgetActionStyle.OUTLINED,
            action = WidgetAction.OpenHome,
        )

        WidgetConnectionUi.Failed -> WidgetPresentation(
            statusRes = R.string.connection_status_failed,
            shortStatusRes = R.string.widget_state_failed_short,
            phase = WidgetPhase.BROKEN,
            tone = WidgetTone.ERROR,
            actionRes = R.string.widget_action_retry,
            actionStyle = WidgetActionStyle.FILLED,
            action = WidgetAction.Connect,
        )

        WidgetConnectionUi.NoInternet -> WidgetPresentation(
            statusRes = R.string.connection_status_no_internet,
            shortStatusRes = R.string.widget_state_no_internet_short,
            phase = WidgetPhase.IDLE,
            tone = WidgetTone.WARNING,
            actionRes = R.string.widget_action_open,
            actionStyle = WidgetActionStyle.OUTLINED,
            action = WidgetAction.OpenHome,
        )
    }
}
