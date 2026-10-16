package app.vazie.vpn.widget

import app.vazie.vpn.core.model.LastUsed

/** Synthetic presentation data for the widget previews. */
internal object WidgetFixtures {

    val selected = WidgetSubjectUi(
        id = "fixture-home",
        name = "Home server",
        mark = "VL",
        protocolLabel = "VLESS",
    )



    /** Configurations a preview can put on the dashboard's chip row. */
    val shortcuts = listOf(
        WidgetConfigurationUi(
            id = "fixture-office",
            name = "Office",
            mark = "VL",
            protocolLabel = "VLESS",
        ),
        WidgetConfigurationUi(
            id = "fixture-travel",
            name = "Travel",
            mark = "VL",
            protocolLabel = "VLESS",
        ),
        WidgetConfigurationUi(
            id = "fixture-backup",
            name = "Backup relay",
            mark = "VL",
            protocolLabel = "VLESS",
        ),
        WidgetConfigurationUi(
            id = "fixture-lab",
            name = "Lab",
            mark = "VL",
            protocolLabel = "VLESS",
        ),
    )

    fun configured(
        connection: WidgetConnectionUi,
        lastUsed: LastUsed = LastUsed.Yesterday,
        selected: WidgetSubjectUi = this.selected,
    ) = VazieWidgetState.Configured(
        selected = selected,
        connection = connection,
        shortcuts = shortcuts,
        lastUsed = lastUsed,
    )

    val idle = configured(WidgetConnectionUi.Idle)
    val connected = configured(WidgetConnectionUi.Connected)

    /** Every state a widget can be in, for the previews that draw them side by side. */
    val allStates: List<VazieWidgetState> = listOf(
        VazieWidgetState.NoConfiguration,
        idle,
        configured(WidgetConnectionUi.Preparing),
        configured(WidgetConnectionUi.Connecting),
        connected,
        configured(WidgetConnectionUi.Disconnecting),
        configured(WidgetConnectionUi.Failed),
        configured(WidgetConnectionUi.NoInternet),
    )
}
