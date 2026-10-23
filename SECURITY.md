# Security policy

## Reporting a vulnerability

Please report a vulnerability **privately**, in one of two ways:

- GitHub: **Security → Report a vulnerability** in this repository (a private advisory only the maintainer sees);
- email: **support@vazie.app**, with "Security" in the subject.

**Do not open a public issue, discussion or pull request for a security problem**, and do not publish details
before a fix is released.

Never include a real configuration, a `vless://` link or a subscription link: they are credentials. Describe the
problem with a made-up configuration (`example.com`, documentation addresses such as `192.0.2.1`) instead.

What helps:

- the app version (Settings → About) and the Android version;
- what an attacker can do, and what they need first (physical access, another app on the phone, a network position);
- steps to reproduce, or a proof of concept that uses no real server.

## What happens next

- An acknowledgement within 7 days.
- An assessment and, for a confirmed issue, a plan for the fix.
- The fix in a release, and credit in its notes unless you prefer to stay anonymous.

There is no bug bounty.

## Scope

In scope:

- the Android app in this repository and the APKs published in its releases;
- the browser extension and its helper (`apps/browser-extension`, `apps/native-host`) and what is published from them;
- how the app stores configurations, and what it sends over the network.

Out of scope:

- Vazie's servers and website: report what you notice, but do not scan, load or attack them;
- VPN servers that users connect to — they belong to their owners;
- attacks that need an unlocked, rooted or compromised phone, unless the app makes them easier;
- vulnerabilities in Xray-core or Android itself: please report those upstream as well.

## Supported versions

Only the latest release receives security fixes.

## How the project protects users

- Configurations are encrypted with a key held in the Android Keystore; Android backup is disabled for the app. In the browser extension they are encrypted with a non-extractable key in the browser profile (see [the threat model](docs/security/EXTENSION_THREAT_MODEL.md)).
- Secrets are wrapped in a type that cannot be logged or serialised by accident.
- The release build fails if an analytics, crash-reporting or tracking library reaches it, and every release APK is
  scanned for credential-shaped strings, debug tools and unexpected hosts.
- The VPN engine (libXray) is downloaded from a pinned upstream release and verified by SHA-256 before it is built in.
- No signing keys or secrets are kept in this repository.
