// The service worker's one object: it owns the vault, the preferences and the tunnel, answers the interface's
// calls, and tells every open page whenever anything changes.

import type { Api, Args, ConfigPreview, Method, Reply, Request, SubscriptionFetchResult } from "../shared/api.ts";
import { UI_PORT } from "../shared/api.ts";
import { type Language, languageOf, translator, type Translator } from "../shared/i18n.ts";
import { parseConfig } from "../shared/parsers.ts";
import { serializeVless } from "../shared/vlessSerializer.ts";
import { siteOfUrl, type SplitTunnel } from "../shared/splitTunnel.ts";
import type { AppState, Prefs, Snapshot } from "../shared/state.ts";
import { isActive } from "../shared/state.ts";
import { BackendData } from "./backendData.ts";
import { Controller } from "./controller.ts";
import { NativeHost } from "./host.ts";
import { loadPrefs, sanitize, savePrefs } from "./prefs.ts";
import { Repository } from "./repository.ts";
import { discardSubscription, fetchSubscription, takeSubscription } from "./subscriptions.ts";
import { MENU, NOTIFICATION, Surfaces } from "./surfaces.ts";
import { clearProxy } from "./proxy.ts";

const PREF_KEYS: (keyof Prefs)[] = [
  "appearance", "appIcon", "appIconPlate", "onboardingDone", "tourDone", "viewedGuides",
  "notificationPromptShown", "notificationsEnabled", "dismissedTips",
];

export class Service {
  readonly repository = new Repository();
  readonly host = new NativeHost();
  readonly backend: BackendData;
  readonly controller: Controller;
  readonly #surfaces = new Surfaces();
  readonly #ports = new Set<chrome.runtime.Port>();
  #prefs: Prefs | null = null;
  #prefsReady: Promise<Prefs> | null = null;
  #profiles: AppState["profiles"] = [];
  #loaded = false;
  #scheduled = false;
  #language: Language = languageOf(chrome.i18n.getUILanguage());

  constructor() {
    this.backend = new BackendData(() => this.changed());
    this.controller = new Controller({
      repository: this.repository,
      host: this.host,
      split: () => this.#prefs?.split ?? { mode: "off", sites: [] },
    });
    this.controller.onChange(() => this.changed());
    this.repository.onChange(() => void this.#reloadProfiles());
  }

  get t(): Translator {
    return translator(this.#language);
  }

  // -- state -----------------------------------------------------------------------------------

  async #prefsNow(): Promise<Prefs> {
    if (this.#prefs) return this.#prefs;
    this.#prefsReady ??= loadPrefs().then((p) => (this.#prefs = p));
    return this.#prefsReady;
  }

  async #reloadProfiles(): Promise<void> {
    this.#profiles = await this.repository.summaries();
    this.#loaded = true;
    this.changed();
  }

  async init(): Promise<void> {
    await this.#prefsNow();
    await this.#reloadProfiles();
  }

  /** The selection, unless the profile it names is gone. */
  #selected(prefs: Prefs): string | null {
    return prefs.selectedId !== null && this.#profiles.some((p) => p.id === prefs.selectedId) ? prefs.selectedId : null;
  }

  state(): AppState {
    const prefs = this.#prefs ?? sanitize(null);
    return {
      snapshot: this.controller.snapshot,
      profiles: this.#profiles,
      prefs: { ...prefs, selectedId: this.#selected(prefs) },
      helper: this.controller.helper,
      traffic: this.controller.traffic,
      proxyControl: this.controller.proxyControl,
      links: this.backend.links.value,
      loaded: this.#loaded && this.#prefs !== null,
    };
  }

  /** Every page is told, once per turn of the event loop however many things changed. */
  changed(): void {
    if (this.#scheduled) return;
    this.#scheduled = true;
    queueMicrotask(() => {
      this.#scheduled = false;
      const state = this.state();
      for (const port of this.#ports) {
        try {
          port.postMessage({ t: "state", state });
        } catch {
          this.#ports.delete(port);
        }
      }
      if (state.loaded) void this.#surfaces.update(state, this.t);
    });
  }

  // -- ports -----------------------------------------------------------------------------------

  accept(port: chrome.runtime.Port): void {
    if (port.name !== UI_PORT || port.sender?.id !== chrome.runtime.id) return;
    this.#ports.add(port);
    this.controller.setWatchers(this.#ports.size);
    port.onDisconnect.addListener(() => {
      this.#ports.delete(port);
      this.controller.setWatchers(this.#ports.size);
    });
    void this.init().then(() => {
      port.postMessage({ t: "state", state: this.state() });
      // The helper is asked about when somebody is looking, not before.
      if (this.controller.helper.kind === "unknown") void this.controller.refreshHelper();
      void this.backend.links.refresh();
    });
  }

  // -- the interface's calls -------------------------------------------------------------------

  async #setPrefs(patch: Partial<Prefs>): Promise<void> {
    const current = await this.#prefsNow();
    const allowed: Partial<Prefs> = {};
    for (const key of PREF_KEYS) if (key in patch) (allowed as Record<string, unknown>)[key] = patch[key];
    this.#prefs = sanitize({ ...current, ...allowed });
    await savePrefs(this.#prefs);
    this.changed();
  }

  async #select(id: string): Promise<void> {
    const current = await this.#prefsNow();
    if (current.selectedId === id) return;
    this.#prefs = { ...current, selectedId: id };
    await savePrefs(this.#prefs);
    this.changed();
  }

  async #connect(profileId?: string): Promise<void> {
    const prefs = await this.#prefsNow();
    const id = profileId ?? this.#selected(prefs);
    if (!id) return;
    if (profileId) await this.#select(profileId);
    await this.controller.connect(id);
  }

  async toggle(): Promise<void> {
    if (isActive(this.controller.snapshot.state)) await this.controller.disconnect();
    else await this.#connect();
  }

  readonly api: { [M in Method]: (...args: Args<M>) => Promise<ReturnType<Api[M]>> | ReturnType<Api[M]> } = {
    getState: () => this.state(),

    connect: async (profileId) => this.#connect(profileId),
    disconnect: async () => this.controller.disconnect(),
    select: async (id) => this.#select(id),

    createFromLink: async (link, name) => {
      // Read again here: what the page sends is a candidate, not a configuration.
      const result = parseConfig(link);
      if (result.kind !== "recognized" || name.trim() === "") return null;
      const id = await this.repository.create(result.draft, name);
      // A newly imported configuration becomes the selected one.
      await this.#select(id);
      return id;
    },
    fetchSubscription: async (link) => fetchSubscription(link),
    importSubscription: async (token) => {
      const drafts = takeSubscription(token);
      if (!drafts) return [];
      const ids = await this.repository.createMany(drafts);
      // The list's first entry is the one its provider put first; that is the one to arm.
      if (ids[0]) await this.#select(ids[0]);
      return ids;
    },
    discardSubscription: (token) => discardSubscription(token),

    details: async (id) => this.repository.details(id),
    revealCredential: async (id) => (await this.repository.profile(id))?.outbound.userId ?? null,
    exportLink: async (id) => {
      const profile = await this.repository.profile(id);
      return profile ? serializeVless(profile.outbound, profile.name) : null;
    },
    rename: async (id, name) => this.repository.rename(id, name),
    duplicate: async (id, name) => this.repository.duplicate(id, name),
    remove: async (id) => {
      if (this.controller.connectedSubjectId === id) await this.controller.disconnect();
      await this.repository.remove(id);
    },
    latencies: async () => {
      const profiles = await Promise.all(this.#profiles.map((p) => this.repository.profile(p.id)));
      const result: Record<string, number | null> = {};
      const queue = profiles.filter((p) => p !== null);
      let helper = true;
      const worker = async () => {
        for (let p = queue.shift(); p && helper; p = queue.shift()) {
          const reply = await this.host.call({ type: "probe", host: p.outbound.endpoint.host, port: p.outbound.endpoint.port }, 8_000);
          if (!reply.ok && (reply.code === "helper" || reply.code === "timeout")) {
            helper = false;
            return;
          }
          result[p.id] = reply.ok && typeof reply.ms === "number" ? reply.ms : null;
        }
      };
      await Promise.all(Array.from({ length: 6 }, worker));
      if (!isActive(this.controller.snapshot.state)) this.host.releaseWhenIdle();
      return helper ? result : {};
    },

    setPrefs: async (patch) => this.#setPrefs(patch),
    setSplit: async (split: SplitTunnel) => {
      const current = await this.#prefsNow();
      this.#prefs = sanitize({ ...current, split });
      await savePrefs(this.#prefs);
      await this.controller.reapplySplit(this.#prefs.split);
      this.changed();
    },
    notificationPermissionChanged: async () => {
      const granted = await chrome.permissions.contains({ permissions: ["notifications"] }).catch(() => false);
      await this.#setPrefs({ notificationsEnabled: granted, notificationPromptShown: true });
    },

    recheckHelper: async () => {
      await this.controller.refreshHelper();
    },
    installEngine: async () => this.controller.installEngine(),
    getFaq: async (force) => {
      await this.backend.faq.refresh(force);
      return { faq: this.backend.faq.value, loading: this.backend.faq.loading };
    },
    activeSite: async () => {
      try {
        const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
        return siteOfUrl(tab?.url);
      } catch {
        return null;
      }
    },
  };

  async handle(request: Request, sender: chrome.runtime.MessageSender): Promise<Reply> {
    // Only this extension's own pages may ask: a web page or another extension cannot.
    if (sender.id !== chrome.runtime.id || request?.t !== "rpc") return { ok: false, error: "forbidden" };
    const method = this.api[request.m];
    if (typeof method !== "function") return { ok: false, error: "unknown method" };
    try {
      await this.init();
      const value = await (method as (...a: unknown[]) => unknown)(...request.a);
      return { ok: true, v: value } as Reply;
    } catch (error) {
      return { ok: false, error: error instanceof Error ? error.message : "failed" };
    }
  }

  // -- the browser's own surfaces --------------------------------------------------------------

  async onMenu(id: string): Promise<void> {
    if (id === MENU.toggle) await this.toggle();
    else if (id.startsWith(MENU.connectPrefix)) await this.#connect(id.slice(MENU.connectPrefix.length));
    else if (id === MENU.add) await chrome.tabs.create({ url: chrome.runtime.getURL("app.html#/add") });
    else if (id === MENU.tab) await chrome.tabs.create({ url: chrome.runtime.getURL("app.html") });
    else if (id === MENU.panel) {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.windowId !== undefined) await chrome.sidePanel.open({ windowId: tab.windowId });
    }
  }

  onNotificationButton(notificationId: string): void {
    if (notificationId === NOTIFICATION) void this.controller.disconnect();
  }

  /** Whatever a previous run left behind: the worker was killed, so its tunnel is gone, and the browser must not
   * keep pointing at it. */
  async cleanUpAfterRestart(): Promise<void> {
    await clearProxy();
  }

  setLanguage(language: Language): void {
    this.#language = language;
  }

  snapshot(): Snapshot {
    return this.controller.snapshot;
  }
}

export type { ConfigPreview, SubscriptionFetchResult };
