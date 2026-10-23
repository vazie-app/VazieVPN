package app.vazie.vpn.shortcuts

import app.vazie.vpn.core.model.ProfileId
import app.vazie.vpn.api.ProfileSummary
import app.vazie.vpn.connection.mark
import app.vazie.vpn.connection.recentConnections

/** One entry in the list a launcher shows when somebody holds down the Vazie icon. */
internal sealed interface VazieShortcut {

    val id: String

    /** Connect to one specific configuration. */
    data class Connect(
        val profileId: ProfileId,
        val label: String,
        /** The row mark drawn on the icon: `VL` for a VLESS configuration. */
        val mark: String = "",
    ) : VazieShortcut {
        override val id: String get() = CONNECT_PREFIX + profileId.value
    }

    /** The one action that is useful before there is anything to connect to. */
    data object AddConfiguration : VazieShortcut {
        override val id: String get() = "vazie.shortcut.add_config"
    }

    companion object {
        const val CONNECT_PREFIX: String = "vazie.shortcut.profile."
    }
}

/** Which shortcuts a launcher should be showing right now. */
internal fun shortcutPlan(
    profiles: List<ProfileSummary>,
    maxShortcuts: Int,
): List<VazieShortcut> {
    if (maxShortcuts <= 0) return emptyList()

    val connects = recentConnections(own = profiles)
        .mapNotNull { summary ->
            safeShortcutLabel(summary.name)?.let { label ->
                VazieShortcut.Connect(
                    profileId = summary.id,
                    label = label,
                    mark = summary.mark,
                )
            }
        }
        .take(maxShortcuts.coerceAtMost(MAX_CONNECT_SHORTCUTS))

    val room = maxShortcuts - connects.size
    return if (room > 0) connects + VazieShortcut.AddConfiguration else connects
}

/** A configuration's name, made fit to sit in another app's database, or `null` if it should not. */
internal fun safeShortcutLabel(name: String): String? {
    val collapsed = name.trim().replace(WHITESPACE, " ")
    if (collapsed.isEmpty()) return null
    if (LOOKS_LIKE_A_SECRET.containsMatchIn(collapsed)) return null
    return if (collapsed.length <= MAX_LABEL_LENGTH) {
        collapsed
    } else {
        collapsed.take(MAX_LABEL_LENGTH - 1).trimEnd() + ELLIPSIS
    }
}

private const val MAX_CONNECT_SHORTCUTS = 3
private const val MAX_LABEL_LENGTH = 20
private const val ELLIPSIS = "…"
private val WHITESPACE = Regex("""\s+""")

/** A scheme, an `@`, a bare host, an IP address or a UUID — the five shapes a name should never have. */
private val LOOKS_LIKE_A_SECRET = Regex(
    """(://)|(@)|([A-Za-z0-9-]+\.[A-Za-z]{2,})|(\d{1,3}(\.\d{1,3}){3})|""" +
        """([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""",
)
