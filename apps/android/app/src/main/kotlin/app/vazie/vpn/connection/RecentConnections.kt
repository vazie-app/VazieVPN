package app.vazie.vpn.connection

import app.vazie.vpn.api.ProfileSummary
import java.time.Instant

/** The order the widget's chips and the launcher's shortcuts offer connections in. */
internal fun recentConnections(own: List<ProfileSummary>): List<ProfileSummary> = own.recent()

/** The two-letter mark a list draws for a connection: the country when it is known, else the protocol. */
internal val ProfileSummary.mark: String
    get() = (countryCode ?: protocolLabel).take(MARK_LENGTH).uppercase()

private fun List<ProfileSummary>.recent(): List<ProfileSummary> =
    sortedByDescending { it.lastUsedAt ?: Instant.MIN }

private const val MARK_LENGTH = 2
