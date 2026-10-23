import type { InvalidReason, UnsupportedFeature } from "../shared/parseResult.ts";
import type { SubscriptionFailure } from "../shared/api.ts";
import type { Translator } from "../shared/i18n.ts";

/** Every parser outcome as a sentence a person can act on (`ImportMessages.kt`). */
export function invalidMessage(t: Translator, reason: InvalidReason): string {
  switch (reason.reason) {
    case "empty":
      return t.t("config_invalid_empty");
    case "unknownScheme":
      return reason.scheme === null ? t.t("config_invalid_not_a_link") : t.t("config_invalid_other_scheme", reason.scheme);
    case "malformedURI":
      return t.t("config_invalid_malformed");
    case "missingUserID":
      return t.t("config_invalid_missing_user_id");
    case "malformedUserID":
      return t.t("config_invalid_malformed_user_id");
    case "missingHost":
      return t.t("config_invalid_missing_host");
    case "malformedHost":
      return t.t("config_invalid_malformed_host");
    case "missingPort":
      return t.t("config_invalid_missing_port");
    case "invalidPort":
      return t.t("config_invalid_port");
    case "malformedEncoding":
      return t.t("config_invalid_encoding");
    case "missingParameter":
      return reason.parameter === "pbk" ? t.t("config_invalid_missing_reality_key") : t.t("config_invalid_missing_parameter", reason.parameter);
    case "conflictingParameters":
      return t.t("config_invalid_conflicting", reason.parameters.join(", "));
  }
}

export function unsupportedMessage(t: Translator, feature: UnsupportedFeature): string {
  return t.t(`config_unsupported_${feature.feature}`, feature.value);
}

export function subscriptionMessage(t: Translator, reason: SubscriptionFailure, host: string | null): string {
  switch (reason) {
    case "insecure":
      return t.t("config_subscription_insecure");
    case "unreachable":
      return t.t("config_subscription_unreachable", host ?? "");
    case "refused":
      return t.t("config_subscription_refused", host ?? "");
    case "tooLarge":
      return t.t("config_subscription_too_large", host ?? "");
    case "empty":
      return t.t("config_subscription_empty");
    case "permission":
      return t.t("x_subscription_permission", host ?? "");
  }
}
