# Vazie VPN for macOS

A calm Mac client for your own VLESS configurations — the same functions as the Android app, made for a Mac.

Bring a configuration or a subscription from your own server or provider. Vazie VPN keeps it encrypted on this Mac
(the key is in your Keychain) and connects in one click — no account, no ads, no analytics.

## What it does

| Android | On the Mac |
|---|---|
| `vless://` links and subscriptions (REALITY, TLS; TCP, WebSocket, gRPC) | the same parser, run against `shared/vless-vectors` |
| Review, rename, duplicate, export, delete; latency per configuration | the same; export copies the link and clears the clipboard after 30 s |
| One-tap connect; the tunnel's state in a notification | Connect in the window, the menu bar or the Dock; a notification with a Disconnect button |
| Split tunneling by app | split tunneling **by site** (all / all but the chosen / only the chosen) |
| Home-screen widgets and the Quick Settings tile | the menu-bar panel |
| Launcher shortcuts | the Dock menu (up to three recent configurations) |
| Two themes, ten icons and their plates | the same; the Dock icon follows the choice and wears the state's dot |
| Onboarding, tour, tips, guides, FAQ, About, privacy | the same; links and questions come from the backend only |

English and Russian, by the system's language.

## Two ways to reach the Mac

**Whole Mac** (beta, not the default until it is proven on every Mac it will be released for): a TUN interface that every program uses, like Shadowrocket's.
One administrator prompt per connection (Touch ID works) starts a small supervisor as root. The supervisor is not read from the app
bundle: its text is carried inside the signed executable and written by the prompt's command into `/private/var/run/app.vazie.vpn.tun`,
a directory only root can change. It copies the engine there and runs it only if the copy is signed by the app's team or is a
pinned build; it accepts the engine's configuration only through a pipe and only after an allow-list check (no log files, nothing
listening beyond the loopback); it gives the interface its address, sends the Mac's IPv4 into it, keeps the server's own
connection off it, points DNS at a resolver inside the tunnel and turns IPv6 off for the service. It says "running" only after a
request made like any program's has gone through, and it undoes everything when told to stop, when the app is gone (the same
process, not just the same number), or when the engine dies. Another connected VPN is named and the connection refused.

* **Kill Switch** (Settings, Whole Mac only, off by default): a pf anchor (`com.apple/050.VazieVPN`, under the system's own
  `anchor "com.apple/*"`; nothing else is touched and nothing is flushed) that drops everything except the tunnel, the one
  connection to the server, DHCP, the local network and IPv6 link-local. If the engine is lost the block stays and the app says
  "Blocked"; Disconnect lifts it. It holds while Vazie VPN runs; if the app is quit, killed, or the Mac restarts, the block is
  lifted so that the Mac is never left without internet. See [MACOS_HARDENING_REPORT.md](MACOS_HARDENING_REPORT.md) for what it
  does not cover.
* **Reconnect automatically** (Settings, on by default): after a lost engine, a changed network (Wi-Fi to Ethernet, a new
  gateway), a sleep, or a server that stopped answering, with doubling delays, a limit, and no new password (the same supervisor
  starts the engine again). "Connected" is only said after the tunnel has been proven; while it is being brought back the app
  says "Reconnecting", and when it gives up it says "Connection lost" and whether the traffic is blocked or going out directly.
* **Recovery**: the Mac's DNS and IPv6 are written down (authenticated, atomically) before they are changed, and put back at
  the next launch if the supervisor could not do it (killed, or the Mac restarted); only what is still the tunnel's is put back.

Settings → Connection mode switches between them.

## How the system-proxy mode works

A Mac has no per-app VPN without a system extension, so the app runs the pinned **Xray** itself and points the system
proxy at it (see [ADR 0003](../../docs/adr/0003-macos-app.md)). While connected, the HTTP, HTTPS and SOCKS proxy of
your Wi-Fi and Ethernet services are the engine's loopback ports; with a split tunnel they are a proxy auto-config
script served on `127.0.0.1`. What the settings said before is recorded first and put back on disconnect, on quit, and
on the next launch if the app was killed. Programs that ignore the system proxy (command-line tools, some games) are not
tunnelled.

## Build

You need Xcode 16 or newer (Swift 6) on macOS 14 or newer.

```bash
swift test                          # the tests: the shared corpus, the logic, both languages, the fakes
scripts/build-app.sh                # build/Vazie VPN.app, with the engine inside (verified against its pinned SHA-256)
scripts/build-app.sh --out DIR      # the same, into DIR (so an app that is running from build/ is not replaced under it)
scripts/embed-tun-script.sh         # after editing packaging/tun-run.sh: regenerates the copy carried inside the app (a test checks they agree)
scripts/build-app.sh --release --version 1.0.0 --build 1 --identity "Developer ID Application: …"
open "build/Vazie VPN.app"
scripts/make-dmg.sh --version 1.0.0 # build/VazieVPN-macOS-1.0.0.dmg: the installer window (art in packaging/dmg-background-art.png)
```

The installer is laid out by Finder itself, so `make-dmg.sh` needs a logged-in Mac (it asks Finder through AppleScript and
checks that the finished image kept the layout); it does not run on a headless CI machine. Signing and notarizing it is the
owner's step (the script prints the `notarytool` line).

Without `--identity` the app is signed with the first *Apple Development* identity in the keychain, else ad hoc.
Notarization is the owner's step.

Tests that need the Xray engine on this Mac, or that change real network settings, skip themselves:

```bash
# applies and restores the proxy on ONE service, which should carry no traffic (an unused USB tether, say)
VAZIE_E2E_PROXY_SERVICE="iPhone USB" swift test --filter RealNetworkSetupTests
```

`VAZIE_PROXY_SERVICES="iPhone USB"` (environment of the running app) narrows which services the app touches — a testing
aid, so a development build can be tried without moving the traffic of the connection you are using.

## Uninstalling

Settings → Delete Vazie VPN closes the tunnel, puts the Mac's proxy settings back, ends the engine, removes what the app
keeps (preferences, caches, saved state; the engine too, unless the browser extension's helper is installed beside it),
takes the app out of Launch Services, moves it to the Trash and quits. **Your configurations and their Keychain key are kept**
(a reinstall finds them); ticking "Also delete my configurations" removes `profiles.vault` and the key as well, for good.

If the app was half removed by a cleaner, run what is left of it from the Terminal (from the installer image, if it is gone):

```bash
"/Applications/Vazie VPN.app/Contents/MacOS/VazieVPN" --uninstall [--delete-configurations]
```

And an app whose bundle is deleted or trashed while it runs notices within a few seconds, puts the network settings back
and quits, so a cleaner no longer leaves a process, a menu-bar icon and a dead proxy behind.

## Layout

```
Sources/VazieCore/   everything that is not a view: parser, Xray config, vault, engine, system proxy, the AppModel
Sources/VazieVPN/    the SwiftUI app: screens, menu-bar panel, Dock menu, notifications
Tests/               XCTest
scripts/             build-app.sh, make-icns.swift, sync-strings.sh
packaging/           Info.plist, tun-run.sh (the root supervisor; carried inside the executable, not in the bundle)
```

The words: `android.*.json` and `ext.*.json` are copies (`scripts/sync-strings.sh`); the Mac's own are `mac.*.json`.

## Not done

- A Network Extension packet tunnel (no password per connection), which needs Apple's entitlement; the whole-Mac mode
  asks for the password each time instead. A privileged helper bound to the app's signature (`SMAppService`) would replace the
  prompt; it needs a Developer ID.
- The root parts (the supervisor, the pf kill switch) are tested against stand-ins, as the ordinary user; what only root can show
  is a manual test list in MACOS_HARDENING_REPORT.md.
- Developer ID signing, notarization and publishing the build; the update row opens the releases page.
- A Mac with *another* VPN or proxy app active (Shadowrocket sets a global proxy, for one) is not fought over: its proxy
  outranks the per-service settings this app writes, so the app checks what the system reports after connecting (and
  every few seconds after) and says "another VPN or proxy app controls this Mac's network settings" instead of showing a
  tunnel nothing uses. Turn the other one off to connect.
