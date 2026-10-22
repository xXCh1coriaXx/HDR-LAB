# HDR Lab v0.3.0.1

## v0.3.0.1 (Maintenance & Dark-Room UI Polish)

Native Android HDR diagnostics for phones and tablets (Android 12+).

- **Fullscreen HUD timeline clearance:** Raised floating HUD baseline (76dp bottom clearance) to completely clear ExoPlayer's DefaultTimeBar scrubber and settings gear, avoiding accidental touch interception.
- **HUD horizontal condensing:** Compact pill layout with "Original", "Codec", and "GPU" labels, preventing horizontal button clipping and wrapping on narrow portrait displays.
- **Test bench button alignment:** Standardized "↗ Fullscreen" single-line label matching "◀ Step" / "Step ▶" button heights.
- **Clean format inspection string:** Human-readable transfer function, color space, and dynamic range tags (`BT.2020 · Limited · HLG / PQ`) replacing raw Media3 ColorInfo dumps.
