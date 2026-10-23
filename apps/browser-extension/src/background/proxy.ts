// The browser's side of the tunnel: point its proxy at the helper's loopback endpoint with a PAC script, and
// (while every site goes through the tunnel) stop WebRTC from showing the real address.

import { pacScript, type SplitTunnel, isActive } from "../shared/splitTunnel.ts";

export type Control = "ours" | "available" | "other" | "unknown";

export async function control(): Promise<Control> {
  try {
    const details = await chrome.proxy.settings.get({ incognito: false });
    switch (details.levelOfControl) {
      case "controlled_by_this_extension":
        return "ours";
      case "controllable_by_this_extension":
        return "available";
      case "controlled_by_other_extensions":
      case "not_controllable":
        return "other";
      default:
        return "unknown";
    }
  } catch {
    return "unknown";
  }
}

export async function applyProxy(split: SplitTunnel, port: number): Promise<boolean> {
  try {
    await chrome.proxy.settings.set({
      value: { mode: "pac_script", pacScript: { data: pacScript(split, port) } },
      scope: "regular",
    });
    // Everything goes through the tunnel: a WebRTC call must not announce the address the tunnel hides.
    // With sites split out, some traffic is meant to be direct, and the browser's own policy stays.
    if (!isActive(split)) {
      await chrome.privacy.network.webRTCIPHandlingPolicy.set({ value: "disable_non_proxied_udp" });
    } else {
      await chrome.privacy.network.webRTCIPHandlingPolicy.clear({ scope: "regular" });
    }
    return (await control()) === "ours";
  } catch {
    return false;
  }
}

/** Gives the browser its own settings back. Safe to call when nothing is applied. */
export async function clearProxy(): Promise<void> {
  try {
    if ((await control()) === "ours") await chrome.proxy.settings.clear({ scope: "regular" });
  } catch {
    // Nothing to restore.
  }
  try {
    await chrome.privacy.network.webRTCIPHandlingPolicy.clear({ scope: "regular" });
  } catch {
    // Not ours to restore.
  }
}
