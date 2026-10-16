# Contributing to Vazie VPN

Thank you for taking the time. Vazie VPN is maintained by one person, so small, focused contributions are the
easiest to review.

## Before you start

- **Security problems** go privately to the maintainer — see [SECURITY.md](SECURITY.md), never a public issue.
- **Bugs:** open an issue with the bug template. Say which app version and Android version you use, and how to
  reproduce the problem.
- **Ideas and larger changes:** open an issue first and describe what you want to change and why, before writing
  code. Some directions are out of scope on purpose: built-in servers, accounts, ads, analytics or tracking of any
  kind.

**Never post a real configuration, `vless://` link or subscription link** in an issue, a log or a screenshot. Use
made-up values: `example.com`, `192.0.2.1`, the UUID `00000000-0000-4000-8000-000000000001`.

## Development setup

See [Build from source](README.md#build-from-source). In short: JDK 21, the Android SDK, and a `local.properties`
with `sdk.dir`, `vazie.api.url` and `vazie.site.url` (any `https` addresses).

```bash
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

## Pull requests

- One change per pull request, with a short description of what and why.
- Keep the existing style: Kotlin, Jetpack Compose, the modules and their boundaries as they are. Do not add a
  dependency without explaining why it is needed; no analytics, crash reporting or ad SDKs will be accepted.
- Add or update tests for behaviour you change. `./gradlew check` must pass.
- Every user-visible string goes into both `values/strings.xml` (English) and `values-ru/strings.xml` (Russian).
- Do not bump `vazieVersionName` or `vazieVersionCode`; releases are cut by the maintainer.

## Release signing (maintainer only)

Release APKs are signed with a key that is never in this repository. Locally it comes from
`signKeystore/key.properties` and the keystore it names; in CI, from the `release` environment's secrets. A release
build with no signing configured is produced unsigned; one with incomplete signing settings fails.

## License of contributions

Contributions are accepted under the license of this repository (see [LICENSE](LICENSE)). By opening a pull request
you confirm that you wrote the change, or have the right to submit it under that license.
