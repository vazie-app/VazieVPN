# ADR 0001: One repository, one build root per toolchain

- Status: Accepted
- Date: 2026-10-19

## Context

Vazie VPN began as an Android app at the root of its repository. A browser extension is now a second client with
the same functions, and it needs a native helper (a third program, in Rust). The three share a contract that must not
drift: how a `vless://` link is read, what the engine is given, and the words on the screens.

## Decision

One repository. **Each toolchain keeps its own build root** and there is no umbrella build system (the same choice
Vazie Keep made, ADR 0002 there):

| Path                    | What it is                                                | Toolchain          |
| ----------------------- | --------------------------------------------------------- | ------------------ |
| `apps/android/`         | The Android app, everything it had at the root before     | Gradle             |
| `apps/browser-extension/` | The Chromium (Manifest V3) extension                    | npm, Vite, Preact  |
| `apps/native-host/`     | The helper the extension starts over native messaging     | Cargo              |
| `apps/macos/`           | The macOS app (SwiftUI, runs Xray itself)                 | SwiftPM            |
| `shared/vless-vectors/` | The corpus both parsers must read the same way            | JSON               |
| `docs/`                 | Decisions and threat models that span clients             | Markdown           |
| `.github/`              | One workflow per area, filtered by path                   | GitHub Actions     |

What is shared is **data, not code**: the vector corpus is run by `SharedVectorCorpusTest.kt` (Android) and
`tests/corpus.test.ts` (extension), so a link either client reads differently fails in CI. The extension's words are
generated from Android's `strings.xml` (`scripts/import-strings.mjs`), so one sentence has one author.

## Consequences

- Open `apps/android` in Android Studio, not the repository root. `./gradlew` runs from `apps/android`.
- CI runs per area (`ci.yml`, `release.yml` for Android; `extension.yml` for the extension and the helper). A change in
  `shared/` runs all of them.
- The Android release procedure (tag `v*`, `release` environment, signing) is unchanged; the workflows work inside
  `apps/android`. Local state that must not be committed (`local.properties`, `signKeystore/`, `.gradle/`) lives
  there too.
- The public repository (`vazie-app/VazieVPN`) holds the whole monorepo, not only the Android tree.

## Alternatives considered

- **A separate repository for the extension.** Rejected: the parser vectors and the strings would need copying, and a
  change to the link format would be two coordinated releases.
- **Leave Android at the root and add the extension beside it.** Rejected: the root would then be Android's
  Gradle build with two foreign directories in it, and "the repository" would still mean "the Android app".
- **Kotlin Multiplatform for the parser.** Rejected for now: the extension would have to run Kotlin in a service
  worker. A shared corpus gives the guarantee that matters (same answers) without a shared runtime.
