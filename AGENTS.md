# HDR Lab Light — Agent Instructions & Working Preferences

> **Branch Context: `simple` (`HDR Lab Light`)**  
> This file defines the architecture, technical constraints, lessons learned, and workflows for all AI coding agents working on **HDR Lab Light** (`dev.hdrlab.light`).

---

## 1. App Identity & Coexistence

* **Dedicated Package Identity:** `dev.hdrlab.light` in `app/build.gradle.kts`.
* **Side-by-Side Installation:** Allows users and developers to install both HDR Lab (the full test bench, `dev.hdrlab`) and HDR Lab Light (`dev.hdrlab.light`) simultaneously on the same Android device without package collision or `INSTALL_FAILED_VERSION_DOWNGRADE` errors.
* **Dynamic FileProvider Authority:** Manifest uses `${applicationId}.files` and Java code uses `getPackageName() + ".files"` to dynamically resolve `dev.hdrlab.light.files`, preventing `INSTALL_FAILED_CONFLICTING_PROVIDER`.

---

## 2. Primary Build & Release Workflow

* **Prefer GitHub Actions for Compilation & Releases:**
  - Always prefer compiling and publishing releases via GitHub Actions (`.github/workflows/release.yml`).
  - To release a new version: Bump version in `app/build.gradle.kts`, update `RELEASE_NOTES.md` and `CHANGELOG.md`, commit, tag (`git tag -a vX.Y.Z -m "..."`), and push the tag (`git push origin vX.Y.Z`).
  - Output APK is formatted as `HDR-Lab-Light-vX.Y.Z.apk`.
* **Local Build Toolchain (Offline Testing):**
  - JDK 17: `%LOCALAPPDATA%\Programs\jdk-17\jdk-17.0.20.1+1` (`JAVA_HOME`)
  - Android SDK: `%LOCALAPPDATA%\Android\Sdk` (`ANDROID_HOME`, `ANDROID_SDK_ROOT`) with Platform 36 and Build-Tools 36.0.0.
  - Fast unit tests: `.\gradlew.bat testDebugUnitTest`
  - Local release test: `.\gradlew.bat assembleRelease`

---

## 3. Critical Android Lessons Learned & Hard Constraints

### A. APK Signing Key Continuity (CRITICAL)
* All release builds MUST use the dedicated project release keystore:
  - Path: `app/hdrlab-release.keystore`
  - Alias: `hdrlab`
  - Password: `hdrlab123`
* Never rely on default machine-specific `debug.keystore`.

### B. Display Pipeline & SurfaceView Integrity
* **Zero-Shader SurfaceView:** Playback must always use `SurfaceView` (`surface_type="surface_view"` in `player.xml`), never `TextureView`. `SurfaceView` bypasses application shaders and connects directly to the Android Hardware Composer (`HWC`), preserving unaltered HDR presentation and dynamic metadata.
* **Exact Seeking:** Enforce `player.setSeekParameters(SeekParameters.EXACT)` so transitions between SDR and HDR land on the exact matching frame without long-GOP keyframe drift.

### C. Tablet Landscape Side-by-Side Layout
* **Adaptive Cards Container:** An intermediate `LinearLayout` (`cardsContainer`) inside `cardsLayout` holds Card 1 (*Capacidades do Teu Ecrã* / *Screen Capabilities*) and Card 2 (*Teste Rápido: SDR vs HDR* / *Quick Test: SDR vs HDR*).
* **Landscape Threshold:** Check `boolean wide = widthDp >= 720 || (widthDp >= 580 && isLandscape);`.
* **Matched Heights & Baseline Alignment:**
  - In wide/landscape mode: `cardsContainer.setOrientation(HORIZONTAL)`, `cardsContainer.setBaselineAligned(false)`, and child cards use `LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)`.
  - A vertical flexible spacer (`View` with `height=0`, `weight=1f`) above `btnDetailedReport` in Card 1 aligns both cards' primary action buttons along an identical bottom baseline.
  - In portrait mode: `cardsContainer.setOrientation(VERTICAL)` and child cards collapse cleanly to `(-1, -2)` without layout jitter.

### D. Window Brightness Override & Lifecycle Restoration
* **Fullscreen Boost:** Setting `screenBrightness = 1.0f` on fullscreen entry ensures the display panel operates at maximum drive for HDR near-black evaluation.
* **Mandatory Restoration:** Always restore original brightness (`BRIGHTNESS_OVERRIDE_NONE` / `-1.0f`) on fullscreen exit, `onPause()`, `onStop()`, and `onBackPressed()`. Failing to restore traps the device at 100% brightness.

### E. Immersive Mode & Floating Controls Clearance
* **Immersive Navigation:** Fullscreen playback must use `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` and hide system bars.
* **3-Second Auto-Dismiss:** Tap-to-reveal player controls must auto-hide after 3 seconds of user inactivity.
* **Bottom Control Clearance:** Floating HUD overlays placed above or inside `PlayerView` must enforce `>= 76dp` `bottomMargin` to avoid intercepting navigation gestures or touch targets.

### F. Automated Transitions & Handler Cleanup
* Always cancel active `Handler` / `Runnable` callbacks (`testCountdownTicker`, `hideFullscreenHud`) in `onStop()`, when switching modes manually, or when exiting fullscreen.

### G. TextView Layout Ordering (setSingleLine vs setMinHeight)
* Always set `setSingleLine(true)` before `setMinHeight(...)` and `setMinimumWidth(...)`, and configure custom padding after `setBackground(...)` to prevent drawable attributes from resetting view padding.

---

## 4. Internationalization (i18n)

* **English Codebase Standard:** All Java/Kotlin source code, XML layouts, view IDs, log tags, and comments must strictly be written in standard International English.
* **Zero Hardcoded UI Strings:** Never embed user-visible string literals directly in Java code or layout XML attributes. Every string must resolve via `@string/...` or `R.string....`.
* **Resource Separation Catalogs:**
  - Base / Default Catalog (`res/values/strings.xml`): Primary reference in International English. Serves as the fallback for all non-Portuguese locales worldwide.
  - Portuguese Catalog (`res/values-pt/strings.xml`): European Portuguese (`pt-PT`) localization for Portuguese device locales.
* **Automatic System Locale Detection:** Localization is handled automatically via Android's native resource qualifier resolution (`values-pt`) without custom runtime language switching logic.

---

## 5. Documentation Architecture

1. **`README.md` (Accessible User Guide):**
   - Written in clear, accessible, jargon-free English for everyday smartphone/tablet users.
   - Explains the two-card interface, how to spot OLED true black vs LCD glow, and how to read the 6 near-black steps.
2. **`RELEASE_NOTES.md` (Strict Current-Only Rule):**
   - Only contains changes for the current release (read directly by GitHub Actions for release body). Never accumulate previous versions here.
3. **`CHANGELOG.md`:**
   - Multi-version historical changelog. Prepend new version summaries at the top.

---

## 6. Philosophy & Code Quality (YAGNI)

* **No Bloat:** 100% offline with zero network permissions in `AndroidManifest.xml`.
* **Fast & Lightweight:** APK size stays under 2 MB with R8 optimization and resource shrinking.

---

## 7. AI Agent Operational Standards: Token Efficiency & Autonomous Quality

All AI coding agents operating on this repository must enforce the following standards:

### A. Token Reduction & Context Economy
* **Targeted Operations:** Avoid reading entire massive files when targeted line ranges, grep searches, or symbol lookups suffice.
* **Compact Command Output:** Filter or limit long command outputs (e.g. `git log -n 5`, selective test runs) to prevent polluting the context window with thousands of redundant tokens.
* **High-Density Communication:** Eliminate conversational fluff, repetitive throat-clearing, and verbose summaries. Provide direct, structured, information-dense updates to the user.
* **YAGNI Pass:** Do not introduce speculative abstractions, unused helper utilities, or boilerplate layers that consume tokens and maintenance overhead.

### B. Agent Quality & Rigorous Self-Verification
* **Proactive Continuous Learning:** Always update `AGENTS.md` immediately whenever a new technical lesson, hardware quirk, layout gotcha, or toolchain constraint is discovered. Never let hard-won lessons vanish across sessions.
* **Verification Before Proclamation:** Never claim an implementation or bugfix works without verifying it:
  1. Inspect the exact `git diff`.
  2. Run unit tests (`.\gradlew.bat testDebugUnitTest`).
  3. Validate release compilation (`.\gradlew.bat assembleRelease`) whenever build or manifest configurations change.
* **Failure Mode & Edge Case Anticipation:** Proactively analyze lifecycle events (screen rotation, backgrounding, multi-window, detached views), display hardware limits (OLED vs LCD, custom vendor video enhancers), and gesture clearance before writing code.
