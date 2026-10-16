package app.vazie.vpn.config.subscription

import app.vazie.vpn.core.model.Secret

/** Asks a subscription's server for its list. The one thing in importing that leaves the device, and it
 * goes to the address the user gave and nowhere else. */
fun interface SubscriptionSource {

    suspend fun fetch(link: SubscriptionLink): SubscriptionFetch
}

sealed interface SubscriptionFetch {

    /** What the server answered with, unread. Every line of it may be a credential. */
    data class Fetched(val body: Secret<String>) : SubscriptionFetch

    data class Failed(val reason: SubscriptionFailure) : SubscriptionFetch
}

/** Why there is no list to read. */
enum class SubscriptionFailure {
    /** No answer at all: no network, no such host, a timeout. */
    UNREACHABLE,

    /** The server answered, and not with the list - the link is wrong, expired or revoked. */
    REFUSED,

    /** More than a list of configurations could reasonably be. */
    TOO_LARGE,
}
