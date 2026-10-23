# Third-party notices: the browser extension

Vazie VPN's browser extension is free software under the GPL-3.0-or-later (see the repository's `LICENSE`).
It bundles the following, each under its own licence:

| Component | Licence | Used for |
|---|---|---|
| [Preact](https://preactjs.com) | MIT | the interface |
| Geologica, Martian Mono (fonts) | SIL OFL 1.1 | text and technical values |

Build tools (not shipped): Vite, TypeScript, `@types/chrome`, `@types/node`; `playwright-core` for the real-browser tests.

The Xray engine is **not** part of the extension. The helper downloads it from its own release
(<https://github.com/XTLS/Xray-core>, MPL-2.0) and runs it as a separate program; see `apps/native-host`.
