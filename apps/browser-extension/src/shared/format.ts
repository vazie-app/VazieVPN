/** `252` → `04:12`; hours appear only once there are any (`formatSessionDuration`). */
export function formatSessionDuration(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const rest = safe % 60;
  const two = (n: number) => String(n).padStart(2, "0");
  return hours > 0 ? `${hours}:${two(minutes)}:${two(rest)}` : `${two(minutes)}:${two(rest)}`;
}

export type ByteUnit = "B" | "KB" | "MB" | "GB";

const KILOBYTE = 1024;
const MEGABYTE = KILOBYTE * 1024;
const GIGABYTE = MEGABYTE * 1024;

/** Rounded down, never up (`oneDecimal`). */
function oneDecimal(bytes: number, unit: number): string {
  const tenths = Math.floor((bytes * 10) / unit);
  return `${Math.floor(tenths / 10)}.${tenths % 10}`;
}

/** A byte count as an amount and the unit it is in, or `null` when nobody will say (`trafficReading`). */
export function trafficReading(bytes: number | null | undefined): { amount: string; unit: ByteUnit } | null {
  if (bytes === null || bytes === undefined || !(bytes >= 0)) return null;
  if (bytes < KILOBYTE) return { amount: String(Math.floor(bytes)), unit: "B" };
  if (bytes < MEGABYTE) return { amount: String(Math.floor(bytes / KILOBYTE)), unit: "KB" };
  if (bytes < GIGABYTE) return { amount: oneDecimal(bytes, MEGABYTE), unit: "MB" };
  return { amount: oneDecimal(bytes, GIGABYTE), unit: "GB" };
}

/** How long ago a configuration last carried traffic, as something a screen can say out loud (`LastUsed.kt`). */
export type LastUsed =
  | { kind: "never" }
  | { kind: "justNow" }
  | { kind: "today"; hour: number; minute: number }
  | { kind: "yesterday" }
  | { kind: "earlier"; year: number; month: number; day: number };

const JUST_NOW_MS = 5 * 60 * 1000;

export function lastUsedOf(lastUsedAt: number | null, now: number): LastUsed {
  if (lastUsedAt === null) return { kind: "never" };
  // A clock that moved backwards would otherwise produce a "last used" in the future, which has no bucket.
  if (lastUsedAt > now) return { kind: "justNow" };
  if (now - lastUsedAt < JUST_NOW_MS) return { kind: "justNow" };
  const then = new Date(lastUsedAt);
  const today = new Date(now);
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOf(today) - startOf(then)) / 86_400_000);
  if (days === 0) return { kind: "today", hour: then.getHours(), minute: then.getMinutes() };
  if (days === 1) return { kind: "yesterday" };
  return { kind: "earlier", year: then.getFullYear(), month: then.getMonth() + 1, day: then.getDate() };
}

const MAX_LABEL_LENGTH = 20;
/** A scheme, an `@`, a bare host, an IP address or a UUID — the five shapes a name should never have. */
const LOOKS_LIKE_A_SECRET =
  /(:\/\/)|(@)|([A-Za-z0-9-]+\.[A-Za-z]{2,})|(\d{1,3}(\.\d{1,3}){3})|([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/;

/** A configuration's name, made fit to sit where other software can read it (the browser's menu), or `null`. */
export function safeMenuLabel(name: string): string | null {
  const collapsed = name.trim().replace(/\s+/g, " ");
  if (collapsed === "") return null;
  if (LOOKS_LIKE_A_SECRET.test(collapsed)) return null;
  if (collapsed.length <= MAX_LABEL_LENGTH) return collapsed;
  return collapsed.slice(0, MAX_LABEL_LENGTH - 1).trimEnd() + "…";
}
