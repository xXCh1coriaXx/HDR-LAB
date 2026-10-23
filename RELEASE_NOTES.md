# HDR Lab Light v0.1.5

## v0.1.5 (Community Display Database & Custom OS Detection)

HDR Lab Light v0.1.5 introduces an anonymous, privacy-focused Community Display Report & Database integration powered by Google Forms and Google Sheets. Users can evaluate their display's true black emittance and shadow detail tracking during the automated test and optionally submit calibration reports. Combined with automated custom Android OS / ROM detection, this crowd-sourced database helps document vendor-specific display processing quirks, raised near-black bugs, and tone-mapping behaviors across Android ecosystems.

### What's New in v0.1.5

- **Community Display Report & Database Integration (Google Forms / Sheets):**
  - **User-Prompted Calibration Survey:** Direct post-test prompt allowing users to submit their perceptual display evaluation—recording OLED true black vs. raised black bugs (or LCD glow), 6-patch near-black step visibility (distinguishing 1% and 2% grey steps), and optional qualitative notes.
  - **Automated Custom OS & ROM Detection:** Automatically queries underlying Android system properties to identify vendor-specific skins and versions, including:
    - Honor MagicOS (`ro.build.version.magic`)
    - Xiaomi HyperOS / MIUI (`ro.mi.os.version.name`, `ro.miui.ui.version.name`)
    - Samsung One UI (`ro.build.version.oneui`, `ro.build.version.sep`)
    - OnePlus OxygenOS (`ro.oxygen.version`, `ro.build.version.opporom`)
    - OPPO ColorOS (`ro.build.version.opporom`)
    - Google Pixel UI (`ro.build.flavor`, Google vendor build tags)
    - Stock AOSP and standard vendor distributions
  - **Anonymous & Privacy-Focused Database:** Telemetry strictly limits submitted data to display hardware specs, detected ROM version, and user visual evaluations—without personal identifiers, accounts, or persistent advertising IDs.
  - **Controlled Internet Permission:** Added `<uses-permission android:name="android.permission.INTERNET" />` solely to facilitate optional, user-triggered report submission over HTTP/HTTPS.

### HDR Lab Light Core Features

- **Automated 10-Second Near-Black Transition:**
  - Automatically plays the calibrated 6-patch near-black step pattern (`*_steps.mp4`) in SDR for 10 seconds accompanied by a real-time countdown ticker and visual guide.
  - Seamlessly transitions to HDR10 (PQ) at second 10 on the exact matching frame to evaluate true black surround (OLED zero-emittance vs LCD backlight glow) and shadow detail tracking (1% and 2% grey bars).
- **Tablet Adaptive Side-by-Side Layout:**
  - Dynamic two-column card arrangement on wide screens and landscape orientations (width >= 720dp or >= 580dp in landscape) with matched heights and aligned action buttons.
- **Zero-Shader Hardware Composer Pipeline:**
  - Dedicated `SurfaceView` bypasses application shaders and connects directly to the Android Hardware Composer (`HWC`), preserving unaltered HDR presentation and metadata.
- **Fullscreen Dark-Room Mode & 100% Brightness Boost:**
  - Automatically overrides window brightness to 100% (`screenBrightness = 1.0f`) during fullscreen evaluation for full panel drive, with guaranteed restoration to system brightness upon exit.
  - Low-luminance tap-to-reveal floating controls with 3-second auto-dimming to preserve dark-room eye adaptation.
- **Side-by-Side Coexistence (`dev.hdrlab.light`):**
  - Dedicated standalone application identity coexists seamlessly with the original full-featured HDR Lab (`dev.hdrlab`) without package conflicts or version downgrade errors.
