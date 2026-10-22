# HDR Lab Light v0.1.1

## v0.1.1 (Adaptive Side-by-Side Tablet Layout)

HDR Lab Light v0.1.1 introduces an adaptive dual-column layout optimized for tablets, foldables, and landscape orientations, ensuring all display diagnostics and test controls fit perfectly in view without vertical scrolling.

### What's New in v0.1.1

- **Adaptive Two-Card Tablet & Landscape Layout:**
  - **Dynamic 2-Column Positioning:** On tablets and wide screens in landscape orientation (viewport width >= 720dp or >= 580dp in landscape), *"Capacidades do Teu Ecrã"* and *"Teste Rápido: SDR vs HDR"* are dynamically arranged side-by-side.
  - **Equal Height & Aligned Action Buttons:** Both cards stretch to matching heights with weighted spacers pushing bottom action buttons (*"Ver Relatório Técnico Detalhado"* and *"Iniciar Teste SDR vs HDR"*) to an identical baseline.
  - **Zero Vertical Scrolling:** Fully eliminates vertical scrolling in landscape, ensuring the primary start test button is never clipped or pushed offscreen.
  - **Fluid Responsive Adaptation:** Automatically collapses back to a clean single-column stacked view on smartphones and portrait orientations without layout stutter.

### HDR Lab Light Core Features

- **Automated 10-Second Near-Black Transition:**
  - Automatically plays the calibrated 6-patch near-black step pattern (`*_steps.mp4`) in SDR for 10 seconds accompanied by a real-time countdown ticker and visual guide.
  - Seamlessly transitions to HDR10 (PQ) at second 10 on the exact matching frame to evaluate true black surround (OLED zero-emittance vs LCD backlight glow) and shadow detail tracking (1% and 2% grey bars).
- **Zero-Shader Hardware Composer Pipeline:**
  - Dedicated `SurfaceView` bypasses application shaders and connects directly to the Android Hardware Composer (`HWC`), preserving unaltered HDR presentation and metadata.
- **Fullscreen Dark-Room Mode & 100% Brightness Boost:**
  - Automatically overrides window brightness to 100% (`screenBrightness = 1.0f`) during fullscreen evaluation for full panel drive, with guaranteed restoration to system brightness upon exit.
  - Low-luminance tap-to-reveal floating controls with 3-second auto-dimming to preserve dark-room eye adaptation.
- **Side-by-Side Coexistence (`dev.hdrlab.light`):**
  - Dedicated standalone application identity coexists seamlessly with the original full-featured HDR Lab (`dev.hdrlab`) without package conflicts or version downgrade errors.
