package app.vazie.vpn.feature.config.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.vazie.vpn.feature.config.R
import app.vazie.vpn.feature.config.SubscriptionFailureUi
import app.vazie.vpn.config.InvalidReason
import app.vazie.vpn.config.UnsupportedFeature

/** Every parser outcome as a sentence a person can act on. */
@Composable
internal fun InvalidReason.message(): String = when (this) {
    InvalidReason.Empty -> stringResource(R.string.config_invalid_empty)

    is InvalidReason.UnknownScheme -> when (val scheme = scheme) {
        null -> stringResource(R.string.config_invalid_not_a_link)
        else -> stringResource(R.string.config_invalid_other_scheme, scheme)
    }

    InvalidReason.MalformedUri -> stringResource(R.string.config_invalid_malformed)
    InvalidReason.MissingUserId -> stringResource(R.string.config_invalid_missing_user_id)
    InvalidReason.MalformedUserId -> stringResource(R.string.config_invalid_malformed_user_id)
    InvalidReason.MissingHost -> stringResource(R.string.config_invalid_missing_host)
    InvalidReason.MalformedHost -> stringResource(R.string.config_invalid_malformed_host)
    InvalidReason.MissingPort -> stringResource(R.string.config_invalid_missing_port)
    InvalidReason.InvalidPort -> stringResource(R.string.config_invalid_port)
    InvalidReason.MalformedEncoding -> stringResource(R.string.config_invalid_encoding)

    is InvalidReason.MissingParameter -> when (name) {
        REALITY_PUBLIC_KEY -> stringResource(R.string.config_invalid_missing_reality_key)
        else -> stringResource(R.string.config_invalid_missing_parameter, name)
    }

    is InvalidReason.ConflictingParameters ->
        stringResource(R.string.config_invalid_conflicting, names.joinToString(", "))
}

@Composable
internal fun UnsupportedFeature.message(): String = when (this) {
    is UnsupportedFeature.Security -> stringResource(R.string.config_unsupported_security, value)
    is UnsupportedFeature.Transport -> stringResource(R.string.config_unsupported_transport, value)
    is UnsupportedFeature.Flow -> stringResource(R.string.config_unsupported_flow, value)
    is UnsupportedFeature.Encryption -> stringResource(R.string.config_unsupported_encryption, value)
}

/** Why a subscription link added nothing. [host] is named wherever the server is the one to ask about it. */
@Composable
internal fun SubscriptionFailureUi.message(host: String?): String = when (this) {
    SubscriptionFailureUi.INSECURE -> stringResource(R.string.config_subscription_insecure)
    SubscriptionFailureUi.UNREACHABLE -> stringResource(R.string.config_subscription_unreachable, host.orEmpty())
    SubscriptionFailureUi.REFUSED -> stringResource(R.string.config_subscription_refused, host.orEmpty())
    SubscriptionFailureUi.TOO_LARGE -> stringResource(R.string.config_subscription_too_large, host.orEmpty())
    SubscriptionFailureUi.EMPTY -> stringResource(R.string.config_subscription_empty)
}

/** The chip's own short word for what is unsupported, beside the outcome. */
internal val UnsupportedFeature.detail: String
    get() = when (this) {
        is UnsupportedFeature.Security -> value
        is UnsupportedFeature.Transport -> value
        is UnsupportedFeature.Flow -> value
        is UnsupportedFeature.Encryption -> value
    }

private const val REALITY_PUBLIC_KEY = "pbk"
