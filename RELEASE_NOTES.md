# HDR Lab v0.3.0.1

## v0.3.0.1 (Maintenance & Dark-Room UI Polish)

Native Android HDR diagnostics for phones and tablets (Android 12+).

- Fullscreen HUD timeline clearance: Raised floating HUD baseline (76dp bottom clearance) to completely clear ExoPlayer's DefaultTimeBar scrubber and settings gear, avoiding accidental touch interception.
- HUD horizontal condensing: Compact pill layout with "Original", "Codec", and "GPU" labels, preventing horizontal button clipping and wrapping on narrow portrait displays.
- Test bench button alignment: Standardized "↗ Fullscreen" single-line label matching "◀ Step" / "Step ▶" button heights.
- Clean format inspection string: Human-readable transfer function, color space, and dynamic range tags (`BT.2020 · Limited · HLG / PQ`) replacing raw Media3 ColorInfo dumps.

---

# HDR Lab v0.3.0

Native Android HDR diagnostics for phones and tablets (Android 12+).

- Dark-room in-fullscreen A/B switcher: Toggle between Original, SDR Codec, and SDR GPU comparisons directly in Fullscreen mode using a low-luminance auto-dimming HUD, preventing eye dark-adaptation loss.
- Single-frame stepping: Step forward and backward frame-by-frame (◀ / ▶) while paused on both the test bench and in fullscreen for inspecting near-black macroblocking, dithering, and edge glow.
- Exact-frame seeking: Configured ExoPlayer with `SeekParameters.EXACT` to eliminate sync keyframe rounding when toggling comparisons on long-GOP streams.
- CTA-861.3 / ST 2086 HDR Static Metadata parser: Decodes mastering display primaries (Rx, Ry, Gx, Gy, Bx, By), white point (Wx, Wy), min/max mastering luminance (nits), MaxCLL, and MaxFALL into structured diagnostic metadata.
- Reliable tone-mapping export pipeline: Transcoder strips audio tracks during SDR conversions, eliminating tone-mapping failures triggered by exotic surround audio formats (TrueHD, DTS-HD, multichannel PCM).
- Real-time HDR/SDR headroom tracking: Added dynamic listener on Android 14+ (`Display.registerHdrSdrRatioChangedListener`) to log panel HDR headroom shifts during session playback.

## Validation

## Validation

Build and lint passed; seven Robolectric regression and metadata unit tests passed. The nine test assets remain byte-identical to v0.1.0/v0.2.0.

## Release assets

- `HDR-Lab-v0.3.0.apk` — installable optimized build (local testing signature).
- `HDR-Lab-v0.3.0-source.zip` — source project.
- `HDR-Lab-v0.3.0-checksums.txt` — SHA-256 integrity checksums.

APK SHA-256: `ba071c9b7d083143221c3dd9edc49c585f876e2c870de17ba0a12aefa0d30c9f`.

---

# HDR Lab v0.2.0

Native Android HDR diagnostics for phones and tablets (Android 12+).

- Clear active comparison states, with a filled button, checkmark, and Selected label.
- Adaptive tablet layout: approximately 25% controls and 75% testing area, with a single-column layout for narrow windows.
- Refined controls, device-neutral guidance, and capability queries off the UI thread.
- Retains the player across responsive layout changes and avoids reloading the active comparison mode.
- Optimized APK with R8 and resource shrinking, signed with the same local testing key as v0.1.0 for in-place updates.

Includes SDR, HDR10/PQ, and HLG black, near-black, and bright-window test clips; original playback; codec/GPU SDR comparisons; and shareable reports.

## Validation

Release build and lint passed; five Robolectric UI regression tests passed. The nine test assets are byte-identical to v0.1.0. Physical-device HDR playback and performance still require verification; no speed or battery improvement percentage is claimed.

## Release assets

- `HDR-Lab-v0.2.0.apk` — installable optimized build (local testing signature).
- `HDR-Lab-v0.2.0-source.zip` — source project.
- `HDR-Lab-v0.2.0-guide.md` — usage and build guide.

APK SHA-256: `411a019d03ed19bdbadcc8f111293c2952e405defc9dbf4d08977be01f7303c2`.
