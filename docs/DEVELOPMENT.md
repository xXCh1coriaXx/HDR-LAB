# Development guide

Start with [AGENTS.md](../AGENTS.md) for working rules and [ARCHITECTURE.md](ARCHITECTURE.md) for detailed constraints and unresolved runtime/security issues. This cleanup improves development workflows; it does not resolve those deferred issues.

## Prerequisites

- Windows PowerShell, Git and JDK **17**. The known workstation JDK is `%LOCALAPPDATA%\Programs\jdk-17\jdk-17.0.20.1+1`; another JDK 17 is fine.
- Android SDK Platform **36**, Build-Tools **36.0.0**. Typical SDK location: `%LOCALAPPDATA%\Android\Sdk`. Configure untracked `local.properties` (`sdk.dir`) or consistent `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
- Use the repository Gradle wrapper; no global Gradle needed. First dependency downloads require network access. `--offline` requires cached artifacts; normal app use remains offline.
- Optional pattern verification needs Python 3, ffmpeg and ffprobe. Generation also needs the FFmpeg encoders used in `tools/generate_patterns.py`.

## Verify from the repository root

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1 -Release -Patterns
```

Default checks run unit tests and debug lint. `-Release` adds release assembly, release lint and reports the release APK size (no hard 2 MB limit); `-Patterns` checks packaged video signaling and decoded references. Failures return a nonzero exit code. No patterns are regenerated or reports submitted. Logs are under `app/build/validation`; reports under `app/build/reports`. See [tool reference](../tools/README.md) for toolchain discovery and optional path overrides.

Windows may resolve `python` to a nonfunctional Microsoft Store alias. Use `-PythonExecutable 'C:\path\to\python.exe'` with a real Python 3 runtime; the script checks it before Gradle when patterns are requested.

Direct commands for isolating failures:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat assembleRelease lintRelease
python tools/verify_patterns.py
.\gradlew.bat -q :app:printVersion
git diff --check
```

Run unit tests for code changes and release compilation for build/manifest/dependency changes. Verify patterns when changing assets or generators. Inspect the exact final diff; report checks actually run and blockers without treating unavailable checks as passes. Keep unrelated work intact.

Test report: `app/build/reports/tests/testDebugUnitTest/index.html`. Lint: `app/build/reports/lint-results-debug.html` (or release equivalent). Local APK: `app/build/outputs/apk/release/app-release.apk`. Robolectric uses API 35 simulation while compilation targets API 36; it cannot certify hardware HDR or real HTTP behavior.

## Code map

| Location | Responsibility |
| --- | --- |
| `app/src/main/java/dev/hdrlab/MainActivity.java` | Two-card UI, OLED suite card, playback, fullscreen/countdown, asset extraction, survey |
| `app/src/main/java/dev/hdrlab/OledTestView.java` | Canvas OLED patterns (smear, near-black ladder, DSE/uniformity, APL windows) |
| `app/src/main/java/dev/hdrlab/Diagnostics.java` | Device names, display declarations, refresh modes, ROM detection, JSON |
| `app/src/main/java/dev/hdrlab/ReportSubmitter.java` | Payload serialization and asynchronous HTTP |
| `app/src/main/res/layout/player.xml` | SurfaceView configuration |
| `app/src/main/res/values*/strings.xml` | English and Portuguese UI catalogs |
| `app/src/main/assets/patterns/` | Reference MP4s |
| `app/src/test/java/dev/hdrlab/LayoutTest.java` | Robolectric regression checks |
| `tools/` | Verification and pattern generation |
| `app/build.gradle.kts` | Identity, version, SDK, signing, dependencies, shrinking |
| `.github/workflows/` | CI and releases |

Search symbols using `rg -n '<symbol>' app/src` and read targeted ranges. `BuildConfig.VERSION_NAME` supplies the report version; `printVersion` exposes the Gradle version for release tooling. Avoid duplicating it in source/tests or adding speculative abstractions.

## Physical-device verification

For playback/UI changes check portrait/landscape, wide/narrow layouts, multi-window, larger fonts and both locales. Exercise pause/stop/Back/fullscreen exit, brightness restoration, three-second HUD dismissal, gesture clearance, manual switches during countdown, player failures and offline submissions. Record device/OS/settings and distinguish observations from measurements; use OLED/LCD hardware where possible.

A successful build, PQ label, SurfaceView or declared nits does not prove correct physical HDR. Emulators cannot certify panel output or vendor enhancement behavior. Precise luminance requires external measurement. Pending pause/timer/report issues in ARCHITECTURE are not passing checks.

## Signing and releases

Prefer GitHub Actions for shared compilation/publication. Local work does not imply pushing, tagging or releasing. A requested release updates Gradle version, current-only RELEASE_NOTES and historical CHANGELOG, then uses an annotated `vX.Y.Z` tag; release APK `HDR-Lab-Light-vX.Y.Z.apk`. README remains an accessible user guide.

`ci.yml` verifies pull requests, pushes to `main`/`beta` and manual runs: unit tests, release lint/build, all nine patterns and release APK output. Test/lint reports are uploaded as artifacts even after failure. `release.yml` runs the same checks before publishing. Manual release dispatch requires an existing tag; it checks out that tag and requires its name to equal `v` plus `:app:printVersion`. Use tags containing the 0.2.0 tooling or newer; historical tags without that task cannot pass the updated workflow.

The release key `app/hdrlab-release.keystore` and build credentials are already exposed in Git. This is unresolved. Do not copy passwords into new docs/logs, silently delete/rotate the key or substitute machine-specific debug signing. Installed apps depend on signing continuity; secure storage/migration is separate work.

Record new toolchain/hardware lessons in these guides and update AGENTS when relevant. Preserve offline operation and optional user-triggered reports; no runtime telemetry.
