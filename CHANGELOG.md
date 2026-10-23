# HDR Lab — Changelog

## v0.1.6.1 (Survey RadioGroup State Tracking Fix)

- Reliable RadioGroup Selection Capture: Fixed an Android programmatic view quirk where default Option A was not serialized when untouched. Added dynamic view IDs (`View.generateViewId()`), `RadioGroup.check(id)`, and fallback checked state verification in `getCheckedText()`.

---

## v0.1.6 (SDR vs HDR Comparative Community Report & 10-Column Database)

- SDR vs HDR 4-Question Comparison Matrix: Evaluates SDR baseline black levels and shadow step visibility (1% to 6%) alongside HDR10 transition black levels and shadow steps to pinpoint raised blacks and black crush anomalies.
- 10-Column Live Community Database: Upgraded community database schema and Google Apps Script webhook to record 10 structured fields (Timestamp, Device Model, Operating System, HDR Capabilities, Peak Brightness, SDR Black Level, SDR Visible Steps, HDR Black Level, HDR Visible Steps, and User Notes).
- Backward-Compatible Parameter Ingestion: Webhook gracefully accepts both comparative keys (`sdr_black`, `sdr_steps`, `hdr_black`, `hdr_steps`) and legacy aliases (`black_level`, `steps_visible`).
- Automated Custom OS & ROM Detection: Real-time detection of vendor skins (Honor MagicOS, Xiaomi HyperOS/MIUI, Samsung One UI, OnePlus OxygenOS, OPPO ColorOS, Google Pixel UI, etc.).
- Retained Core Engine & Tablet Layout: Preserved zero-shader SurfaceView bypass directly to Android Hardware Composer (HWC), exact-seeking 10-second SDR-to-HDR transition, responsive side-by-side tablet cards, and full bilingual localization (EN / PT-PT).

---

## v0.1.5.1 (Live Community Webhook Integration)

- Production Webhook Endpoint: Activated live Google Apps Script endpoint in `ReportSubmitter.java`, directing submissions to the central community Google Sheet in real time.
- Offline Test Suite: Ensured all unit tests run hermetically with isolated mock endpoints.

---

## v0.1.5 (Community Display Database & Custom OS Detection)

- Community Display Report & Database integration: Introduced an optional, user-prompted survey to submit display calibration evaluations to a crowd-sourced Google Forms / Sheets database (evaluating OLED true black vs. raised black bugs, near-black step visibility, and user notes).
- Automated Custom OS / ROM detection: Automatically identifies custom Android vendor skins and versions (Honor MagicOS, Xiaomi HyperOS/MIUI, Samsung One UI, OnePlus OxygenOS, OPPO ColorOS, Google Pixel UI, etc.) by reading vendor build properties.
- Anonymous, privacy-first telemetry: Collects solely display hardware capabilities, ROM identifiers, and user perceptual ratings without personal data or persistent tracking identifiers.
- Controlled network permission: Added `<uses-permission android:name="android.permission.INTERNET" />` scoped strictly to optional, user-initiated survey form dispatch.
- Retained core HDR Lab Light features: Preserved adaptive side-by-side tablet layout, 10-second SDR-to-HDR near-black step test, zero-shader SurfaceView HWC bypass, and full bilingual i18n (EN / PT-PT).

---

## v0.1.2 (Full Bilingual i18n: EN & PT-PT)

- Full bilingual support (International English & Portuguese pt-PT): All UI strings, diagnostic cards, technical report dialogs, and test HUD labels fully localized.
- Automatic system locale detection: Dynamically selects European Portuguese (`pt-PT`) for Portuguese device locales, defaulting to International English for all other locales.
- International standard English codebase: Complete separation of UI strings into native Android resource catalogs (`res/values/strings.xml` and `res/values-pt/strings.xml`), standardizing underlying code and layout templates in English.
- Retained tablet adaptive side-by-side layout & near-black test: Dynamic two-column card arrangement in wide/landscape viewports with matched heights and aligned action buttons, alongside automated 10-second SDR-to-HDR near-black evaluation.

---

## v0.1.1 (Tablet Adaptive Side-by-Side Layout)

- Adaptive two-card tablet and landscape layout: dynamically positions "Capacidades do Teu Ecrã" and "Teste Rápido: SDR vs HDR" side-by-side in 2 columns on wide screens and tablets in landscape (>= 720dp width or >= 580dp in landscape).
- Equal card height and button alignment: matched card heights with weighted vertical spacers aligning action buttons ("Ver Relatório Técnico Detalhado" and "Iniciar Teste SDR vs HDR") along the same horizontal baseline.
- Scroll-free landscape viewport: fits entire test setup and action controls on-screen simultaneously, preventing the start button from being pushed offscreen or requiring vertical scrolling.
- Fluid responsive reflow: maintains clean 1-column vertical stacking on portrait and narrow devices.

---

## v0.1.0 (HDR Lab Light Initial Release)

- Standalone lightweight package (`dev.hdrlab.light`): Coexists side-by-side with full HDR Lab (`dev.hdrlab`) without package conflicts or version downgrade errors (`INSTALL_FAILED_VERSION_DOWNGRADE`).
- Streamlined two-card architecture ("Capacidades do Teu Ecrã" and "Teste Rápido: SDR vs HDR") tailored for fast 2-minute display evaluation.
- Automated 10-second SDR-to-HDR transition in fullscreen on calibrated 6-patch near-black step pattern with countdown timer and matching-frame switch.
- Auto-boost fullscreen window brightness to 100% (`screenBrightness = 1.0f`) with guaranteed restoration to system brightness on exit.
- Tap-to-reveal floating HUD with 3-second auto-dimming and manual mode toggles.
- Preserved zero-shader SurfaceView bypass directly to Android Hardware Composer (HWC).

---

## v0.3.0.1 (Maintenance & Dark-Room UI Polish)

- Fullscreen HUD timeline clearance: Raised floating HUD baseline (76dp bottom clearance) to completely clear ExoPlayer's DefaultTimeBar scrubber and settings gear, avoiding accidental touch interception.
- HUD horizontal condensing: Compact pill layout with "Original", "Codec", and "GPU" labels, preventing horizontal button clipping and wrapping on narrow portrait displays.
- Test bench button alignment: Standardized "↗ Fullscreen" single-line label matching "◀ Step" / "Step ▶" button heights.
- Clean format inspection string: Human-readable transfer function, color space, and dynamic range tags (`BT.2020 · Limited · HLG / PQ`) replacing raw Media3 ColorInfo dumps.

---

## v0.3.0 (Dark-Room View & Hardware Pipeline Analysis)

- Dark-room in-fullscreen A/B switcher: Toggle between Original, SDR Codec, and SDR GPU comparisons directly in Fullscreen mode using a low-luminance auto-dimming HUD, preventing eye dark-adaptation loss.
- Single-frame stepping: Step forward and backward frame-by-frame (◀ / ▶) while paused on both the test bench and in fullscreen for inspecting near-black macroblocking, dithering, and edge glow.
- Exact-frame seeking: Configured ExoPlayer with `SeekParameters.EXACT` to eliminate sync keyframe rounding when toggling comparisons on long-GOP streams.
- CTA-861.3 / ST 2086 HDR Static Metadata parser: Decodes mastering display primaries (Rx, Ry, Gx, Gy, Bx, By), white point (Wx, Wy), min/max mastering luminance (nits), MaxCLL, and MaxFALL into structured diagnostic metadata.
- Reliable tone-mapping export pipeline: Transcoder strips audio tracks during SDR conversions, eliminating tone-mapping failures triggered by exotic surround audio formats (TrueHD, DTS-HD, multichannel PCM).
- Real-time HDR/SDR headroom tracking: Added dynamic listener on Android 14+ (`Display.registerHdrSdrRatioChangedListener`) to log panel HDR headroom shifts during session playback.

---

## v0.2.0 (Tablet & Adaptive Split-View)

- Clear active comparison states, with a filled button, checkmark, and Selected label.
- Adaptive tablet layout: approximately 25% controls and 75% testing area, with a single-column layout for narrow windows.
- Refined controls, device-neutral guidance, and capability queries off the UI thread.
- Retains the player across responsive layout changes and avoids reloading the active comparison mode.
- Optimized APK with R8 and resource shrinking, signed with the dedicated local release key for in-place updates.
