package app.vazie.vpn.config

import app.vazie.vpn.api.ProfileDraft

/** What came of reading a [ConfigSource]. */
sealed interface ParseResult {

    /** Understood, and Vazie can store and describe it. */
    data class Recognized(
        val draft: ProfileDraft,
        val descriptor: ProtocolDescriptor,
    ) : ParseResult

    /** Understood well enough to name what is missing on Vazie's side, and no further. */
    data class Unsupported(
        val descriptor: ProtocolDescriptor,
        val feature: UnsupportedFeature,
    ) : ParseResult

    /** Not a configuration Vazie can read, for a reason it can name. */
    data class Invalid(val reason: InvalidReason) : ParseResult
}

/** The part of a recognized configuration Vazie has no support for. */
sealed interface UnsupportedFeature {
    data class Security(val value: String) : UnsupportedFeature
    data class Transport(val value: String) : UnsupportedFeature
    data class Flow(val value: String) : UnsupportedFeature
    data class Encryption(val value: String) : UnsupportedFeature
}

/** Why a configuration could not be read. */
sealed interface InvalidReason {

    /** There was nothing to read. */
    data object Empty : InvalidReason

    /** The text is a URI of some other kind. */
    data class UnknownScheme(val scheme: String?) : InvalidReason

    /** The right scheme, but not the shape a share link has. */
    data object MalformedUri : InvalidReason

    /** No user id before the `@`. */
    data object MissingUserId : InvalidReason

    /** A user id that is not a UUID. */
    data object MalformedUserId : InvalidReason

    /** No host between the `@` and the port. */
    data object MissingHost : InvalidReason

    /** A host that is neither a domain name nor an IP address. */
    data object MalformedHost : InvalidReason

    /** No `:port` after the host. */
    data object MissingPort : InvalidReason

    /** A port that is not a number in 1..65535. */
    data object InvalidPort : InvalidReason

    /** A `%` sequence that is not a percent-encoded byte. */
    data object MalformedEncoding : InvalidReason

    /** A parameter this security or transport cannot work without. */
    data class MissingParameter(val name: String) : InvalidReason

    /** Parameters that cannot describe one working configuration together. */
    data class ConflictingParameters(val names: List<String>) : InvalidReason
}
