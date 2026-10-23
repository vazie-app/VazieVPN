// The parts of Vazie that live outside its pages — what Android spreads over widgets, the Quick Settings tile,
// launcher shortcuts and the status notification: the toolbar icon and its tooltip, the menu under a right click,
// and, if the person allowed it, a notification with a Disconnect button while the tunnel is up.

import { drawIcon, type Dot } from "../shared/icons.ts";
import { safeMenuLabel } from "../shared/format.ts";
import type { Translator } from "../shared/i18n.ts";
import type { AppState, ProfileSummary } from "../shared/state.ts";

const NOTIFICATION_ID = "vazie-tunnel";
const MAX_RECENT = 3;
const SIZES = [16, 32, 48];

export const MENU = {
  toggle: "toggle",
  add: "add",
  tab: "open-tab",
  panel: "open-panel",
  connectPrefix: "connect:",
} as const;

const marks = new Map<string, Promise<ImageBitmap>>();

function markBitmap(icon: string): Promise<ImageBitmap> {
  let bitmap = marks.get(icon);
  if (!bitmap) {
    bitmap = fetch(chrome.runtime.getURL(`icons/marks/${icon}.webp`))
      .then((r) => r.blob())
      .then((b) => createImageBitmap(b));
    marks.set(icon, bitmap);
  }
  return bitmap;
}

function dotOf(state: AppState["snapshot"]["state"]): Dot {
  switch (state.kind) {
    case "connected":
      return "connected";
    case "preparing":
    case "connecting":
    case "disconnecting":
    case "noInternet":
      return "busy";
    case "failed":
      return "failed";
    default:
      return null;
  }
}

/** The order the menu offers connections in: the ones that carried traffic most recently first. */
export function recentConnections(profiles: ProfileSummary[]): ProfileSummary[] {
  return [...profiles].sort((a, b) => (b.lastUsedAt ?? -Infinity) - (a.lastUsedAt ?? -Infinity));
}

export function statusWord(t: Translator, state: AppState["snapshot"]["state"], hasProfiles: boolean): string {
  switch (state.kind) {
    case "connected":
      return t.t("connection_status_protected");
    case "preparing":
      return t.t("connection_status_preparing");
    case "connecting":
      return t.t("connection_status_connecting");
    case "disconnecting":
      return t.t("connection_status_disconnecting");
    case "failed":
      return t.t("connection_status_failed");
    case "noInternet":
      return t.t("connection_status_no_internet");
    default:
      return hasProfiles ? t.t("connection_status_disconnected") : t.t("connection_status_no_configuration");
  }
}

export class Surfaces {
  #iconKey = "";
  #menuKey = "";
  #notified = false;
  #notificationKey = "";

  async update(state: AppState, t: Translator): Promise<void> {
    await Promise.allSettled([this.#icon(state), this.#title(state, t), this.#menu(state, t), this.#notification(state, t)]);
  }

  async #icon(state: AppState): Promise<void> {
    const { appIcon, appIconPlate } = state.prefs;
    const dot = dotOf(state.snapshot.state);
    const key = `${appIcon}|${appIconPlate}|${dot}`;
    if (key === this.#iconKey) return;
    this.#iconKey = key;
    const mark = await markBitmap(appIcon);
    const imageData: Record<number, ImageData> = {};
    const make = (w: number, h: number) => new OffscreenCanvas(w, h);
    for (const size of SIZES) {
      const canvas = new OffscreenCanvas(size, size);
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      drawIcon(ctx, size, mark, appIcon, appIconPlate, dot, make);
      imageData[size] = ctx.getImageData(0, 0, size, size);
    }
    await chrome.action.setIcon({ imageData });
  }

  async #title(state: AppState, t: Translator): Promise<void> {
    const word = statusWord(t, state.snapshot.state, state.profiles.length > 0);
    await chrome.action.setTitle({ title: `${t.t("app_name")} — ${word}` });
  }

  async #menu(state: AppState, t: Translator): Promise<void> {
    const active = state.snapshot.state.kind === "connected" || state.snapshot.state.kind === "connecting" || state.snapshot.state.kind === "preparing";
    const recent = recentConnections(state.profiles)
      .map((p) => ({ id: p.id, label: safeMenuLabel(p.name) }))
      .filter((p): p is { id: string; label: string } => p.label !== null)
      .slice(0, MAX_RECENT);
    const key = JSON.stringify([t.language, active, state.profiles.length > 0, recent]);
    if (key === this.#menuKey) return;
    this.#menuKey = key;

    await chrome.contextMenus.removeAll();
    const add = (id: string, title: string) => chrome.contextMenus.create({ id, title, contexts: ["action"] });
    if (state.profiles.length > 0) {
      add(MENU.toggle, active ? t.t("widget_action_disconnect") : t.t("widget_action_connect"));
    }
    for (const item of recent) add(MENU.connectPrefix + item.id, t.t("shortcut_connect_to", item.label));
    // The one action that is useful before there is anything to connect to.
    if (recent.length < MAX_RECENT) add(MENU.add, t.t("shortcut_add_config_long"));
    chrome.contextMenus.create({ id: "sep", type: "separator", contexts: ["action"] });
    add(MENU.panel, t.t("x_open_side_panel"));
    add(MENU.tab, t.t("x_open_in_tab"));
  }

  async #notification(state: AppState, t: Translator): Promise<void> {
    const api = chrome.notifications;
    if (!api) return;
    const connected = state.snapshot.state.kind === "connected";
    if (state.prefs.notificationsEnabled && connected) {
      // The state and nothing else: a notification is read on a locked screen, so it never names a configuration.
      const options: chrome.notifications.NotificationCreateOptions = {
        type: "basic",
        iconUrl: chrome.runtime.getURL("icons/icon-128.png"),
        title: t.t("vpn_notification_title"),
        message: t.t("vpn_state_connected"),
        buttons: [{ title: t.t("vpn_notification_disconnect") }],
        silent: true,
        requireInteraction: true,
      };
      // The state pushes again for every traffic reading. Showing the same notification again each time makes the
      // system pop its banner up again each time, so it is touched only when what it says has changed.
      const key = JSON.stringify([options.title, options.message, options.buttons]);
      if (this.#notified && key === this.#notificationKey) return;
      if (this.#notified) await api.update(NOTIFICATION_ID, options);
      else if (!(await this.#alreadyShown(api))) await api.create(NOTIFICATION_ID, options);
      this.#notified = true;
      this.#notificationKey = key;
    } else if (this.#notified) {
      this.#notified = false;
      this.#notificationKey = "";
      await api.clear(NOTIFICATION_ID);
    }
  }

  /** The worker can be restarted while the notification from before is still up; it is not shown a second time. */
  async #alreadyShown(api: typeof chrome.notifications): Promise<boolean> {
    try {
      return NOTIFICATION_ID in (await api.getAll());
    } catch {
      return false;
    }
  }
}

export const NOTIFICATION = NOTIFICATION_ID;
