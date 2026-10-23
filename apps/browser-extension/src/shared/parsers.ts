import type { ParseResult } from "./parseResult.ts";
import { accepts, parseVless } from "./vlessParser.ts";

const SCHEME = /^[A-Za-z][A-Za-z0-9+.-]{0,31}$/;

/** The formats a screen may tell a person Vazie reads, as one already-formatted phrase. */
export const ADVERTISED_FORMATS = "VLESS";

/** A scheme, if the text starts with one. Anything that is not `word://` reports none rather than guessing. */
function schemeOrNull(text: string): string | null {
  const index = text.indexOf("://");
  const candidate = index < 0 ? "" : text.slice(0, index);
  return SCHEME.test(candidate) ? candidate.toLowerCase() : null;
}

/** The first parser that accepts the input answers for it. One today; the list is the extension point. */
export function parseConfig(raw: string): ParseResult {
  const trimmed = raw.trim();
  if (trimmed === "") return { kind: "invalid", reason: { reason: "empty" } };
  if (!accepts(trimmed)) {
    return { kind: "invalid", reason: { reason: "unknownScheme", scheme: schemeOrNull(trimmed) } };
  }
  return parseVless(trimmed);
}
