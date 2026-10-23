// A subscription link: one `GET` to the link's own address, once. The request goes to that server and nowhere
// else, with no session and no cookies; the browser permission for that one origin is asked for by the interface,
// at the person's click, and handed back as soon as the list is in.

import type { ConfigPreview, SubscriptionFetchResult } from "../shared/api.ts";
import type { ProfileDraft } from "../shared/model.ts";
import { toPreview } from "../shared/preview.ts";
import { isInsecureSubscription, parseSubscriptionLink, readSubscription } from "../shared/subscription.ts";

const BODY_LIMIT_BYTES = 1024 * 1024;
const TIMEOUT_MS = 20_000;
const TOKEN_LIFETIME_MS = 10 * 60 * 1000;
const USER_AGENT = "VazieVPN";
const RULE_ID = 1;

interface Held {
  drafts: { draft: ProfileDraft; name: string }[];
  timer: ReturnType<typeof setTimeout>;
}

const held = new Map<string, Held>();

async function boundedText(response: Response): Promise<string | null> {
  const reader = response.body?.getReader();
  if (!reader) return "";
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > BODY_LIMIT_BYTES) {
      void reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(bytes);
}

/** Panels pick the format by what asks; the app's own name is what it sends, and nothing about this browser. */
async function nameTheClient(host: string): Promise<void> {
  try {
    await chrome.declarativeNetRequest.updateSessionRules({
      removeRuleIds: [RULE_ID],
      addRules: [
        {
          id: RULE_ID,
          priority: 1,
          action: {
            type: "modifyHeaders" as chrome.declarativeNetRequest.RuleActionType,
            requestHeaders: [{ header: "user-agent", operation: "set" as chrome.declarativeNetRequest.HeaderOperation, value: USER_AGENT }],
          },
          condition: {
            requestDomains: [host.replace(/^\[|\]$/g, "")],
            initiatorDomains: [chrome.runtime.id],
            resourceTypes: ["xmlhttprequest" as chrome.declarativeNetRequest.ResourceType],
          },
        },
      ],
    });
  } catch {
    // The list is still asked for; a panel that wants another User-Agent will say so by refusing.
  }
}

async function forgetClient(): Promise<void> {
  try {
    await chrome.declarativeNetRequest.updateSessionRules({ removeRuleIds: [RULE_ID] });
  } catch {
    // Nothing to remove.
  }
}

export async function fetchSubscription(rawLink: string): Promise<SubscriptionFetchResult> {
  if (isInsecureSubscription(rawLink)) return { ok: false, host: null, reason: "insecure" };
  const link = parseSubscriptionLink(rawLink);
  if (!link) return { ok: false, host: null, reason: "refused" };

  const permitted = await chrome.permissions.contains({ origins: [link.originPattern] }).catch(() => false);
  if (!permitted) return { ok: false, host: link.host, reason: "permission" };

  await nameTheClient(link.host);
  try {
    const response = await fetch(link.url, {
      method: "GET",
      headers: { Accept: "text/plain, */*;q=0.5" },
      credentials: "omit",
      cache: "no-store",
      referrerPolicy: "no-referrer",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    // The link was checked to be https; a redirect must not quietly undo that.
    if (!response.url.startsWith("https://")) return { ok: false, host: link.host, reason: "unreachable" };
    if (!response.ok) return { ok: false, host: link.host, reason: "refused" };
    const body = await boundedText(response);
    if (body === null) return { ok: false, host: link.host, reason: "tooLarge" };

    const content = readSubscription(body);
    if (content.recognized.length === 0) return { ok: false, host: link.host, reason: "empty" };

    const token = crypto.randomUUID();
    const drafts = content.recognized.map((draft) => ({ draft, name: toPreview(draft).suggestedName }));
    held.set(token, { drafts, timer: setTimeout(() => held.delete(token), TOKEN_LIFETIME_MS) });
    const configs: ConfigPreview[] = content.recognized.map(toPreview);
    return { ok: true, token, host: link.host, configs, skipped: content.skipped, truncated: content.truncated };
  } catch {
    // Engines report an unreachable host each in their own way; the person is told one thing.
    return { ok: false, host: link.host, reason: "unreachable" };
  } finally {
    await forgetClient();
    // The list is downloaded once: the access it needed goes back with it.
    await chrome.permissions.remove({ origins: [link.originPattern] }).catch(() => false);
  }
}

/** The drafts behind a token, once. */
export function takeSubscription(token: string): { draft: ProfileDraft; name: string }[] | null {
  const entry = held.get(token);
  if (!entry) return null;
  clearTimeout(entry.timer);
  held.delete(token);
  return entry.drafts;
}

export function discardSubscription(token: string): void {
  takeSubscription(token);
}
