import type { VlessOutbound } from "./model.ts";

export type UnsupportedRuntimeFeature = "engine" | "protocol" | "transport" | "security" | "flow";

const UNSAFE_FINGERPRINT = "unsafe";
/** Matches Xray's `hello…` fingerprint family by prefix instead of copying its tables. */
const EXPLICIT_FINGERPRINT_PREFIX = "hello";
const PRESET_FINGERPRINTS = new Set([
  "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized", "randomizednoalpn",
]);

function isKnownFingerprint(fingerprint: string | null): boolean {
  const name = fingerprint?.trim().toLowerCase() ?? "";
  if (name === "") return true;
  if (name === UNSAFE_FINGERPRINT) return false;
  return PRESET_FINGERPRINTS.has(name) || name.startsWith(EXPLICIT_FINGERPRINT_PREFIX);
}

/** What the pinned Xray build can execute, asked before anything is started (`XraySupport.kt`). */
export function supports(outbound: VlessOutbound): { ok: true } | { ok: false; feature: UnsupportedRuntimeFeature } {
  const { security, transport, flow } = outbound;
  if (security.kind === "tls" && security.allowInsecure) return { ok: false, feature: "security" };
  if (!isKnownFingerprint(security.kind === "none" ? null : security.fingerprint)) {
    return { ok: false, feature: "security" };
  }
  if (security.kind === "reality" && transport.kind === "ws") return { ok: false, feature: "security" };
  // Vision needs TLS or REALITY over RAW/TCP; the engine checks it itself.
  if (flow === "xtls-rprx-vision") {
    if (transport.kind !== "tcp") return { ok: false, feature: "flow" };
    if (security.kind === "none") return { ok: false, feature: "flow" };
  }
  return { ok: true };
}
