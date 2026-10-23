// The extension's manifest, as code, so every permission sits beside the reason it is there.

/** The public half of the development key: it gives an unpacked build the same extension id on every machine, so
 * the helper's manifest can name it. A store build has the id the store gives it (see the helper's `install`). */
export const DEVELOPMENT_KEY =
  "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxtQnsdWreKFm+uRYWZv62yjB8rOdFE9Ba5twOjBwdMoDsKGCKPArShadaRZuX30M64seJmNg0eJwV3WxqAi9hglj6/FDlX4a24NGUszUhI1n2y+Ip5+TcHlFlaMXCJNgE2c5Ldb3umO5TKYj5qYM2+gbg8atZjTrlWe5wXBwsNMru932DlYhdB74ingd7SlPUMZGh5a6p0h7V1QlL5+YRLtag2j6WX3Eem7FBb6Ie/SkCKKO36L6f+PcmfalY1tMUVmA4+xUvmVzxEXMBtHxuBppuplWmZWd3qA+Zbiza341dxEXUTh6y3+zHBZ3MHkPrOVa1Mv8dNnuqKWRBJ57LQIDAQAB";

export interface ManifestOptions {
  version: string;
  apiUrl: string;
  /** Include `key`: for unpacked builds only; the Chrome Web Store refuses a package that has one. */
  withKey: boolean;
}

export function manifest({ version, apiUrl, withKey }: ManifestOptions): Record<string, unknown> {
  const apiOrigin = `${new URL(apiUrl).origin}/*`;
  return {
    manifest_version: 3,
    name: "__MSG_extName__",
    description: "__MSG_extDescription__",
    default_locale: "en",
    version,
    minimum_chrome_version: "116",
    ...(withKey ? { key: DEVELOPMENT_KEY } : {}),
    icons: { 16: "icons/icon-16.png", 32: "icons/icon-32.png", 48: "icons/icon-48.png", 128: "icons/icon-128.png" },
    background: { service_worker: "background.js", type: "module" },
    action: {
      default_popup: "popup.html",
      default_title: "__MSG_extName__",
      default_icon: { 16: "icons/icon-16.png", 32: "icons/icon-32.png", 48: "icons/icon-48.png" },
    },
    side_panel: { default_path: "sidepanel.html" },
    commands: {
      "toggle-connection": {
        suggested_key: { default: "Alt+Shift+V" },
        description: "__MSG_commandToggle__",
      },
    },
    permissions: [
      // The helper that runs the engine. Without it there is no tunnel: a browser cannot open a socket or run Xray.
      "nativeMessaging",
      // Point the browser at the helper's loopback endpoint, and nothing else.
      "proxy",
      // Keep WebRTC from announcing the real address while every site goes through the tunnel.
      "privacy",
      // Preferences and the cached About links and questions.
      "storage",
      // The menu under the toolbar icon: the recent configurations, connect and disconnect.
      "contextMenus",
      "sidePanel",
      // Paste a configuration from the clipboard, at the person's click.
      "clipboardRead",
      // The site of the tab the person is looking at, for \"split this site\", only after they click the icon.
      "activeTab",
      // Name the client \"VazieVPN\" when a subscription list is downloaded (what the app does).
      "declarativeNetRequestWithHostAccess",
    ],
    // Only the Vazie backend's public endpoints: the About links and the questions. No session, no cookies.
    host_permissions: [apiOrigin],
    // Asked for at the moment they mean something: the status notification, and a subscription's own server.
    optional_permissions: ["notifications"],
    optional_host_permissions: ["https://*/*"],
    incognito: "spanning",
  };
}
