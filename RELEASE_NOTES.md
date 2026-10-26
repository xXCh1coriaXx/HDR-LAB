# OLED Tester v0.3.0-beta.2 — OLED Screen Quality & Defect Testing Suite (Preview 2)

This second preview release adds the Universal In-Test Brightness Bar, Subpixel Burn-In & Aging Suite, and SDR vs. HDR Mode Selection across **OLED Tester** (`dev.hdrlab.light`).

- **Universal Fullscreen Brightness Bar:** Available across *all* test modes (automated SDR-to-HDR Quick Test video playback + all OLED Canvas suites) allowing testing of black crush, banding, and ABL throttling from 1% up to 100% window brightness.
- **Subpixel Burn-In & Aging Suite:** 9 full-field test slides (Pure Red, Pure Green, Pure Blue, Pure White, 50% Mid Gray, 20% Dark Gray, Cyan, Magenta, Yellow) to isolate individual subpixel wear, color tinting, and image retention.
- **SDR vs. HDR Mode Toggle:** Direct user switching between SDR (`ActivityInfo.COLOR_MODE_DEFAULT` / BT.709) and HDR (`ActivityInfo.COLOR_MODE_HDR` / API 35 `Window.setDesiredHdrHeadroom` and automatic SDR/PQ video stream switching in the Step Ladder).
- **Enriched Qualitative Reporting:** Added subpixel burn-in / image retention survey question (`oledBurnIn`) to the optional community database submission.
- **Robust Lifecycle Cleanup:** Safely restores window brightness, refresh rate overrides, and color modes on `exitFullscreen()`, `onPause()`, `onStop()`, and Back.
