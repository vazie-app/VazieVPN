import { ICONS } from "../shared/icons.ts";
import type { AppIconId, AppIconPlate, Appearance, GuideId, Prefs } from "../shared/state.ts";
import { modeFromId, SPLIT_OFF, type SplitTunnel, normalizeSite, MAX_SITES } from "../shared/splitTunnel.ts";

const KEY = "prefs";

export const DEFAULT_PREFS: Prefs = {
  appearance: "night_indigo",
  appIcon: "orbit",
  appIconPlate: "deep",
  split: SPLIT_OFF,
  selectedId: null,
  onboardingDone: false,
  tourDone: false,
  viewedGuides: [],
  notificationPromptShown: false,
  notificationsEnabled: false,
  dismissedTips: [],
};

const GUIDES: GuideId[] = ["toolbar", "context_menu", "side_panel", "notification", "helper"];

function readSplit(value: unknown): SplitTunnel {
  const raw = (value ?? {}) as { mode?: unknown; sites?: unknown };
  const sites = Array.isArray(raw.sites)
    ? [...new Set(raw.sites.map((s) => (typeof s === "string" ? normalizeSite(s) : null)).filter((s): s is string => s !== null))].slice(0, MAX_SITES).sort()
    : [];
  return { mode: modeFromId(raw.mode), sites };
}

/** A stored value is not a trusted enum: anything unknown resolves to the default. */
export function sanitize(value: unknown): Prefs {
  const raw = (value ?? {}) as Record<string, unknown>;
  const appearance: Appearance = raw.appearance === "milk" ? "milk" : "night_indigo";
  const appIcon = (typeof raw.appIcon === "string" && raw.appIcon in ICONS ? raw.appIcon : "orbit") as AppIconId;
  const plates = ICONS[appIcon].plates;
  const appIconPlate = (plates.includes(raw.appIconPlate as AppIconPlate) ? raw.appIconPlate : ICONS[appIcon].signature) as AppIconPlate;
  const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((s): s is string => typeof s === "string") : []);
  return {
    appearance,
    appIcon,
    appIconPlate,
    split: readSplit(raw.split),
    selectedId: typeof raw.selectedId === "string" ? raw.selectedId : null,
    onboardingDone: raw.onboardingDone === true,
    tourDone: raw.tourDone === true,
    viewedGuides: strings(raw.viewedGuides).filter((g): g is GuideId => GUIDES.includes(g as GuideId)),
    notificationPromptShown: raw.notificationPromptShown === true,
    notificationsEnabled: raw.notificationsEnabled === true,
    dismissedTips: strings(raw.dismissedTips).slice(0, 50),
  };
}

export async function loadPrefs(): Promise<Prefs> {
  try {
    const stored = await chrome.storage.local.get(KEY);
    return sanitize(stored[KEY]);
  } catch {
    return DEFAULT_PREFS;
  }
}

export async function savePrefs(prefs: Prefs): Promise<void> {
  await chrome.storage.local.set({ [KEY]: prefs });
}
