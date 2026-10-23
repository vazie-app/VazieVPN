# ADR 0002: The browser extension and its helper

- Status: Accepted
- Date: 2026-10-19

## Context

The brief: an extension for Vazie VPN whose functions are the app's functions. The app is a client for the person's own
VLESS configurations (REALITY and TLS, over TCP, WebSocket and gRPC), with subscriptions, a latency reading, split
tunneling, two themes and ten icons, guides, a FAQ and About links from the backend, English and Russian, and no
account, ads or analytics.

A browser extension **cannot be a VPN client by itself**. It cannot listen on a socket, it cannot run Xray, and a
`WebSocket` it opens cannot speak VLESS+REALITY. What it can do is tell the browser where its proxy is
(`chrome.proxy`). So the tunnel needs a program outside the browser.

## Decision

**1. Two parts.** The extension is the client; a small native **helper** (`apps/native-host`, Rust) runs the engine.
The extension starts the helper with Chrome's native messaging (`app.vazie.vpn`), so the helper lives exactly as long
as the extension's port to it: close the browser and the tunnel is gone.

**2. The engine is Xray**, the same one the app embeds (libXray v26.7.28 is Xray-core v26.7.28). The extension turns
a stored configuration into the *outbounds* with the app's own rules (`xrayConfig.ts` is `XrayConfigFactory.kt`,
including the raw network name and the pre-quantum fingerprints). The helper writes the *inbounds*: an HTTP proxy and
the stats API, both bound to `127.0.0.1` on ports it picks. The configuration reaches Xray on its standard input,
never a file. The helper refuses any outbound but `proxy` (VLESS) and `block`.

**3. The browser is pointed at the helper by a PAC script** the extension generates. It fails closed: a request meant
for the tunnel is answered with the tunnel's address alone. Names are resolved by the server (the proxy gets the
host name), which is the app's "remote resolution". The browser's own network is never left pointing at a dead
tunnel: the worker clears the proxy at every start, before anything else.

**4. Split tunneling is by site.** The app's three modes (all, all but the chosen, only the chosen) apply to sites
instead of apps; a site covers its subdomains; addresses on the person's own network always go direct. A change
applies to the live PAC without reconnecting.

**5. The tunnel is proven before the browser is told to use it.** After the engine is up, the helper does what the app's
readiness probe does: finish TLS with an address through the tunnel, then reach a name through it. Only then does the
extension set the proxy. An unusable tunnel never takes the browser's traffic with it.

**6. State lives in the service worker**, which a connected native port keeps alive. Pages (popup, tab, side panel) are
thin: typed RPC over `runtime.sendMessage`, and one port on which the worker pushes the whole state (`shared/api.ts`,
`state.ts`). No type in that state can hold a secret; a credential leaves the worker only through "show" and
"export", one at a time.

**7. Configurations are encrypted at rest** with an AES-GCM key that is a non-extractable `CryptoKey` in IndexedDB
(the browser's counterpart of the Android Keystore key). Preferences are plain, as in the app. Nothing uses
`storage.sync`.

**8. Words come from the backend where the app takes them from the backend**: the About links (`/apps/vpn/links`)
and the questions (`/faq/vpn`), cached, with no shipped fallback.

**9. The engine is downloaded by the helper, pinned by version and SHA-256 per platform**, as the Android build pins
libXray. A mismatch deletes the download.

**10. Permissions**, each with its reason (see `manifest.ts`): `nativeMessaging` (the helper), `proxy`, `privacy`
(WebRTC), `storage`, `contextMenus`, `sidePanel`, `clipboardRead` (paste at a click), `activeTab` (the tab's site, after a
click), `declarativeNetRequestWithHostAccess` (name the client `VazieVPN` for a subscription download). Host access:
the Vazie backend only. Optional, asked at the moment they mean something: `notifications`, and the one origin of a
subscription link (taken back as soon as the list is in).

## What maps to what

| Android                                   | Extension                                                        |
| ----------------------------------------- | ---------------------------------------------------------------- |
| `VpnService` and the TUN                  | the helper's loopback proxy and the PAC script                   |
| VPN consent dialog ("Preparing")          | starting the helper ("Preparing")                                |
| Android Keystore                          | non-extractable `CryptoKey`                                      |
| Split tunneling by app                    | split tunneling by site                                          |
| Widgets, Quick Settings tile              | toolbar icon with the state, popup, side panel                   |
| Launcher shortcuts                        | the menu under a right click on the icon (same label filter)     |
| Status notification with Disconnect       | optional notification with Disconnect                            |
| Share sheet for export                    | the clipboard, cleared after 30 seconds                          |
| Check for updates (backend version)       | the browser's own update check (`runtime.requestUpdateCheck`)    |
| Launcher icon and plate                   | toolbar icon, drawn from the same art and plates                 |

## Differences that are deliberate

- **A helper to install.** Unavoidable; the Home screen and Settings → Helper say so, in words, whenever it is missing.
- **Only the browser is tunnelled.** Other programs on the computer are not (a system tunnel would be a different product).
- **No update notice from the backend** for the extension itself: stores update their own copies, and the backend
  publishes no extension line. Sideloaded builds say there is nothing to ask.
- **No login on the loopback proxy.** Chrome answers a proxy's login challenge only for an extension that can read
  every site; asking for `<all_urls>` in a VPN extension would cost more trust than the login buys. The proxy is bound to
  `127.0.0.1` on a port that changes with every connection. See the threat model.
- **UDP is not carried** (an HTTP proxy cannot): the browser falls back from QUIC to TCP, and WebRTC is held back while
  every site uses the tunnel.

## Alternatives considered

- **Xray (or sing-box) compiled to WebAssembly in the extension.** Rejected: no raw sockets, so no REALITY or TCP.
- **A VPN over WebSocket from a service worker.** Rejected: only works for one transport and cannot be a proxy for the
  browser (an extension cannot listen).
- **SOCKS5 instead of HTTP.** Rejected: Chrome ignores credentials for SOCKS and resolves names locally unless told
  otherwise; HTTP CONNECT keeps the host name for the server.
- **Node or Python for the helper.** Rejected: the person would need a runtime. A static Rust binary is one file.
- **An installer package (pkg/msi) first.** Left to release engineering: signing and notarization belong to the owner's
  accounts. `vazie-vpn-host install` does the same job from a downloaded binary.
