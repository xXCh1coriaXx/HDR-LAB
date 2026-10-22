# HDR Lab Light v0.1.2

## v0.1.2 (Full Bilingual Support: International English & Portuguese pt-PT)

HDR Lab Light v0.1.2 introduces comprehensive bilingual internationalization (i18n), standardizing the application on an International English foundation with full European Portuguese (`pt-PT`) localization, automated system locale detection, and strict separation of UI strings from application logic.

### What's New in v0.1.2

- **Full Bilingual Support (EN & PT-PT):**
  - **Automatic System Locale Detection:** Automatically detects device system language—serving International English by default worldwide and dynamically switching to European Portuguese (`pt-PT`) on Portuguese device locales.
  - **Android XML Resource Separation:** 100% of user-facing interface text, diagnostic card titles, descriptions, technical dialogs, and test HUD labels are decoupled into native Android resource catalogs (`res/values/strings.xml` and `res/values-pt/strings.xml`).
  - **International Standard English Codebase:** Standardized layout templates, view IDs, and Java implementation to international English conventions while preserving localized presentation.
- **Tablet Adaptive Side-by-Side Layout & Near-Black Test:**
  - **Dynamic Two-Column Layout:** Maintains responsive side-by-side card placement on wide screens and landscape orientations (width >= 720dp or >= 580dp in landscape) with matched heights and aligned action buttons.
  - **Automated Calibrated Test:** Evaluates display true black and near-black shadow detail tracking via automated 10-second SDR-to-HDR transition on the 6-patch step pattern with exact-frame switching.

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
