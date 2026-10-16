package app.vazie.vpn.feature.connections

import app.vazie.vpn.core.model.LastUsed
import kotlinx.collections.immutable.persistentListOf

/** Synthetic list data, for previews. */
internal object ConnectionsFixtures {

    /** Five rows that differ in everything except protocol. */
    val populated = ConnectionsUiState(
        configurations = persistentListOf(
            ConfigurationRowUi(
                id = "sample-home-relay",
                name = "Home relay",
                mark = "VL",
                protocolLabel = "VLESS",
                status = ConfigurationStatusUi.CONNECTED,
                lastUsed = LastUsed.JustNow,
            ),
            ConfigurationRowUi(
                id = "sample-office-tunnel",
                name = "Office tunnel",
                mark = "VL",
                protocolLabel = "VLESS",
                status = ConfigurationStatusUi.IDLE,
                lastUsed = LastUsed.Today(hour = 3, minute = 14),
            ),
            ConfigurationRowUi(
                id = "sample-backup-relay",
                name = "Backup relay",
                mark = "VL",
                protocolLabel = "VLESS",
                status = ConfigurationStatusUi.IDLE,
                lastUsed = LastUsed.Yesterday,
            ),
            ConfigurationRowUi(
                id = "sample-travel-relay",
                name = "Travel relay",
                mark = "VL",
                protocolLabel = "VLESS",
                status = ConfigurationStatusUi.ERROR,
                lastUsed = LastUsed.Earlier(year = 2025, month = 11, day = 3),
            ),
            ConfigurationRowUi(
                id = "sample-old-config",
                name = "Old config",
                mark = "VL",
                protocolLabel = "VLESS",
                status = ConfigurationStatusUi.UNAVAILABLE,
                enabled = false,
            ),
        ),
    )

    val empty = ConnectionsUiState()
}
