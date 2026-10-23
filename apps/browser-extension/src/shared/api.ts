// The typed RPC between the interface and the service worker. Plain JSON: `runtime.sendMessage` cannot carry more.

import type { InvalidReason, UnsupportedFeature } from "./parseResult.ts";
import type {
  AppState,
  Prefs,
  ProfileDetails,
} from "./state.ts";
import type { SplitTunnel } from "./splitTunnel.ts";
import type { Faq } from "./backend.ts";

/** What one configuration looks like in a preview: safe fields only. */
export interface ConfigPreview {
  protocolLabel: string;
  securityLabel: string | null;
  transportLabel: string;
  server: string;
  endpointHost: string;
  endpointPort: number;
  serverName: string | null;
  fingerprint: string | null;
  flow: string | null;
  suggestedName: string;
  mark: string;
  keptParameters: string[];
}

export type SubscriptionFailure = "insecure" | "unreachable" | "refused" | "tooLarge" | "empty" | "permission";

export type SubscriptionFetchResult =
  | { ok: true; token: string; host: string; configs: ConfigPreview[]; skipped: number; truncated: boolean }
  | { ok: false; host: string | null; reason: SubscriptionFailure };

export type Detection =
  | { kind: "waiting" }
  | { kind: "recognized"; preview: ConfigPreview }
  | { kind: "unsupported"; protocolLabel: string; feature: UnsupportedFeature }
  | { kind: "invalid"; reason: InvalidReason };

export interface Api {
  getState(): AppState;

  connect(profileId?: string): void;
  disconnect(): void;
  select(profileId: string): void;

  /** Stores one link under the name the person confirmed; the link is read again here, not trusted from the page. */
  createFromLink(link: string, name: string): string | null;
  /** Downloads a subscription's list from its own server. The list's links stay here, behind `token`. */
  fetchSubscription(link: string): SubscriptionFetchResult;
  importSubscription(token: string): string[];
  discardSubscription(token: string): void;

  details(id: string): ProfileDetails | null;
  revealCredential(id: string): string | null;
  /** The share link, for exactly one hand-off. */
  exportLink(id: string): string | null;
  rename(id: string, name: string): void;
  duplicate(id: string, name: string): string | null;
  remove(id: string): void;
  /** Round-trip time per configuration, `null` for no answer. Empty when there is no helper to ask. */
  latencies(): Record<string, number | null>;

  setPrefs(patch: Partial<Prefs>): void;
  setSplit(split: SplitTunnel): void;
  /** Called after the person granted or refused the optional notification permission. */
  notificationPermissionChanged(): void;

  recheckHelper(): void;
  /** Asks the helper to download and verify the pinned Xray engine. */
  installEngine(): boolean;
  getFaq(force: boolean): { faq: Faq | null; loading: boolean };
  /** The tab that is open now: its site, for the "this site" row. */
  activeSite(): string | null;
}

export type Method = keyof Api;
export type Args<M extends Method> = Parameters<Api[M]>;
export type Result<M extends Method> = Awaited<ReturnType<Api[M]>>;

export interface Request<M extends Method = Method> {
  t: "rpc";
  m: M;
  a: Args<M>;
}

export type Reply<M extends Method = Method> = { ok: true; v: Result<M> } | { ok: false; error: string };

/** The port the interface opens so that the worker can push each change of state. */
export const UI_PORT = "vazie-ui";

export type Push = { t: "state"; state: AppState };
