# Vazie VPN helper

The small native program behind the browser extension. A browser extension cannot open a tunnel: it cannot run a VPN
engine or listen on a port. The browser starts this program over Chrome's native messaging (`app.vazie.vpn`); it runs
the person's own configuration in **Xray** and exposes a **loopback-only HTTP proxy**; the extension points the browser
at it. When the browser closes, so does the helper, and the tunnel with it.

The design is [ADR 0002](../../docs/adr/0002-browser-extension.md); the threat model is
[here](../../docs/security/EXTENSION_THREAT_MODEL.md).

## Install

**An ordinary person** opens an installer and does nothing else. The extension's Helper screen offers the file for
their system and notices the helper by itself once it is installed.

| System | The file | Notes |
|---|---|---|
| macOS | `VazieVPNHelper.pkg` (`packaging/macos/build-pkg.sh`, universal) | its post-install step runs the helper as the person at the keyboard |
| Windows | `vazie-vpn-helper-windows-x64.exe` | double-click; it installs and says so |
| Linux | `vazie-vpn-helper-linux-x64` | make it executable and open it |

Running the helper with **no arguments** is the same as `install`: it finds the Vazie VPN extension in the person's
browsers (it reads where each profile records its extensions, so the extension's id is never typed — a store build, an
unpacked build and a copy with another id are all found), copies itself to its permanent place, registers with those
browsers and downloads the engine, in the person's language (English or Russian), and keeps its window open until it
is read.

From a terminal:

```bash
cargo build --release
./target/release/vazie-vpn-host install            # --extension-id ID only if the extension can't be found
```

It writes the browser's native messaging manifest (Chrome, Chrome Beta and Canary, Chromium, Edge, Brave, Vivaldi, Arc, Dia, Opera, Yandex Browser — those
the person has run; `--all-browsers` for every one) naming only the extensions it found (plus the development id
`apmoogcaoomcoaomkacmlngnknmgieeg`) as allowed to start it.

| Command | Does |
|---|---|
| `[install] [--extension-id ID]… [--all-browsers] [--skip-engine] [--quiet]` | places the helper, registers it, installs the engine |
| `engine` | downloads and verifies the pinned Xray |
| `status` | the helper, the platform, the engine, and the extensions found |
| `uninstall [--keep-engine]` | removes the registrations (and the engine) |

Where things go — macOS `~/Library/Application Support/Vazie VPN`, Linux `~/.local/share/vazie-vpn`, Windows
`%LOCALAPPDATA%\Vazie VPN`: `bin/` the helper, `engine/<version>/` the engine.

## The engine

Xray-core **v26.7.28** (the version libXray v26.7.28 in the Android app is built on), per platform, pinned by SHA-256
in `src/engine.rs`: macOS (Apple silicon, Intel), Linux (x86-64, arm64) and Windows (x86-64, arm64). The download is
hashed as it is written and deleted unless it matches. To move to another release, change `VERSION` and the table
together (the digests are on the release's asset list).

## Messages (native messaging, JSON)

| `type` | Request | Reply |
|---|---|---|
| `hello` | | `version`, `platform`, `engine: { installed, supported, version, pinned }` |
| `engine.install` | | `ok` (events `engineProgress`) |
| `start` | `outbounds` (`proxy` VLESS, `block`) | `port`; or `code`: `engine`, `request`, `config`, `bind`, `timeout` |
| `ready` | | `ok`; or `not_ready` with `stage` `tls` or `dns` — the same two checks the app's readiness probe makes |
| `traffic` | | `rx`, `tx` since the engine started |
| `probe` | `host`, `port` | `ms` — a TCP handshake over the machine's own network, best of two; `null` for no answer |
| `stop` | | `ok` |

The helper writes the inbounds itself (HTTP proxy and stats API on `127.0.0.1`, ports it picks) and refuses any other
outbound. The configuration goes to Xray's standard input, never a file; the engine runs with logging off and its output
is never forwarded. An engine that ends by itself is reported once (`exited`).

## Packaging and signing

`packaging/macos/build-pkg.sh` makes the package; the CI job `extension.yml` builds the three installers with the names
above and their SHA-256. **Signing is the owner's**: a package signed with a *Developer ID Installer* identity and
notarized (`APP_SIGN_IDENTITY`, `PKG_SIGN_IDENTITY`, `NOTARY_PROFILE` in the environment; none is in this repository)
opens without a warning. Unsigned, macOS asks the person to right-click → Open and Windows SmartScreen shows "More info
→ Run anyway", which the Helper screen says in words. The installers are published as release assets at
`VAZIE_HELPER_URL` (default `https://github.com/vazie-app/VazieVPN/releases/download/v<version>`); the extension's download
button points there. The installers are added to the release of the same version by the manually started workflow `release-extension.yml`
(which uses no secret). The user-facing guide is [docs/install](../../docs/install/README.en.md).

## Develop

```bash
cargo test && cargo clippy --all-targets
HOME=/tmp/vazie-home cargo run -- engine      # a scratch HOME keeps your real browser profile untouched
```

The end-to-end checks are in `../browser-extension/tests/e2e`. The Windows paths (registry registration, a hidden
console for the engine) are written but have not been run on Windows.
