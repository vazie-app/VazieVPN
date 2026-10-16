package app.vazie.vpn.feature.connections

import app.vazie.vpn.core.model.LastUsed
import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ConnectionSubject
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.api.VpnConnectionSnapshot
import app.vazie.vpn.api.VpnConnectionState
import app.vazie.vpn.api.profileId
import java.time.Instant
import java.time.ZoneId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A stored profile as a row, with the state it is actually in. */
internal fun List<ProfileSummary>.toRows(
    selected: ProfileId?,
    snapshot: VpnConnectionSnapshot,
    now: Instant,
    zone: ZoneId,
): ImmutableList<ConfigurationRowUi> {
    val connected = snapshot.state
        .takeIf { it is VpnConnectionState.Connected }
        ?.profileId
    return map { summary ->
        ConfigurationRowUi(
            id = summary.id.value,
            name = summary.name,
            mark = summary.protocolLabel.take(MARK_LENGTH).uppercase(),
            protocolLabel = summary.protocolLabel,
            status = when (summary.id) {
                connected -> ConfigurationStatusUi.CONNECTED
                selected -> ConfigurationStatusUi.SELECTED
                else -> ConfigurationStatusUi.IDLE
            },
            // Bucketed here, once, against a clock the caller supplies. `LastUsed` explains why the answer is
            // coarse and why it is not an `Instant` by the time a row holds it. `LastUsed`.
            lastUsed = LastUsed.of(summary.lastUsedAt, now, zone),
        )
    }.toImmutableList()
}

private const val MARK_LENGTH = 2
