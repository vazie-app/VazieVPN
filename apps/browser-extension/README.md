# Vazie VPN for the browser

The Vazie VPN app as a Chromium extension (Chrome, Edge, Brave, Vivaldi, Arc… — Manifest V3, Chrome 116 or newer).
Every function of the Android app that makes sense in a browser:

- **Configurations** — paste a `vless://` link or a subscription link; REALITY and TLS over TCP, WebSocket and gRPC;
  review and name before saving; unknown parameters are kept as they are; rename, duplicate, export, delete; the
  latency of each server. Read by the same rules as Android, against the same test vectors.
- **Connection** — one click to connect and disconnect, the state in the toolbar icon, the session clock, bytes
  down and up, the connection details; the tunnel is proven usable before the browser is pointed at it.
- **Split tunneling by site** — everything, everything but the chosen sites, or only the chosen sites.
- **Always at hand** — the toolbar popup, a side panel and a full tab; the menu under a right click on the icon
  (your recent configurations, connect and disconnect); `Alt+Shift+V`; an optional notification with Disconnect.
- **Comfortable** — the Milk and Night Indigo themes and the ten icons (drawn into the toolbar), the first-run pages
  and tour, the guides and the questions and answers, the About links, English and Russian (from your browser).
- **Private** — no account, no analytics; configurations encrypted at rest; see the
  [threat model](../../docs/security/EXTENSION_THREAT_MODEL.md).

## Install

Download `vazie-vpn-1.0.0-chromium.zip` and the helper for your system from [the release](https://github.com/vazie-app/VazieVPN/releases/tag/v1.0.0),
unzip the extension into a folder you will keep, *Load unpacked* it in `chrome://extensions`, then open Settings →
Helper and follow the button. The step-by-step guide (checking the files, each system's installer, updating, removing,
troubleshooting) is [docs/install/README.en.md](../../docs/install/README.en.md) · [по-русски](../../docs/install/README.ru.md).

It needs the **helper** ([`../native-host`](../native-host)): a browser cannot open a tunnel by itself. Settings →
Helper tells you whether it is found and how to install it. The design is
[ADR 0002](../../docs/adr/0002-browser-extension.md).

## Build and load

```bash
npm ci
npm run check                          # typecheck and tests (the shared vector corpus, the logic, the strings)
node scripts/build.mjs                 # dist/chromium, with the development key → the extension id is always
                                       # apmoogcaoomcoaomkacmlngnknmgieeg (--development: not minified, with source maps)
```

Open `chrome://extensions`, turn on *Developer mode*, *Load unpacked* → `dist/chromium`. Then Settings → Helper in the
extension offers the installer (see [the helper's README](../native-host/README.md)).

```bash
node scripts/package.mjs     # the Chrome Web Store build: no key → dist/store, zipped as dist/vazie-vpn-<version>-chromium.zip
```

The unpacked build carries a public key so that its id never changes; the store refuses a package that has one, so the
store build has none and gets the store's id. The helper does not care which: it finds the extension in the browser.
`VAZIE_API_URL`, `VAZIE_SITE_URL` and `VAZIE_HELPER_URL` set the backend, the site and where the helper's installers
are published. The official addresses are not in this repository: a store build (`--store`, `npm run package`) needs
the first two in the environment, a development build uses the placeholders `https://api.example.com/v1` and
`https://example.com`. The helper defaults to `https://github.com/vazie-app/VazieVPN/releases/download/v<version>`.

## Layout

| Path | What |
|---|---|
| `src/shared` | Pure logic, no browser API, tested with `node --test`: link parser and serializer, subscriptions, the Xray outbound, split tunneling and the PAC script, formats, the backend answers' validation, the strings |
| `src/background` | The service worker: vault, preferences, the connection controller, the native helper client, the proxy, the toolbar and menu, the RPC |
| `src/ui` | The Preact interface: one app on three surfaces (popup, tab, side panel) |
| `public` | Icons (the ten marks and their plates' art), fonts, locales for the store listing |
| `scripts` | `build.mjs`, `package.mjs`, `import-strings.mjs` (Android's words → `src/shared/i18n/android.*.json`), `check-strings.mjs` |
| `tests` | Unit tests, and `e2e` — real-browser tests |

The words: the Android app's strings are imported by `node scripts/import-strings.mjs` (do not edit
`android.*.json`); the extension's own are in `ext.en.json` and `ext.ru.json`. A key missing in either language fails
`npm test`.

## Real-browser tests

They load the built extension into Chrome for Testing with the built helper and a real Xray, so they are not part of
`npm run check`:

```bash
npm i --no-save playwright-core
node scripts/build.mjs --development
(cd ../native-host && cargo build)
# a HOME whose engine is installed:   HOME=/tmp/vazie-home ../native-host/target/debug/vazie-vpn-host engine
HOME=/tmp/vazie-home node tests/e2e/host.mjs                     # the helper alone, over native messaging
HOME=/tmp/vazie-home node tests/e2e/extension.mjs --screenshots /tmp/shots   # the whole flow, in the browser
node tests/e2e/no-helper.mjs                                     # no helper, and a proxy left by a dead tunnel
```

`extension.mjs` runs a local Xray as "the user's server", adds a configuration through the interface, connects, loads a
page through the tunnel and checks that the engine counted it, splits a site, disconnects, and checks that the
browser's proxy settings came back. Chrome for Testing is found under `~/Library/Caches/ms-playwright` (or set `CHROME`).
What cannot be automated: a subscription link needs a trusted HTTPS server; try it by hand.
