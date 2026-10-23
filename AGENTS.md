# HDR Lab Light: agent quick start

Academic/research Android app. Read this first, then [DEVELOPMENT](docs/DEVELOPMENT.md) for setup and [ARCHITECTURE](docs/ARCHITECTURE.md) before touching playback, layout, diagnostics or surveys. Detailed lessons live there to keep this entry point small.

## Work and verify

- Start at the repository root with `git status --short` and the relevant diff; preserve unrelated changes. Keep scope narrow. Do not push, tag or publish unless requested. Prefer GitHub Actions for shared compilation/releases.
- Windows prerequisites: JDK 17, Android Platform 36, Build-Tools 36.0.0. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1`; add `-Release` for build/manifest/dependency changes and `-Patterns` for asset changes. See DEVELOPMENT for direct commands. Windows Store Python aliases can be nonfunctional; use `-PythonExecutable` for a real runtime when needed.
- Inspect the exact final diff and run unit tests before claiming a fix. Release compilation is required for build/manifest changes. Report checks actually run and limits; Robolectric cannot verify physical HDR.
- Use `rg`, targeted reads and compact output. Avoid speculative abstractions, unused helpers and duplicated sources of truth. Simplify without removing existing functionality.
- Consider rotation, backgrounding, multi-window, detached views, offline failures, OLED/LCD differences and vendor processing. Compare results with the request; report assumptions, low confidence and likely regressions/failure modes.
- Record new technical lessons immediately here or in the linked guides. Keep this entry point concise.

## Non-negotiable constraints

- Installed package `dev.hdrlab.light`; Java namespace `dev.hdrlab`. FileProvider uses `${applicationId}.files` / `getPackageName() + ".files"` to preserve coexistence with the full app.
- Preserve signing continuity. Current `app/hdrlab-release.keystore` and hardcoded credentials are exposed in Git; this remains unresolved. Do not copy credentials into new documentation, silently replace/delete the key or use machine debug signing for releases. Key changes need a migration plan.
- Keep `SurfaceView` (`surface_type="surface_view"`), `SeekParameters.EXACT`, immersive transient system bars, 3-second control dismissal and at least 76 dp bottom HUD clearance. SurfaceView does not prove absence of vendor processing.
- Preserve fullscreen brightness boost; restore the previous window override on exit, pause, stop and Back. Cancel countdown/HUD callbacks on stop, manual mode changes and fullscreen exit. Known lifecycle/timer gaps remain: see ARCHITECTURE.
- Preserve responsive two-card sizing, widget ordering, generated RadioButton IDs, `RadioGroup.check(id)` and fallback checked-child traversal documented in ARCHITECTURE.
- Refresh extracted assets after app updates; do not trust only file existence. Preserve PQ reference codes and decoded pattern values.
- English source, IDs, comments and log tags. Every UI string uses resources: English `values/strings.xml`, European Portuguese `values-pt/strings.xml`; native Android locale selection, no custom language switch.
- INTERNET exclusively supports optional user-triggered reports, asynchronously and fail-safe. No unsolicited network work, analytics, ads, crash beacons, accounts or persistent identifiers. Normal testing remains offline.
- Discover brands/models dynamically, deduplicate names and retain generic ROM fallbacks. OS properties are hints. Declared luminance is not measured luminance.
- Keep APK below 2,000,000 bytes with R8 and resource shrinking. Do not compromise pattern integrity to save bytes.

## Code and docs map

- `app/src/main/java/dev/hdrlab/`: `MainActivity.java` (UI/player/countdown/survey/extraction), `Diagnostics.java` (device/display/ROM), `ReportSubmitter.java` (payload/HTTP).
- `app/src/test/java/dev/hdrlab/LayoutTest.java`: Robolectric. `tools/`: validation/pattern generation. `.github/workflows/`: automation. `app/build.gradle.kts`: identity/version/signing/dependencies.
- README is an accessible user guide. RELEASE_NOTES contains only the current release and feeds Actions; prepend history to CHANGELOG. A requested release bumps Gradle version and both docs, then uses an annotated `vX.Y.Z` tag; APK `HDR-Lab-Light-vX.Y.Z.apk`.
