// Everything the interface may know, in one value. No field here can hold a secret: a credential never leaves the
// service worker's vault except through `revealCredential` and `exportLink`, one at a time.

import type { SecurityKind, TransportKind, XrayFlow } from "./model.ts";
import type { SplitTunnel } from "./splitTunnel.ts";
import type { AppLinks } from "./backend.ts";

export type Failure =
  /** The native helper is not installed, or Chrome cannot start it. */
  | "helper"
  /** The helper runs, but the Xray engine is not on disk (or failed its checksum). */
  | "engine"
  | "noInternet"
  /** The stored profile could not be turned into something the engine can run. */
  | "profile"
  /** The profile is valid and the engine cannot execute it. Reported before anything is started. */
  | "unsupported"
  /** The local tunnel endpoint could not be opened. */
  | "tunnel"
  /** The engine refused to start, or stopped on its own. */
  | "handshake"
  /** The engine came up and nothing could reach the internet through it. */
  | "tunnelUnusable"
  /** Another extension controls the browser's proxy settings. */
  | "proxyControlled"
  | "unknown";

export type ConnectionState =
  | { kind: "idle" }
  | { kind: "preparing" }
  | { kind: "connecting"; profileId: string }
  | { kind: "connected"; profileId: string; since: number }
  | { kind: "disconnecting" }
  | { kind: "failed"; failure: Failure }
  | { kind: "noInternet" };

export interface ConnectionSubject {
  id: string;
  name: string;
  protocolLabel: string;
  mark: string;
}

/** The closed set the connection details screen may render. */
export interface Diagnostics {
  engine: string;
  protocol: string;
  security: SecurityKind;
  transport: string;
  flow: XrayFlow | null;
  endpointHost: string;
  endpointPort: number;
  /** Round-trip time to the server, measured from this machine; `null` until it has been. */
  latencyMs: number | null;
  dnsMode: "tunnel";
  ipv4: boolean;
  ipv6: boolean;
}

export interface Snapshot {
  state: ConnectionState;
  subject: ConnectionSubject | null;
  diagnostics: Diagnostics | null;
}

export interface Traffic {
  rx: number;
  tx: number;
}

export interface ProfileSummary {
  id: string;
  name: string;
  protocolLabel: string;
  mark: string;
  createdAt: number;
  lastUsedAt: number | null;
  /** Whether the engine can run it; `false` disables Connect instead of letting it fail. */
  runnable: boolean;
}

export interface ProfileDetails {
  id: string;
  name: string;
  engineLabel: string;
  protocolLabel: string;
  security: SecurityKind;
  transport: TransportKind;
  endpointHost: string;
  endpointPort: number;
  serverName: string | null;
  fingerprint: string | null;
  flow: XrayFlow | null;
  /** The WebSocket path, the gRPC service name or the TCP header — whatever the transport made meaningful. */
  transportDetail: string | null;
  unknownParameterNames: string[];
  createdAt: number;
  lastUsedAt: number | null;
}

export type Appearance = "night_indigo" | "milk";
export const APPEARANCES: Appearance[] = ["night_indigo", "milk"];

export type AppIconId =
  | "orbit" | "lime" | "emerald" | "magenta" | "cotton_candy" | "crimson" | "gold" | "pearl" | "onyx" | "monochrome";
export type AppIconPlate = "deep" | "light" | "ink" | "forest" | "rose" | "amber" | "mist";

export type GuideId = "toolbar" | "context_menu" | "side_panel" | "notification" | "helper";

export interface Prefs {
  appearance: Appearance;
  appIcon: AppIconId;
  appIconPlate: AppIconPlate;
  split: SplitTunnel;
  selectedId: string | null;
  onboardingDone: boolean;
  tourDone: boolean;
  viewedGuides: GuideId[];
  /** The notification explanation is shown once, at the first Connect. */
  notificationPromptShown: boolean;
  notificationsEnabled: boolean;
  dismissedTips: string[];
}

export type HelperStatus =
  | { kind: "unknown" }
  /** `detail` is the browser's own words about why the helper could not be reached, when it gave any. */
  | { kind: "missing"; detail?: string | null }
  | { kind: "ready"; version: string; engineVersion: string | null; platform: string };

export interface AppState {
  snapshot: Snapshot;
  profiles: ProfileSummary[];
  prefs: Prefs;
  helper: HelperStatus;
  traffic: Traffic | null;
  /** Whether the browser lets this extension control its proxy right now. */
  proxyControl: "ours" | "available" | "other" | "unknown";
  /** The About links, as the backend last published them; `null` until it has answered once. */
  links: AppLinks | null;
  /** `true` once the vault has been read at least once. */
  loaded: boolean;
}

export const isActive = (state: ConnectionState): boolean =>
  state.kind === "preparing" ||
  state.kind === "connecting" ||
  state.kind === "connected" ||
  state.kind === "disconnecting";

export function profileIdOf(state: ConnectionState): string | null {
  return state.kind === "connecting" || state.kind === "connected" ? state.profileId : null;
}
