// The native helper, over Chrome's native messaging. One port at a time; a request is a message with an id and
// the reply carries it back. Anything the helper volunteers (`exited`, `engineProgress`) is an event.

export const HOST_NAME = "app.vazie.vpn";

export type HostReply = { ok: true; [key: string]: unknown } | { ok: false; code: string; [key: string]: unknown };

export interface HostEvent {
  event: "exited" | "engineProgress";
  [key: string]: unknown;
}

export interface Hello {
  version: string;
  platform: string;
  engine: { installed: boolean; supported: boolean; version: string | null; pinned: string };
}

type Pending = { resolve: (reply: HostReply) => void; timer: ReturnType<typeof setTimeout> };

export class NativeHost {
  #port: chrome.runtime.Port | null = null;
  #pending = new Map<number, Pending>();
  #nextId = 1;
  #listeners = new Set<(event: HostEvent) => void>();
  #idle: ReturnType<typeof setTimeout> | null = null;
  /** What the browser said the last time the helper could not be reached ("Specified native messaging host not found."). */
  lastError: string | null = null;

  onEvent(listener: (event: HostEvent) => void): void {
    this.#listeners.add(listener);
  }

  get connected(): boolean {
    return this.#port !== null;
  }

  #open(): chrome.runtime.Port | null {
    if (this.#port) return this.#port;
    let port: chrome.runtime.Port;
    try {
      port = chrome.runtime.connectNative(HOST_NAME);
    } catch (error) {
      this.lastError = error instanceof Error ? error.message : null;
      return null;
    }
    port.onMessage.addListener((message: Record<string, unknown>) => {
      if (typeof message.event === "string") {
        for (const listener of this.#listeners) listener(message as unknown as HostEvent);
        return;
      }
      const id = message.id;
      if (typeof id !== "number") return;
      const pending = this.#pending.get(id);
      if (!pending) return;
      this.#pending.delete(id);
      clearTimeout(pending.timer);
      pending.resolve(message as unknown as HostReply);
    });
    port.onDisconnect.addListener(() => {
      // `lastError` must be read, or Chrome logs it as unchecked; it also says why, which the person is shown.
      this.lastError = chrome.runtime.lastError?.message ?? null;
      if (this.#port === port) this.#port = null;
      for (const [id, pending] of this.#pending) {
        clearTimeout(pending.timer);
        pending.resolve({ ok: false, code: "helper" });
        this.#pending.delete(id);
      }
      for (const listener of this.#listeners) listener({ event: "exited", code: -1, helper: true });
    });
    this.#port = port;
    return port;
  }

  /** One request. `{ ok: false, code: "helper" }` when the helper is not installed or cannot be started. */
  call(message: Record<string, unknown>, timeoutMs = 15_000): Promise<HostReply> {
    this.#cancelIdle();
    const port = this.#open();
    if (!port) return Promise.resolve({ ok: false, code: "helper" });
    return new Promise((resolve) => {
      const id = this.#nextId++;
      const timer = setTimeout(() => {
        this.#pending.delete(id);
        resolve({ ok: false, code: "timeout" });
      }, timeoutMs);
      this.#pending.set(id, { resolve, timer });
      try {
        port.postMessage({ id, ...message });
      } catch {
        clearTimeout(timer);
        this.#pending.delete(id);
        resolve({ ok: false, code: "helper" });
      }
    });
  }

  async hello(): Promise<{ ok: true; hello: Hello } | { ok: false; detail: string | null }> {
    this.lastError = null;
    const reply = await this.call({ type: "hello" }, 5_000);
    if (!reply.ok) return { ok: false, detail: this.lastError };
    return { ok: true, hello: reply as unknown as Hello };
  }

  /** Lets go of the helper after a while with nothing to do; a running tunnel keeps it. */
  releaseWhenIdle(delayMs = 60_000): void {
    this.#cancelIdle();
    this.#idle = setTimeout(() => this.close(), delayMs);
  }

  #cancelIdle(): void {
    if (this.#idle) clearTimeout(this.#idle);
    this.#idle = null;
  }

  close(): void {
    this.#cancelIdle();
    const port = this.#port;
    this.#port = null;
    port?.disconnect();
  }
}
