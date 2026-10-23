# Vazie VPN 1.0.0 — installation

This release has two things to install:

| File | What it is |
|---|---|
| `VazieVPN-universal.apk` (also `-arm64-v8a`, `-armeabi-v7a`) | The Android app (Android 9 or newer) |
| `vazie-vpn-1.0.0-chromium.zip` | The extension for Chrome, Edge, Brave, Vivaldi, Arc, Dia and other Chromium browsers on a computer (Chrome 116 or newer) |
| `VazieVPNHelper.pkg` · `vazie-vpn-helper-windows-x64.exe` · `vazie-vpn-helper-linux-x64` | The **helper** the extension needs: one file, for your system (macOS, Windows, Linux) |

Vazie VPN is a client for **your own** VLESS configurations: it comes with no servers, so you need a `vless://` link or a subscription link from your server or provider.

Neither the app nor the extension is in a store yet, so both are installed from the file. Before you install anything, check the file — it takes a minute and tells you the file is the one Vazie published.

> Русская версия: [README.ru.md](https://github.com/vazie-app/VazieVPN/releases/download/v1.0.0/README.ru.md)

---

## 1. Check the file

Every file's SHA-256 is in `SHA256SUMS.txt` in this release.

| System | Command |
|---|---|
| macOS, Linux | `shasum -a 256 vazie-vpn-1.0.0-chromium.zip` |
| Windows (PowerShell) | `Get-FileHash .\vazie-vpn-1.0.0-chromium.zip -Algorithm SHA256` |
| Windows (cmd) | `certutil -hashfile vazie-vpn-1.0.0-chromium.zip SHA256` |

The result must be exactly the line for that file in `SHA256SUMS.txt`. If it differs, do not install the file: download it again, and if it still differs write to support@vazie.app.

The APKs are also signed. With the Android SDK on a computer you can check who signed one:

```
apksigner verify --print-certs VazieVPN-universal.apk
```

The certificate must be `CN=Vazie, OU=Application Signing, O=Vazovsky Development`, SHA-256 `970fbd4614aadaa171fb4c9aa58f6df0adad98c2c6d06252b5d3ad0d54f0a3aa` (also in `SIGNING_CERTIFICATE.txt`).

---

## 2. The Android app

You need Android 9 or newer. `VazieVPN-universal.apk` runs on any supported phone; the other two are smaller builds for 64-bit (`arm64-v8a`) and old 32-bit (`armeabi-v7a`) phones.

1. Download the APK on the phone (or copy it there from a computer).
2. Open it. If Android says it cannot install apps from this source, tap **Settings** and allow **Install unknown apps** for the app you opened the file from (your browser or file manager). You can switch it off again afterwards.
3. Tap **Install**.

If Google Play Protect warns about an unknown developer: the app is new and not in Google Play, so Google does not know the developer yet. Check the file first (section 1), then tap **More details → Install anyway**. You do not need to turn Play Protect off.

The app is signed with the Vazie release key, so it does not install over a Vazie VPN build signed before 2026-10-17: remove such a build first.

Open the app, tap **Add configuration**, paste your `vless://` link (or a subscription link) and connect. Android asks once for permission to set up a VPN.

---

## 3. The browser extension

The extension works in desktop browsers on Chromium: Chrome, Edge, Brave, Vivaldi, Arc, Dia, Opera, Yandex Browser, Chromium. It does not work in Chrome on a phone, in Firefox or in Safari.

**Why a helper?** A browser extension cannot open a VPN tunnel by itself: it cannot run a VPN engine or listen on a port. The helper is a small program on your computer that runs your configuration in Xray (the same engine as in the Android app) and opens a proxy that only your own computer can reach. The extension points the browser at it, and gives the browser its own settings back when you disconnect. Only the browser goes through the tunnel; other programs do not.

### Step 1 — the extension

1. **Unzip** `vazie-vpn-1.0.0-chromium.zip` into a folder you will keep, for example `Documents\VazieVPN` — not Downloads or a temporary folder. The browser runs the extension straight from that folder. The folder must contain `manifest.json`.
2. Open the extensions page and turn on **Developer mode**:

   | Browser | Address |
   |---|---|
   | Chrome | `chrome://extensions` |
   | Edge | `edge://extensions` |
   | Brave | `brave://extensions` |
   | Vivaldi | `vivaldi://extensions` |
   | Arc | `chrome://extensions` |

3. Click **Load unpacked** and choose the folder from step 1.
4. Click the puzzle-piece icon next to the address bar and **pin** Vazie VPN.

The browser may remind you at start-up that extensions in developer mode are on. That is how every extension installed outside a store is treated; you can dismiss it.

### Step 2 — the helper

Open Vazie VPN, then **Settings → Helper**. It offers the installer for your system as one button; you can also take the file from this release. Then:

**macOS** — open `VazieVPNHelper.pkg` and follow the installer (Apple silicon and Intel). The installer is not signed by Apple yet, so macOS may say it cannot verify the developer: **right-click the file and choose Open**, or open **System Settings → Privacy & Security** and click **Open Anyway**.

**Windows** — open `vazie-vpn-helper-windows-x64.exe`. A window appears, installs the helper and says when it is done; press Enter to close it. The file is not signed yet, so Windows SmartScreen may show *Windows protected your PC*: click **More info → Run anyway**.

**Linux** — make the file executable and run it:

```
chmod +x vazie-vpn-helper-linux-x64
./vazie-vpn-helper-linux-x64
```

What the installer does: it finds the Vazie VPN extension in your browsers by itself (you never type an extension id), copies the helper into your user folder, registers it with your browser, and downloads the Xray engine from its own release, checking it against a SHA-256 built into the helper. It needs the internet for that download and no administrator rights on Windows and Linux (the macOS installer asks for your password, as any installer does, and then sets everything up for you, not for the administrator).

Go back to the extension. **It notices the helper by itself** — there is nothing to press. If the Helper screen still asks you to install it after a minute, press **Check again**.

### Step 3 — first connection

1. In the extension, click **+ Add** and paste your `vless://` link or a subscription link. A subscription link makes the browser ask for permission to reach that one server; allow it, and the permission is taken back as soon as the list is downloaded.
2. Check what Vazie VPN read, give it a name, **Save**.
3. Press **Connect**. The toolbar icon shows the state.

To send only some sites through the VPN (or all except some), open **Settings → Split tunneling**. Addresses on your own network (`localhost`, `192.168.x.x`, `.local`) never go through the tunnel.

### Updating

Download the new archive, unzip it **into the same folder** (replace the files), open the extensions page and click the **reload** (circular arrow) button on Vazie VPN.

Do not move or rename the folder: the browser treats an extension from a new location as a new extension.

### Removing

- The extension: click **Remove** on the extensions page.
- The helper: run it with `uninstall`. macOS: `"$HOME/Library/Application Support/Vazie VPN/bin/vazie-vpn-host" uninstall`. Linux: `~/.local/share/vazie-vpn/bin/vazie-vpn-host uninstall`. Windows (cmd): `"%LOCALAPPDATA%\Vazie VPN\bin\vazie-vpn-host.exe" uninstall`. This removes the registration and the downloaded engine.

---

## Problems and questions

- **The Helper screen does not find the helper.** The installer looks for the extension in your browsers, so install the extension first; if you did it the other way round, run the installer again. Then press **Check again**. If your browser is not in the list above (Chrome, Edge, Brave, Vivaldi, Arc, Dia, Opera, Yandex Browser, Chromium), tell us which it is. If you load the extension from a folder (as above), it must already be loaded when you run the installer, or run the installer again afterwards: it learns the extension's id from the browser.
- **"The engine couldn't be installed".** The download needs the internet and `github.com`. Press **Install the engine** on the Helper screen when you are online.
- **"The browser's proxy is taken".** Another extension (or your organisation's policy) controls the proxy settings; Vazie VPN never takes them over. Turn the other one off.
- **The file does not match its checksum** — download it again, and if it still differs write to support@vazie.app.
- **Something does not work** — support@vazie.app, or the Feedback section of the app's About screen. A security problem: [SECURITY.md](https://github.com/vazie-app/VazieVPN/blob/main/SECURITY.md). Never post a real configuration or `vless://` link — it is a credential.

The extension and the helper were tested end to end on macOS (Apple silicon). The Windows and Linux builds are built and unit-tested by the release workflow but have not been run by a person yet; please report what you find.

What the extension protects and what it knowingly does not: [the threat model](https://github.com/vazie-app/VazieVPN/blob/main/docs/security/EXTENSION_THREAT_MODEL.md).

Source code: https://github.com/vazie-app/VazieVPN · License: GPL-3.0-or-later
