# HDR Lab — Agent Instructions & Working Preferences

> **Branch Context: `pro` (`HDR Lab — Full Test Bench / Pro Edition`)**  
> This file defines the architecture, technical constraints, lessons learned, and workflows for all AI coding agents working on the core **HDR Lab** engineering test bench (`dev.hdrlab`).

---

## 1. Primary Build & Release Workflow

* **Prefer GitHub Actions for Compilation & Releases:**
  - Always prefer compiling and publishing releases via GitHub Actions (`.github/workflows/release.yml`).
  - Cloud runners build, test, package, and publish in ~2 minutes with zero local CPU/memory overhead.
  - To release a new version: Update version in `app/build.gradle.kts`, update `RELEASE_NOTES.md` and `CHANGELOG.md`, commit, tag (`git tag -a vX.Y.Z -m "..."`), and push the tag (`git push origin vX.Y.Z`). GitHub Actions handles the rest.
* **Local Build Toolchain (Offline / Fast Unit Testing):**
  - The local machine has a fully functional headless Android build toolchain installed:
    - JDK 17: `%LOCALAPPDATA%\Programs\jdk-17\jdk-17.0.20.1+1` (`JAVA_HOME`)
    - Android SDK: `%LOCALAPPDATA%\Android\Sdk` (`ANDROID_HOME`, `ANDROID_SDK_ROOT`) with Platform 36 and Build-Tools 36.0.0.
  - Use `.\gradlew.bat testDebugUnitTest` for fast local unit testing before pushing changes.
  - Use `.\gradlew.bat assembleRelease` for local offline builds when needed.

---

## 2. Critical Android Lessons Learned & Hard Constraints

### A. APK Signing Key Continuity (CRITICAL)
* **The Rule:** Android enforces strict signing certificate identity across package updates. If an update is signed with a different key, Android blocks it with:
  `"App not installed as package conflicts with an existing package."`
* **The Solution:**
  - Never rely on default machine-specific `debug.keystore` for release builds.
  - All release builds (local and GitHub Actions) **MUST** use the dedicated project release keystore:
    - Path: `app/hdrlab-release.keystore`
    - Alias: `hdrlab`
    - Store/Key Password: `hdrlab123`
  - This ensures users can update in-place seamlessly without ever needing to uninstall.

### B. Display Pipeline & SurfaceView Integrity
* **Zero-Shader SurfaceView:** Playback must always use `SurfaceView`, never `TextureView`. `SurfaceView` bypasses application shaders and connects directly to the Android Hardware Composer (`HWC`), preserving unaltered HDR presentation and dynamic metadata.
* **Exact Seeking:** Seeking must enforce `player.setSeekParameters(SeekParameters.EXACT)`. Standard seek rounds to keyframes and causes frame drift between Original and SDR comparisons.
* **Audio-Stripped Tone Mapping:** In `MainActivity.beginExport()`, tone mapping invocations MUST keep `.setRemoveAudio(true)` on `EditedMediaItem`. User test clips often contain exotic multichannel audio (TrueHD, DTS-HD, 7.1 LPCM) that cause Media3 Transformer audio encoders to fail even when video tone mapping is fully supported.

### C. Media3 PlayerView Bottom Control Clearance
* **Timeline Clearance:** ExoPlayer's `DefaultTimeBar` and bottom controls require ~72dp clearance. Floating overlay HUDs placed above or inside `PlayerView` must enforce `>= 76dp` `bottomMargin` to avoid intercepting touches or obscuring the timeline scrubber and settings gear.

### D. Media3 ColorInfo Humanization
* **Clean Format Inspection:** Never output raw `ColorInfo.toString()`; format `colorSpace`, `colorRange`, and `colorTransfer` into explicit readable tags (`BT.2020 · Limited · HLG / PQ`) to prevent multiline layout bloat and unreadable debug dumps on small screens.

### E. TextView Layout Ordering (setSingleLine vs setMinHeight)
* **SingleLine Overwrites MinHeight:** In Android AOSP `TextView`, calling `setSingleLine(true)` internally invokes `setLines(1)`, resetting `mMinMode` to `LINES` and invalidating any prior `setMinHeight(pixels)` (causing `getMinHeight()` to return `-1`).
* **Correct Call Order:** Always set `setSingleLine(true)` before `setMinHeight(...)` and `setMinimumWidth(...)`, and configure custom padding after `setBackground(...)` to prevent drawable attributes from resetting view padding.

### F. Dark-Room Mode, Frame Stepping & Window Brightness
* **Single-Frame Stepping:** Stepping forward and backward frame-by-frame (◀ / ▶) while paused allows inspecting near-black macroblocking, dithering, and edge glow.
* **Fullscreen Brightness Boost:** Setting `WindowManager.LayoutParams.screenBrightness = 1.0f` ensures the display panel operates at maximum drive during fullscreen evaluation.
* **Mandatory Restoration:** Always restore original system-managed brightness using `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE` (`-1.0f`) on fullscreen exit, `onPause()`, `onStop()`, or `onDestroy()`.

### G. Fullscreen Immersive Sticky Mode & Auto-Dimming HUD
* **Immersive Navigation:** Fullscreen playback must use `WindowInsetsControllerCompat` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` and hide both `systemBars()`.
* **Inactivity Auto-Dismiss:** Tap-to-reveal player controls should automatically hide after 3 seconds of user inactivity so near-black steps and black surround can be inspected without UI interference.

### H. CTA-861.3 / SMPTE ST 2086 HDR Static Metadata Parser
* Decodes mastering display primaries (Rx, Ry, Gx, Gy, Bx, By), white point (Wx, Wy), min/max mastering luminance (nits), MaxCLL, and MaxFALL into structured diagnostic metadata for engineers and color scientists.

---

## 3. Documentation Architecture

Always maintain distinct documentation files:
1. **`README.md` (General / Accessible):**
   - Written in clear, direct, jargon-free English for everyday smartphone/tablet users.
   - Points users to `TECHNICAL_README.md` for technical depth.
2. **`TECHNICAL_README.md` (Engineers & Color Scientists):**
   - Contains exact 10-bit limited-range YUV values (`Y=64`, neutral chroma `Cb/Cr=512`), patch increment matrices, and bright-window nits targets.
   - Documents CTA-861.3 / SMPTE ST 2086 metadata parsing, AOSP Display APIs, and MediaCodec/OpenGL tone-mapping pipeline specifications.
3. **`RELEASE_NOTES.md` vs `CHANGELOG.md` (Strict Current-Only Rule):**
   - `RELEASE_NOTES.md` must only contain the changes for the current release. GitHub Actions reads this file directly for the release body.
   - Historical multi-version release logs belong in `CHANGELOG.md`. Never accumulate previous version entries in `RELEASE_NOTES.md`.

---

## 4. Code Quality & Philosophy (YAGNI)

* **No Bloat:** Do not introduce heavy third-party libraries, databases, analytics, or background services.
* **Offline & Private:** HDR Lab operates 100% offline with zero network permissions in `AndroidManifest.xml`.
* **Preserve Existing Behavior:** Never remove existing functionality merely to simplify implementation. Responsive layouts, rotation handling, and private data retention mechanisms must be preserved.


---

## 5. AI Agent Operational Standards: Token Efficiency & Autonomous Quality

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
