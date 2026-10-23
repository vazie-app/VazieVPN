import { parseConfig } from "./parsers.ts";
import type { ProfileDraft } from "./model.ts";

/** An `https` address whose answer is share links (`SubscriptionLink.kt`). */
export interface SubscriptionLink {
  /** The whole address. It carries the subscriber's token, so it is a credential like any share link. */
  url: string;
  /** The only part of the address a screen may show. */
  host: string;
  /** `https://host[:port]/*`, the one origin the extension asks the browser for. */
  originPattern: string;
}

const SCHEME = "https://";
const HOST = /^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$/;
const IPV6 = /^\[[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*\]$/;
const PORT = /^(?::[0-9]{1,5})?$/;

export function parseSubscriptionLink(raw: string): SubscriptionLink | null {
  const text = raw.trim();
  if (!text.toLowerCase().startsWith(SCHEME)) return null;
  // One address and nothing around it.
  if (/\s/.test(text)) return null;

  const afterScheme = text.slice(SCHEME.length);
  const cut = afterScheme.search(/[/?#]/);
  const authority = cut < 0 ? afterScheme : afterScheme.slice(0, cut);
  // `user:password@host` is how a link dresses one server up as another.
  if (authority.includes("@")) return null;

  let host: string;
  if (authority.startsWith("[")) {
    const close = authority.indexOf("]");
    host = (close < 0 ? "" : authority.slice(0, close)) + "]";
  } else {
    const colon = authority.lastIndexOf(":");
    host = colon < 0 ? authority : authority.slice(0, colon);
  }
  if (!HOST.test(host) && !IPV6.test(host)) return null;
  const rest = authority.slice(host.length);
  if (!PORT.test(rest)) return null;
  return { url: text, host: host.toLowerCase(), originPattern: `https://${host.toLowerCase()}${rest}/*` };
}

/** Whether `raw` is a web address without TLS: never asked for over a connection anyone on the path can read. */
export function isInsecureSubscription(raw: string): boolean {
  return raw.trim().toLowerCase().startsWith("http://");
}

export interface SubscriptionContent {
  recognized: ProfileDraft[];
  skipped: number;
  truncated: boolean;
}

/** More than anyone picks a server from, and few enough to store and show at once. */
export const MAX_CONFIGURATIONS = 100;
const LINK_MARK = "://";

/** The list as text. Links in the clear are taken as they are; anything else is tried as Base64, in either
 * alphabet and with or without padding, because servers disagree on all three. */
function decoded(body: string): string {
  if (body.includes(LINK_MARK)) return body;
  const compact = body.replace(/\s+/g, "").replace(/=+$/, "").replace(/-/g, "+").replace(/_/g, "/");
  try {
    const binary = atob(compact);
    const bytes = Uint8Array.from(binary, (ch) => ch.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  } catch {
    return body;
  }
}

/** Reads a subscription's answer: share links one per line, or Base64-encoded as a whole. */
export function readSubscription(body: string): SubscriptionContent {
  const recognized: ProfileDraft[] = [];
  let skipped = 0;
  let truncated = false;
  for (const line of decoded(body).split(/\r?\n/).map((l) => l.trim())) {
    // Only something link-shaped is a link Vazie failed to read; a blank line or an error page is not.
    if (!line.includes(LINK_MARK)) continue;
    if (recognized.length === MAX_CONFIGURATIONS) {
      truncated = true;
      break;
    }
    const result = parseConfig(line);
    if (result.kind === "recognized") recognized.push(result.draft);
    else skipped++;
  }
  return { recognized, skipped, truncated };
}
