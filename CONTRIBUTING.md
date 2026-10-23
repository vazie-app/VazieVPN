# Contributing to Vazie VPN

Thank you for taking the time. Vazie VPN is maintained by one person, so small, focused contributions are the
easiest to review.

## Before you start

- **Security problems** go privately to the maintainer — see [SECURITY.md](SECURITY.md), never a public issue.
- **Bugs:** open an issue with the bug template. Say which app (Android or the browser extension), which version, which
  Android or browser version you use, and how to reproduce the problem.
- **Ideas and larger changes:** open an issue first and describe what you want to change and why, before writing
  code. Some directions are out of scope on purpose: built-in servers, accounts, ads, analytics or tracking of any
  kind.

**Never post a real configuration, `vless://` link or subscription link** in an issue, a log or a screenshot. Use
made-up values: `example.com`, `192.0.2.1`, the UUID `00000000-0000-4000-8000-000000000001`.

## Repository layout

One repository, one build root per toolchain ([ADR 0001](docs/adr/0001-monorepo-layout.md)):

| Path | What | Build |
|---|---|---|
| `apps/android` | The Android app | Gradle (run `./gradlew` from this directory) |
| `apps/browser-extension` | The Chromium extension | `npm ci`, `npm run check`, `npm run build` |
| `apps/native-host` | The helper the extension starts | `cargo test`, `cargo build --release` |
| `shared/vless-vectors` | The link corpus both parsers must read the same way | run by both test suites |

A change to the link format or to a parser's reading of it starts in `shared/vless-vectors`: both clients' tests
must pass.

## Development setup: Android

See [Build from source](apps/android/README.md#build-from-source). In short: JDK 21, the Android SDK, and a
`local.properties` in `apps/android` with `sdk.dir`, `vazie.api.url` and `vazie.site.url` (any `https` addresses).

```bash
cd apps/android
./gradlew assembleDebug
./gradlew check
```

`check` runs the unit tests, Android lint and the project's own checks:

| Check | What it guards |
|---|---|
| `checkModuleRules` | Which module may depend on which (for example, only `app` sees the VPN engine) |
| `checkDesignTokens` | No hard-coded colours, sizes or font sizes outside `core/designsystem` |
| `checkLocalization` | Every English string has a Russian one, and the other way round |
| `checkPreviewConvention` | Every Compose component has a preview |

Release-only checks:

```bash
./gradlew :app:assembleRelease :app:checkReleaseGraph :app:checkReleaseArtifact
```

`checkReleaseGraph` fails if an analytics or crash-reporting SDK reaches the release; `checkReleaseArtifact` scans
the built APKs.

## Development setup: browser extension and helper

Node 22.18 or newer and a stable Rust toolchain.

```bash
cd apps/browser-extension
npm ci
npm run check                      # typecheck and tests
node scripts/build.mjs --development    # unpacked build in dist/chromium, with a stable extension id
cd ../native-host && cargo test && cargo build
```

Load `dist/chromium` in `chrome://extensions` (developer mode). The real-browser checks (`tests/e2e`) are described
in [apps/browser-extension/README.md](apps/browser-extension/README.md). Every user-visible string of the extension is
in `ext.en.json` and `ext.ru.json` (the Android app's words are imported by `scripts/import-strings.mjs`);
`npm test` fails if a key is missing in either language.

## Pull requests

- One change per pull request, with a short description of what and why.
- Keep the existing style: Kotlin, Jetpack Compose, the modules and their boundaries as they are. Do not add a
  dependency without explaining why it is needed; no analytics, crash reporting or ad SDKs will be accepted.
- Add or update tests for behaviour you change. `./gradlew check` must pass.
- Every user-visible string goes into both languages: `values/strings.xml` and `values-ru/strings.xml` on Android,
  `ext.en.json` and `ext.ru.json` in the extension.
- Do not bump `vazieVersionName` or `vazieVersionCode`; releases are cut by the maintainer.

## Release signing (maintainer only)

Release APKs are signed with a key that is never in this repository. Locally it comes from
`apps/android/signKeystore/key.properties` and the keystore it names; in CI, from the `release` environment's secrets. A release
build with no signing configured is produced unsigned; one with incomplete signing settings fails.

## License of contributions

Contributions are accepted under the license of this repository (see [LICENSE](LICENSE)). By opening a pull request
you confirm that you wrote the change, or have the right to submit it under that license.
