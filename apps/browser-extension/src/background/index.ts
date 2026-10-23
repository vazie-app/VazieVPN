// The service worker. Every listener is registered here, at the top level, because that is what wakes a stopped
// worker: the interface's calls, the menu, the shortcut, the network coming and going.

import type { Request } from "../shared/api.ts";
import { Service } from "./service.ts";

const service = new Service();

// The worker was started, so no tunnel of a previous run exists any more (it lived in the helper, and the helper
// lives only as long as this worker's port to it). A browser left pointing at it would have no internet at all.
void service.cleanUpAfterRestart().then(() => service.init());

chrome.runtime.onConnect.addListener((port) => service.accept(port));

chrome.runtime.onMessage.addListener((request: Request, sender, respond) => {
  void service.handle(request, sender).then(respond);
  return true;
});

chrome.contextMenus.onClicked.addListener((info) => void service.onMenu(String(info.menuItemId)));

chrome.commands.onCommand.addListener((command) => {
  if (command === "toggle-connection") void service.toggle();
});

function watchNotifications(): void {
  chrome.notifications?.onButtonClicked.addListener((id) => service.onNotificationButton(id));
  chrome.notifications?.onClicked.addListener(() => void chrome.tabs.create({ url: chrome.runtime.getURL("app.html") }));
}
watchNotifications();
chrome.permissions.onAdded.addListener((permissions) => {
  if (permissions.permissions?.includes("notifications")) watchNotifications();
});

self.addEventListener("online", () => service.controller.networkChanged(true));
self.addEventListener("offline", () => service.controller.networkChanged(false));

chrome.runtime.onInstalled.addListener((details) => {
  void chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: false });
  // The welcome: the first thing a new installation shows is the app's own first-run pages.
  if (details.reason === "install") void chrome.tabs.create({ url: chrome.runtime.getURL("app.html") });
});
