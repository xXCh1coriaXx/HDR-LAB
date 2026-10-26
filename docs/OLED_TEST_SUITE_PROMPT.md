# OLED Tester - Comprehensive OLED Screen Quality Test Suite Prompt

Copy and paste the prompt below directly into your AI coding agent (e.g., Claude, Cursor, Antigravity, Copilot Workspace, Codex, etc.) to implement the OLED testing suite.

---

```markdown
You are an expert Android Display and Media Systems Engineer. Your task is to expand the "OLED Tester" Android app (package `dev.hdrlab.light`, Java namespace `dev.hdrlab`) by building a specialized, research-grade **OLED Screen Quality & Defect Testing Suite**.

## Core Problem Context
OLED/AMOLED mobile displays exhibit unique hardware and driving characteristics that conventional display testers fail to isolate:
1. **Black-to-Dark Pixel Response Delay (Purple Smearing / OLED Ghosting):** At low brightness, subpixels turning on from true off (#000000 / 0 V) to near-black grays (#080808 - #181818) have slower rise times than transition between illuminated states, causing noticeable purple/blue trails during motion.
2. **Near-Black Uniformity & Banding (Dirty Screen Effect - DSE):** Low-voltage driving non-uniformity produces static vertical lines and mottled patterns, typically most visible at 1% to 15% stimulus levels.
3. **Black Crush vs. Floating Black:** Hardware decoder tone mapping or aggressive panel gamma can either crush fine shadow detail below 2% into pure black or lift true black into an active glowing gray.
4. **Auto Brightness Limiter (ABL) & Thermal Throttling:** Small Average Picture Level (APL) windows achieve high peak nits, but large full-screen windows get throttled by display power/thermal management.

---

## Required Test Suites & Features

### 1. Black-to-Dark Motion & Pixel Rise Time Suite (OLED Smear Test)
- **Interactive Moving Canvas View:**
  - Background: Pure true black (`#000000` — ensuring OLED subpixels are completely powered down).
  - Foreground test targets: Moving blocks/bars in variable near-black tones (`#050505`, `#080808`, `#0F0F0F`, `#181818`, `#202020`).
  - Motion parameters: Configurable horizontal and vertical scrolling speeds (e.g. 100 px/s to 2000 px/s).
  - High refresh rate support: Synchronized to display refresh rate (60Hz, 90Hz, 120Hz, 144Hz) using Android `Choreographer` frame callbacks. Display current FPS and dropped frame count so motion jitter is not mistaken for display smearing.
  - In-test brightness slider: Allows testing smearing behavior across min-brightness up to full-brightness.

### 2. Near-Black Step Ladder & Black Crush Suite
- **Granular Low-Stimulus Steps:**
  - 0.5%, 1.0%, 2.0%, 3.0%, 4.0%, 5.0%, 7.5%, and 10.0% stimulus levels displayed against pure black.
  - Numbered or stepped reference patches to determine the exact threshold where shadow detail becomes discernible from pure black.
- **Dual-Pipeline Execution:**
  - **Pipeline A (Native Canvas / OS UI):** Renders directly via Android `View`/`Canvas` to evaluate OS `SurfaceFlinger` compositing, baseline display gamma, and color management.
  - **Pipeline B (Media3 SurfaceView Video Playback):** Plays pre-rendered 10-bit PQ/HLG and 8-bit SDR test pattern videos to evaluate hardware media decoder behavior, vendor video enhancement engines, and dynamic tone mapping.

### 3. Dirty Screen Effect (DSE) & Vertical Banding Suite
- **Uniform Gray Slides:** Full-field uniform backgrounds for 1%, 2%, 3%, 5%, 10%, and 15% stimulus levels.
- **Optokinetic Motion Trigger (Horizontal Pan):**
  - Displays a slow, smooth continuous horizontal scroll across faint vertical patterns and low-stimulus grays.
  - Human visual perception detects static panel non-uniformity (vertical banding) significantly better when the background is in smooth motion (exploiting optokinetic nystagmus).

### 4. Floating Black & ABL / APL Stress Suite
- **Variable Window Sizes (APL Test):**
  - Pure black background with centered white/HDR peak boxes of exact display area percentages: 1%, 2%, 5%, 10%, 25%, 50%, and 100%.
  - Allows inspecting:
    1. **Floating black:** Does the black surround stay completely dark (subpixels off), or does the presence of a bright window induce stray panel glow / elevated black floor?
    2. **ABL brightness step-down:** Visual observation of panel dimming as APL increases from 1% to 100%.

### 5. Diagnostics & Qualitative Community Reporting
- Extend the diagnostic data collection in `Diagnostics.java` and `ReportSubmitter.java`:
  - Detect and log physical refresh rates and supported modes (`Display.getMode()`, `Display.getSupportedModes()`).
  - Add survey evaluation fields:
    - *Black crush threshold:* (e.g., crushes below 1%, 2%, 3%, or all steps visible).
    - *OLED purple smear intensity:* (None, Mild, Severe).
    - *Near-black uniformity / DSE:* (Clean & Uniform, Visible Banding, Severe Mottling/DSE).
    - *Floating black under HDR/peak load:* (True Black / Pixels Off vs. Visible Glow).

---

## Architectural & Technical Constraints

1. **Package Identity & Continuity:**
   - Package ID must remain `dev.hdrlab.light`, Java package `dev.hdrlab`.
   - FileProvider authority must remain `${applicationId}.files`.
   - Preserve existing keystore and signing continuity.
2. **App Sizing & Assets:**
   - The app does not need to stay under 2MB. High-quality 10-bit test pattern video assets and rich test graphics are permitted.
3. **Video Playback Architecture:**
   - Video tests must continue to use `androidx.media3:media3-exoplayer` with `SurfaceView` (`surface_type="surface_view"`) and `SeekParameters.EXACT`.
   - Never use `TextureView` or application-level shaders for video playback (which interfere with native HDR passthrough and hardware overlays).
4. **Window & Display Management:**
   - Maintain fullscreen brightness override handling (`screenBrightness = 1.0f`), restoring the previous override on exit, `onPause()`, `onStop()`, and Back.
   - Use `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` for immersive testing.
5. **Internationalization (i18n):**
   - All UI strings, options, and descriptions must be defined in resources:
     - English: `res/values/strings.xml`
     - European Portuguese: `res/values-pt/strings.xml` and `res/values-pt-rPT/strings.xml`
6. **Privacy & Offline Safety:**
   - The app must work 100% offline. `INTERNET` permission is used exclusively for optional, user-initiated community reports. No background telemetry, crash beacons, or unsolicited tracking.
7. **Verification & Tests:**
   - Maintain and update unit tests in `app/src/test/java/dev/hdrlab/LayoutTest.java`.
   - Validate builds via `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1`.
```
