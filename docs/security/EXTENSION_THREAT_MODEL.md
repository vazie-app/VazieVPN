# Threat model: the browser extension and its helper

What is protected, from whom, and what is knowingly not.

## Assets

1. The configurations (UUIDs, REALITY keys, server addresses) and subscription links.
2. The person's traffic while the tunnel is up: that it goes through their server and not around it.
3. The browser's network settings: that a dead tunnel does not leave the browser without internet.

## Trust boundaries

```
 web pages ──✕──▶ extension pages ◀──▶ service worker ◀── native port ──▶ helper ──▶ Xray ──▶ the person's server
 other extensions ✕                         │                              │
                                            └─ IndexedDB (encrypted)       └─ 127.0.0.1 only
```

## Threats and answers

| Threat | Answer |
| ------ | ------ |
| A web page or another extension asks the worker for a configuration | The worker answers only `runtime.sendMessage` from its own extension (`sender.id`); the extension has no content scripts and exposes no web-accessible resources. |
| Another extension starts the helper | The helper's manifest lists the allowed extension ids; Chrome refuses any other origin. |
| A configuration is read off disk | Encrypted with an AES-GCM `CryptoKey` that is non-extractable. Copying the profile's files does not copy the key's material. Code running *inside* the extension can use the key — as an app's own process can use its Keystore key. |
| A secret reaches the interface | No type in the interface's state can hold one. The UUID is shown only on "Show" and clears itself after a minute; an export copies the link and clears the clipboard after 30 seconds if it is still there. A `Secret` wrapper prints as `Secret(…)` in any string or JSON. |
| A configuration reaches a log | Nothing is written to the console; the helper starts Xray with `loglevel: none` and never forwards the engine's own messages (they can quote the configuration). |
| The configuration touches the disk in the helper | It goes to Xray's standard input. |
| A request makes the helper a way onto the open internet | The helper composes the inbounds itself and accepts only a `proxy` (VLESS) and a `block` outbound. |
| The helper downloads a tampered engine | The download is hashed while it is written and deleted unless it matches the SHA-256 pinned per platform in the source. |
| A subscription server learns about the person | One `GET` with `Accept: text/plain`, no cookies, no referrer, client named `VazieVPN`, over `https` only, limited to 1 MiB and 20 s. The browser permission for that origin is requested at the click and removed afterwards. |
| DNS leaks | The proxy gets host names, and the server resolves them. The PAC never resolves anything. |
| WebRTC reveals the real address | While every site uses the tunnel, `webRTCIPHandlingPolicy` is `disable_non_proxied_udp`. With sites split out, some traffic is meant to be direct and the browser's own policy stays. |
| The tunnel dies and the browser keeps pointing at it | The PAC returns the tunnel's address with no `DIRECT` fallback, so nothing leaks while it is dead; and the worker clears the proxy at every start and whenever the engine exits. |
| The tunnel is unusable at connect | The helper proves TLS-through-the-tunnel and name-through-the-tunnel *before* the browser is pointed at it. |
| Another extension controls the proxy | Detected (`levelOfControl`) and reported before anything starts; Vazie never takes the setting. |

## Accepted risks

- **The loopback proxy has no login.** Any program running as the same person on the same computer can find the port
  and send traffic through the person's server. Such a program can already read the browser's memory and the
  person's files; the proxy adds little. Chrome does not answer proxy logins for extensions without access to every
  site, so a login would cost the extension the permission this model most wants it not to have. The port changes
  with every connection, and the proxy listens on `127.0.0.1` only.
- **Only the browser is tunnelled.** Other programs, and other browsers, are not.
- **A person who can write to the extension's storage can use the vault key.** The same is true of any app's own
  process on any platform.
- **The helper is a binary the person runs.** It is built from this repository and its release is checksummed;
  signing and notarization are the release process's job, not this document's.
- **`sendMessage` is JSON**, so the interface receives the share link as text at export; that is the point of
  exporting.
