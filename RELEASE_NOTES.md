# HDR Lab Light v0.1.0

## v0.1.0 (Initial Release)

HDR Lab Light is an accessible, lightweight edition of HDR Lab designed for everyday smartphone and tablet users to evaluate display capabilities and test SDR vs HDR near-black performance in under 2 minutes.

- **Independent Standalone App (`dev.hdrlab.light`):**
  - Configured with a dedicated package identity (`dev.hdrlab.light`) allowing HDR Lab Light to coexist side-by-side on the same device with the original full-featured HDR Lab (`dev.hdrlab`).
  - Completely eliminates package collision and `INSTALL_FAILED_VERSION_DOWNGRADE` conflicts during installation.
- **Two-Card Accessible Interface:**
  - **Capacidades do Teu Ecrã:** Instant summary of panel capabilities in plain language (HDR support: HDR10, HLG, Dolby Vision vs SDR; Wide Color Gamut: DCI-P3 vs sRGB; estimated peak nits), with quick access to the deep technical CTA-861.3 / ST 2086 report dialog when desired.
  - **Teste Rápido (SDR vs HDR):** Single-tap entry point to run an automated comparative test in fullscreen.
- **Automated 10-Second Near-Black Transition:**
  - Automatically plays the calibrated 6-patch near-black step pattern (`*_steps.mp4`) in SDR for 10 seconds accompanied by a real-time countdown ticker and visual guide.
  - Seamlessly transitions to HDR10 (PQ) at second 10 on the exact matching frame to evaluate true black surround (OLED zero-emittance vs LCD backlight glow) and shadow detail tracking (1% and 2% grey bars).
- **Fullscreen Dark-Room Mode & Controls:**
  - Automatically boosts window brightness to 100% (`screenBrightness = 1.0f`) during fullscreen evaluation and guarantees full restoration to system-managed brightness on exit.
  - Tap-to-reveal low-luminance floating controls (`[ Ver em SDR ]`, `[ Ver em HDR ]`, `[ ⟲ Reiniciar ]`, `[ ✕ Sair ]`) with 3-second auto-dimming to preserve dark-room eye adaptation.
- **Zero-Shader Hardware Composer Pipeline:**
  - Dedicated `SurfaceView` bypasses Android UI compositing shaders directly to the Hardware Composer (`HWC`), guaranteeing unadulterated HDR metadata and pixel values.
  - Signed with the project release keystore for seamless future updates.
