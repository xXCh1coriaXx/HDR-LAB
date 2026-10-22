# HDR Lab — Changelog

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
