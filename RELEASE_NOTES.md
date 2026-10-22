# HDR Lab Light v0.4.0

## v0.4.0 (HDR Lab Light Edition)

An accessible, streamlined edition of HDR Lab designed for everyday users to evaluate display capabilities and test SDR vs HDR near-black performance in under 2 minutes.

- **Two-Card Accessible Interface:**
  - **Capacidades do Teu Ecrã:** Instant summary of device panel capabilities in plain language (HDR support: HDR10, HLG, Dolby Vision vs SDR; Wide Color Gamut: DCI-P3 vs sRGB; estimated peak nits), with access to the detailed technical report dialog.
  - **Teste Rápido (SDR vs HDR):** Single-tap entry point to run an automated 20-second comparison test in fullscreen.
- **Automated 10-Second Near-Black Transition:**
  - Runs the calibrated 6-patch near-black step pattern (`*_steps.mp4`) in SDR for 10 seconds with a countdown ticker and visual guide.
  - Automatically transitions to HDR10 (PQ) at the exact matching frame to evaluate true black surround (OLED zero-emittance vs LCD backlight glow) and shadow detail tracking (1% and 2% grey bars).
- **Fullscreen Dark-Room Mode & Controls:**
  - Automatically boosts window brightness to 100% (`screenBrightness = 1.0f`) during playback and guarantees restoration to system brightness upon exit.
  - Tap-to-reveal floating controls (`[ Ver em SDR ]`, `[ Ver em HDR ]`, `[ ⟲ Reiniciar ]`, `[ ✕ Sair ]`) with 3-second auto-dimming to preserve dark-room eye adaptation.
- **Hardware Composer Integrity:**
  - Dedicated zero-shader `SurfaceView` bypasses Android UI compositing for unadulterated HDR output.
  - Signed with the official release key for seamless in-place updates.
