<div align="center">

<img src="apps/android/docs/assets/brand/vazie-app-icon-rounded.png" width="112" alt="Vazie VPN icon">

# Vazie VPN

**A calm client for your own VLESS configurations — on Android and in your browser.**

Bring a configuration or a subscription from your own server or provider. Vazie VPN keeps it encrypted on your
device and connects in one tap — no account, no ads, no analytics.

[Android app](apps/android/README.md) · [Browser extension](apps/browser-extension/README.md) · [Privacy](#privacy) · [vazie.app](https://vazie.app)

**English** · [Русский](README.ru.md)

</div>

## What is in this repository

| | |
|---|---|
| [`apps/android`](apps/android) | The Android app: VLESS over REALITY and TLS (TCP, WebSocket, gRPC), subscriptions, split tunneling by app, widgets, a Quick Settings tile. |
| [`apps/browser-extension`](apps/browser-extension) | The same app as a Chromium (Manifest V3) extension: configurations, subscriptions, latency, split tunneling by site, themes, icons, guides, FAQ, English and Russian. |
| [`apps/native-host`](apps/native-host) | The helper the extension starts over native messaging. It runs Xray — the engine the Android app embeds — on a loopback-only proxy. |
| [`shared/vless-vectors`](shared/vless-vectors) | The corpus of links both parsers must read the same way. |
| [`docs`](docs) | Decisions and threat models that span the clients. |

Vazie VPN is a client. It does not come with servers: it connects to the VLESS server you already have.

## Install

Everything is in [the latest release](https://github.com/vazie-app/VazieVPN/releases/latest); the full guide, with
checks, screenshots-in-words and troubleshooting, is [docs/install/README.en.md](docs/install/README.en.md).

**Android** (9 or newer): download `VazieVPN-universal.apk`, open it, allow installing from the file if asked, add your
`vless://` or subscription link, connect.

**Browser** (Chrome, Edge, Brave, Vivaldi, Arc — Chrome 116 or newer), in two steps:

1. **The extension.** Unzip `vazie-vpn-1.0.0-chromium.zip` into a folder you will keep, open `chrome://extensions`, turn on
   *Developer mode*, **Load unpacked**, choose the folder, pin Vazie VPN.
2. **The helper.** A browser cannot open a tunnel on its own, so the extension has a small program on your computer that
   runs your configuration in Xray on a proxy only your computer can reach. Open Vazie VPN → **Settings → Helper**: one
   button downloads the installer for your system (`VazieVPNHelper.pkg` for macOS, `vazie-vpn-helper-windows-x64.exe`,
   `vazie-vpn-helper-linux-x64`). Open it — that is all: it finds the extension by itself, registers with your browser
   and downloads the engine (checked against a pinned SHA-256). The extension notices when it is done.

The files are not signed yet: macOS says it cannot verify the developer (right-click → Open), Windows SmartScreen says
*More info → Run anyway*. Check each file against `SHA256SUMS.txt` first. From source: `apps/native-host` and
`apps/browser-extension` have their own READMEs.

The design is in [ADR 0002](docs/adr/0002-browser-extension.md); what the extension protects and what it knowingly does
not is in [the threat model](docs/security/EXTENSION_THREAT_MODEL.md).

The design is in [ADR 0002](docs/adr/0002-browser-extension.md); what it protects and what it knowingly does not is in
[the threat model](docs/security/EXTENSION_THREAT_MODEL.md).

## Privacy

- **No account.** Neither client ever asks who you are.
- **Configurations stay on your device**, encrypted with a key the platform holds (Android Keystore; a non-extractable
  key in the browser profile). They are never uploaded.
- **No analytics, crash reporting, advertising or tracking** in either client.
- **What the clients send:** to the Vazie backend — an update check (Android only), the About links and the answers to common questions; nothing
  about you, your device or your configurations. A subscription link is downloaded directly from its own server over
  HTTPS.

A VPN changes who can see your traffic; it does not make you anonymous. The server you connect to, and its provider,
can see your connections.

## Security, contributing, license

Please report vulnerabilities privately: [SECURITY.md](SECURITY.md). Never post a configuration or a `vless://` link —
it is a credential. Contributions: [CONTRIBUTING.md](CONTRIBUTING.md), [Code of Conduct](CODE_OF_CONDUCT.md).

Vazie VPN is free software under the **GNU General Public License, version 3 or (at your option) any later version** —
see [LICENSE](LICENSE). The names "Vazie" and "Vazie VPN" and the icons are not licensed as marks:
[TRADEMARKS.md](TRADEMARKS.md).

Made by **Vazovsky** — [vazie.app](https://vazie.app) · support@vazie.app
