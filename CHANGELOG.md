# Changelog

Historical changes, not guarantees of current behaviour. See [current constraints](docs/ARCHITECTURE.md).

## OLED Tester / HDR Lab Light

### v0.3.0-beta.3 — SDR ramps and fullscreen reliability

- Seven SDR grayscale/RGB/CMY ramps with 0–255 code labels and localized observation guidance.
- Black interface and readable RGB accents across cards, controls and launcher icon.
- Playback-aware Quick Test countdown, synchronized ladder mode controls, captured HDR-headroom restoration and playback-error recovery.
- Scrollable/adaptive fullscreen controls, 48 dp button targets, wrapping dashboard labels and touch-exploration support in EN/PT-PT.
- Fresh animation statistics after detach/reattach; regression coverage for ramps, Android 12 rotation/resizing, large text, playback stalls/errors and lifecycle cleanup.

### v0.3.0-beta.2 — OLED suite preview 2

- Universal 1%–100% fullscreen brightness slider for video/Canvas.
- Nine burn-in/ageing slides: RGB, white, 50%/20% grey, cyan, magenta, yellow.
- SDR/HDR toggle using window colour mode, API 35 headroom and SDR/PQ ladder switching.
- Optional `oledBurnIn` report field; EN/PT-PT resource coverage and Robolectric tests.

### v0.3.0-beta.1 — OLED suite preview

- Renamed app to OLED Tester in EN/PT-PT; retained package/signing identity.
- Motion smear: `#050505`–`#202020` on black, 100–2000 px/s horizontal/vertical, Choreographer, FPS/drop counter and brightness control.
- Near-black ladder (0.5%–10%): Canvas versus SDR/PQ/HLG SurfaceView videos.
- Uniformity (1%–15%, static/60 px/s pan), white windows (1%–100% area), nine burn-in slides, universal brightness and SDR/HDR controls.
- Diagnostics/reporting: active/supported refresh modes, highest-refresh fullscreen selection, `oledBlackCrush`, `oledSmear`, `oledUniformity`, `oledFloatingBlack`, `oledBurnIn`.
- Pause/exit/stop restore brightness/refresh; manual HDR switching cancels countdown. Removed 2 MB cap.

### v0.2.0 — Cleanup

- Validation script: JDK/SDK discovery, logs, decoded pattern checks and a historical 2,000,000-byte budget (later removed).
- CI: unit tests, lint, release build, patterns and retained reports.
- Release tag/version validation and safe manual dispatch.
- BuildConfig report version; removed unused Media3 Transformer.
- Shortened agent guide, added developer/architecture references and consistent editor settings.
- Retained playback/assets/signing; key exposure and report delivery remained unresolved.

### v0.1.8 — PQ steps and extraction refresh

- Re-encoded `pq_steps.mp4` with codes `[83, 104, 137, 167, 214, 281]` on black 64, nominal `[0.01, 0.05, 0.20, 0.50, 1.50, 5.00]` nits, addressing previously invisible low steps (<0.03 nits).
- Refresh cached videos after app updates. Originally described as per process; implementation is per Activity.

### v0.1.7 — JSON reports and device discovery

- Dynamic Build-property discovery, brand capitalisation and prefix deduplication replaced brand lists.
- JSON POST reporting with proposed individual files in Drive folder `HDR lab reports`; deployed configuration remains unverified.
- Added declared peak/min luminance, gamut, resolution/refresh, SoC and HDR formats alongside survey results.

### v0.1.6.1 — RadioGroup selection fix

- Generated IDs, `RadioGroup.check(id)` and checked-child fallback fixed untouched default selections missing from reports.

### v0.1.6 — SDR/HDR survey

- Four questions compare SDR/HDR black appearance and six shadow steps.
- Historical ten-column schema: timestamp, model, OS, HDR capabilities, peak brightness, SDR black/steps, HDR black/steps and notes.
- Webhook accepted `sdr_black`, `sdr_steps`, `hdr_black`, `hdr_steps` plus legacy `black_level`/`steps_visible`.
- Vendor-ROM detection; retained shader-free SurfaceView (then described as an HWC bypass), exact-seeking ten-second transition, tablet cards and EN/PT-PT. Current architecture explains the processing limits.

### v0.1.5.1 — Webhook integration

- Configured Apps Script endpoint for the then-documented community Sheet.
- Isolated mock endpoints kept unit tests offline.

### v0.1.5 — Community reports

- Optional Forms/Sheets survey for black appearance, near-black steps and notes.
- Vendor OS/ROM discovery via properties; display/ROM/observation reporting without persistent tracking identifiers.
- INTERNET permission limited to user-triggered reports.
- Retained tablet layout, ten-second comparison, SurfaceView and EN/PT-PT.

### v0.1.2 — Localisation

- Localised UI, cards, reports and HUD in English/European Portuguese using Android locale selection.
- Separated resource strings from English source/layouts; retained matched tablet cards and near-black comparison.

### v0.1.1 — Tablet layout

- Side-by-side cards at ≥720 dp, or ≥580 dp in landscape; vertical stacking elsewhere.
- Equal heights/weighted spacers aligned action buttons and kept landscape setup/actions visible without scrolling.

### v0.1.0 — Light initial release

- `dev.hdrlab.light` coexists with full `dev.hdrlab`, avoiding package/version conflicts.
- Two-card setup for a short evaluation; fullscreen six-patch SDR-to-HDR comparison after ten seconds.
- Maximum window brightness with restoration; tap-to-reveal HUD, three-second dismissal and manual mode toggles.
- SurfaceView playback without an application shader.

## Earlier full HDR Lab history

These entries describe the full app, not features promised by Light.

### v0.3.0.1 — Dark-room controls

- 76 dp HUD clearance avoids scrubber/settings overlap.
- Compact Original/Codec/GPU controls prevent narrow-screen clipping.
- Single-line Fullscreen button matches frame-step button heights.
- Readable transfer/colour/range labels replace raw ColorInfo output.

### v0.3.0 — Hardware pipeline analysis

- Dim fullscreen Original/SDR Codec/SDR GPU comparisons.
- Paused forward/back frame stepping on bench/fullscreen for compression, dithering and edge inspection.
- Exact seeking avoids long-GOP keyframe rounding.
- CTA-861.3/ST 2086 metadata: RGB primaries, white point, mastering min/max nits, MaxCLL/MaxFALL.
- SDR export strips audio to avoid TrueHD/DTS-HD/multichannel-PCM conversion failures.
- Android 14+ `Display.registerHdrSdrRatioChangedListener` tracks headroom changes.

### v0.2.0 — Adaptive split view

- Filled/checkmarked Selected state; approximately 25% controls/75% test area and narrow-screen stacking.
- Device-neutral guidance, refined controls and capability queries off the UI thread.
- Retained player/current comparison through layout changes.
- R8/resource shrinking and dedicated release signing for in-place updates.
