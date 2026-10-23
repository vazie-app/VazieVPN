// The About links and the questions: the last list the backend published, kept so a cold start offline shows it
// too (`AppLinksRepository.kt`, `FaqRepository.kt`). The extension has no questions and no links of its own: they
// are `null` until the backend has answered once.

import { type AppLinks, type Faq, readFaq, readLinks } from "../shared/backend.ts";
import { API_URL } from "../shared/config.ts";

const TIMEOUT_MS = 8_000;
/** The backend caches its answer for five minutes; asking more often than this learns nothing. */
const MIN_INTERVAL_MS = 10 * 60 * 1000;

class Published<T> {
  value: T | null = null;
  loading = false;
  #loaded = false;
  #lastAsked: number | null = null;
  #inflight: Promise<void> | null = null;

  readonly #storageKey: string;
  readonly #path: string;
  readonly #read: (json: unknown) => T | null;
  /** FAQ asks every time while it has nothing, so "try again" works. */
  readonly #askWhileEmpty: boolean;
  readonly #changed: () => void;

  constructor(storageKey: string, path: string, read: (json: unknown) => T | null, askWhileEmpty: boolean, changed: () => void) {
    this.#storageKey = storageKey;
    this.#path = path;
    this.#read = read;
    this.#askWhileEmpty = askWhileEmpty;
    this.#changed = changed;
  }

  async #load(): Promise<void> {
    if (this.#loaded) return;
    this.#loaded = true;
    try {
      const stored = await chrome.storage.local.get(this.#storageKey);
      // Read back through the same checks: an edited file opens nothing new.
      this.value = this.#read(stored[this.#storageKey]);
    } catch {
      this.value = null;
    }
  }

  refresh(force = false): Promise<void> {
    if (this.#inflight) return this.#inflight;
    this.#inflight = this.#run(force).finally(() => {
      this.#inflight = null;
    });
    return this.#inflight;
  }

  async #run(force: boolean): Promise<void> {
    await this.#load();
    const now = Date.now();
    const recent = this.#lastAsked !== null && now - this.#lastAsked < MIN_INTERVAL_MS;
    if (!force && recent && (this.value !== null || !this.#askWhileEmpty)) return;
    this.#lastAsked = now;
    this.loading = true;
    this.#changed();
    let json: unknown = null;
    try {
      // No session, no cookies, nothing of the person's in it.
      const response = await fetch(`${API_URL}${this.#path}`, {
        credentials: "omit",
        cache: "no-store",
        referrerPolicy: "no-referrer",
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (response.ok) json = await response.json();
    } catch {
      json = null;
    }
    this.loading = false;
    const fetched = json === null ? null : this.#read(json);
    if (fetched !== null && JSON.stringify(fetched) !== JSON.stringify(this.value)) {
      this.value = fetched;
      try {
        await chrome.storage.local.set({ [this.#storageKey]: json });
      } catch {
        // Losing the file costs the list on the next cold start offline, nothing more.
      }
    }
    this.#changed();
  }

  async current(): Promise<T | null> {
    await this.#load();
    return this.value;
  }
}

export class BackendData {
  readonly links: Published<AppLinks>;
  readonly faq: Published<Faq>;

  constructor(changed: () => void) {
    this.links = new Published("cache:links", "/apps/vpn/links", readLinks, false, changed);
    this.faq = new Published("cache:faq", "/faq/vpn", readFaq, true, changed);
  }
}
