# Development

## Setup

Use Windows PowerShell, Git, JDK **17**, Android Platform **36** and Build-Tools **36.0.0**. Use the Gradle wrapper; no global Gradle is needed.

Set the SDK in untracked `local.properties` (`sdk.dir`) or SDK environment variables. First dependency downloads need internet; `--offline` needs cached dependencies. See [tool discovery and overrides](../tools/README.md#tool-discovery).

## Verification

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1 -Release -Patterns
```

Default: unit tests and debug lint. Add `-Release` for build/manifest/dependency changes; add `-Patterns` for assets/generators. [Tool reference](../tools/README.md) covers requirements, logs and failures. Checks do not regenerate assets or submit reports.

To isolate failures:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat assembleRelease lintRelease
python tools/verify_patterns.py
.\gradlew.bat -q :app:printVersion
git diff --check
```

Inspect the final diff; report actual results and blockers. Test report: `app/build/reports/tests/testDebugUnitTest/index.html`; lint: `app/build/reports/lint-results-debug.html` (or release equivalent); APK: `app/build/outputs/apk/release/app-release.apk`.

Robolectric covers APIs 31 and 35; compilation targets API 36. It cannot verify physical HDR or real HTTP delivery.

## Code map

Java files below are in `app/src/main/java/dev/hdrlab/`.

| File/location | Purpose |
| --- | --- |
| `MainActivity.java` | UI, playback, fullscreen/countdown, extraction, survey |
| `OledTestView.java` | Canvas patterns and animation |
| `Diagnostics.java` | Device/ROM discovery, display declarations, diagnostic JSON |
| `ReportSubmitter.java` | Report payload and asynchronous HTTP |
| `app/src/main/res/layout/player.xml` | SurfaceView |
| `app/src/main/res/values*/strings.xml` | Localisation |
| `app/src/main/assets/patterns/` | Reference videos |
| `app/src/test/java/dev/hdrlab/LayoutTest.java` | Regression tests |
| `app/src/test/java/dev/hdrlab/OledRampTest.java` | Ramp pixels and animation attachment lifecycle |
| `app/build.gradle.kts` | Version, identity, SDK, signing, dependencies, shrinking |
| `tools/`, `.github/workflows/` | Validation/generation, CI/releases |

Search with `rg -n '<symbol>' app/src`. Read the relevant [runtime constraints](ARCHITECTURE.md) before changing behaviour.

## Device checks

For playback/UI changes, check rotation, wide/narrow layouts, multi-window, larger fonts, both locales and detached views. Exercise pause/stop/Back/fullscreen exit, brightness restoration, HUD dismissal/gesture clearance, manual switches during countdown, player errors and offline reports.

Use OLED and LCD where possible. Record device, OS and settings. Separate visual observations from measurements; precise luminance requires external equipment.

## Signing and releases

Prefer GitHub Actions for shared builds/releases. Release only when requested: update the Gradle version, current-only `RELEASE_NOTES.md` and historical `CHANGELOG.md`, then use an annotated `vX.Y.Z` tag. APK name: `HDR-Lab-Light-vX.Y.Z.apk`. Reports use `BuildConfig.VERSION_NAME`; release tooling uses `:app:printVersion`.

CI checks PRs, `main`/`beta` pushes and manual runs: unit tests, release lint/build and all nine patterns. Reports are uploaded even after failure. Release workflow repeats these checks and consumes `RELEASE_NOTES.md`. Manual release requires an existing tag matching `v` plus the checked-out Gradle version; older tags without `printVersion` cannot pass.

The tracked `app/hdrlab-release.keystore` and credentials are exposed. Preserve signing continuity; do not copy passwords into docs/logs, silently delete/rotate the key or use machine debug signing. Removing a file cannot revoke past copies. Secure storage/key migration remains separate work.
