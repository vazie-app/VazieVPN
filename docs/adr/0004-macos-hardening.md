# ADR 0004: macOS hardening: a root supervisor that trusts nothing the user can write, a kill switch, recovery

- Status: Accepted (the privileged paths are not yet verified on a real Mac as root; see the report)
- Date: 2026-10-20

## Context

An audit of the whole-Mac mode (ADR 0003, update) found, among other things: the script run as root lived in the app bundle
(writable by the user in the usual install), the control directory it wrote `status` into and deleted `stop` from was the
user's, the engine's configuration came through a pipe the user made and was handed to the engine unchecked (a `log.access`
path would have been written as root), the engine binary was not checked at run time, the original DNS and IPv6 lived only in a
shell variable (lost on a kill or a reboot), nothing watched the tunnel in this mode, a lost engine put the traffic back on the
open network, and a split set to "only these sites" sent their DNS lookups out directly.

## Decision

Keep the architecture (Xray + the system's administrator prompt + a TUN interface) and change who is trusted:

1. **The trust anchor is the signed executable.** The supervisor's text is carried inside it (`TunScriptSource.swift`, generated
   from `packaging/tun-run.sh`, a test keeps them equal). The prompt's command writes it into `/private/var/run/app.vazie.vpn.tun`
   (root's parent, root's directory) and runs it from there. Nothing in the bundle or in the user's folders is run as root.
2. **The user's channel is a pipe, and what comes through is checked.** `cmd` is a FIFO owned by the user inside the root
   directory (it can be written to and cannot be replaced). `CONFIG <nonce> <json>` is checked by an allow-list (JavaScript for
   `osascript`, always present) before the engine sees it; the server's address, port and the tunnel's name are taken from the
   checked document, not from arguments. Status flows back in a root-written file with the same nonce.
3. **The engine is verified as a copy.** The supervisor copies it and checks the copy: signed by the app's Team ID
   (`codesign -R`), or a pinned SHA-256 of the upstream binary.
4. **Fail closed, optionally.** The kill switch is a pf anchor under `com.apple/*`; no global flush, the main ruleset untouched,
   `pfctl -E`/`-X` by reference. It is refused (and the tunnel not opened) if the system would not evaluate the anchor.
5. **Recovery is a record, not a memory.** A snapshot of DNS/IPv6, authenticated with the vault's Keychain key and written
   atomically before any change, is reconciled at launch and after every tunnel; only what is still the tunnel's is put back.
6. **The lifecycle is a state machine with an intent.** `reconnecting` and `lost` are states; "connected" is only said after the
   proofs; Auto Reconnect uses the same supervisor (no new password in the background), doubling delays and a limit.

## Consequences

- The residual risk of the prompt model: a program running as the user can ask for the same prompt and run the same supervisor
  with an allow-listed configuration of its own (a VPN to a server of its choice) if the person types the password. A privileged
  helper bound to the app's signature (`SMAppService` + XPC with a code requirement) removes it, and needs a Developer ID.
- The kill switch is not persistent: it is lifted when the app goes or the Mac restarts, so the Mac is never left without
  internet; it does not cover the time before connecting, and with a split, root's sockets are let through.
- A Mac whose engine is a download rather than the bundled one must match a pinned hash; only arm64 is pinned today.

## Alternatives considered

- **A LaunchDaemon installed once.** Better binding, and a persistent block; needs Developer ID, an installer and a removal story.
- **`pf` rules in the main ruleset.** Would affect other apps' rules and survive less well; rejected.
- **A config validator in Swift.** The user's side can be bypassed; the check has to be where root runs.
