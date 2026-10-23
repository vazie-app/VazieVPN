# ADR 0003: The macOS app: a system-proxy client, not a system extension

- Status: Accepted
- Date: 2026-10-20

## Context

Vazie VPN is to run on a Mac with the functions the Android app has: your own VLESS configurations and subscriptions,
one-tap connect, split tunneling, the state outside the app (widgets, the Quick Settings tile, launcher shortcuts, the
status notification), two themes and ten icons, guides, a tour, the About links and the questions from the backend.

A Mac cannot do what Android's `VpnService` does without help from Apple. A real per-app VPN is a Network Extension
(a packet tunnel provider), which needs a paid developer account's entitlement, a provisioning profile and, for
distribution, notarization. The owner has none of these in hand today.

## Decision

A native SwiftUI app (`apps/macos`, a Swift package) that runs the pinned **Xray** itself and points the Mac's network
settings at it:

1. **Engine.** Xray v26.7.28 (the version libXray in the Android app is built on), pinned by SHA-256 per
   architecture. The build puts it inside the app; the app can also download and verify it. It gets the configuration
   through a pipe (never a file), listens on three loopback ports (HTTP, SOCKS, stats), and logs nothing.
2. **Route.** While connected, `networksetup` points the HTTP, HTTPS and SOCKS proxy of every enabled hardware service
   (`enN`, `bridgeN`) at the engine. With a split tunnel it points them at a proxy auto-config script served on the
   loopback address instead (the same script the browser extension generates, and it fails closed).
3. **Put it back.** What the settings said before is written to a record first and restored on disconnect, on quit and,
   if the app was killed, on the next launch (which also ends the engine the crash left behind).
4. **Split by site.** A system proxy knows names, not programs, so Android's per-app list becomes a per-site list with
   the same three modes, as in the extension.
5. **Outside the window.** A menu-bar panel (the widget and the tile), the Dock menu (the launcher shortcuts), the app
   icon chosen in Appearance (with the state's dot), a notification with a Disconnect button.
6. **Shared, not copied by hand.** The link parser is run against `shared/vless-vectors` like the other two clients;
   the words come from Android's `strings.xml` through the extension's import, plus `mac.{en,ru}.json`.

## Consequences

- Programs that ignore the system proxy (command-line tools, some games) are not tunnelled. The guide and the privacy
  page say so. A packet tunnel, to carry everything, is a separate piece of work that needs the entitlement.
- Another VPN with its own proxy settings active at the same time (Shadowrocket and similar) can override the system
  proxy; the app does not fight it.
- No account, no server list, no analytics, as everywhere else. The app talks to the Vazie backend (links, questions),
  to a subscription's own server when asked, and to the engine's GitHub release if the engine is downloaded.
- Signing with Developer ID and notarization are the owner's steps; `scripts/build-app.sh --identity` takes the identity.

## Alternatives considered

- **Network Extension packet tunnel.** The right answer for a full-device, per-app VPN; blocked on the entitlement.
- **Xray's `tun` inbound with routes set by a root helper.** Works for all traffic but needs a privileged helper (an
  admin prompt per connect, or an installed daemon) and rewrites the routing table next to other VPNs. Not shipped
  untested; left as a follow-up.
- **Wrapping the browser extension's helper.** It exists to be started by a browser; the app does the same work
  directly and shares only the engine directory.

## Update 2026-10-20: the whole-Mac mode

A second mode was added: a TUN interface for every program, started as root through the system's administrator prompt (`packaging/tun-run.sh`). It is offered as a beta and is **not** the default: its script is tested against stand-ins for the system commands, but it has not been run against a real server. The system proxy stays the default until it has. Settings → Connection mode switches.

## Update 2026-10-20 (later): hardening

The whole-Mac mode has since run against a real server, and was then audited and reworked: see
[0004-macos-hardening.md](0004-macos-hardening.md). The root script is no longer read from the app bundle.
