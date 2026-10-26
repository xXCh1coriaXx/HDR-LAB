# OLED Tester v0.3.0-beta.1 — OLED Screen Quality & Defect Testing Suite (Preview)

This preview release expands the app into **OLED Tester** (`dev.hdrlab.light`) with a dedicated fullscreen OLED screen quality test suite alongside the 10-second SDR-to-HDR transition diagnostic.

- **Black-to-Dark Motion & Smear Suite:** Choreographer-synchronized `#050505`–`#202020` moving blocks on a `#000000` background across 100–2000 px/s horizontal and vertical speeds, with an in-test brightness slider and live FPS / dropped-frame readout.
- **Near-Black Step Ladder & Black Crush Suite:** Dual-pipeline comparison between Pipeline A (native Canvas `0.5%`–`10.0%` numbered patches) and Pipeline B (Media3 `SurfaceView` playback with `SeekParameters.EXACT` for calibrated SDR, HDR10/PQ, and HLG reference step videos).
- **Near-Black Uniformity / DSE Suite:** Full-field `1%`–`15%` uniform dark gray slides with static and continuous horizontal optokinetic pan (`60` px/s) modes to expose vertical banding and mottling.
- **Floating Black & ABL / APL Stress Suite:** Centered `1%`–`100%` display area white windows on `#000000` to inspect stray surround glow and Auto Brightness Limiter dimming.
- **Subpixel Burn-In & Aging Suite:** 9 full-field slides (Pure Red, Green, Blue, White, 50% Mid Gray, 20% Dark Gray, Cyan, Magenta, Yellow) to inspect individual subpixel wear and image retention.
- **Universal Brightness Bar & SDR/HDR Mode Toggle:** In-test `1%`–`100%` window brightness slider across all video and canvas suites, plus an SDR/HDR pipeline toggle (`COLOR_MODE_HDR` / `setDesiredHdrHeadroom` and SDR/PQ video switching).
- **Display Mode Diagnostics & Community Survey:** Logs active and supported display refresh modes, requests the highest-refresh mode at the active resolution in fullscreen, and adds optional survey fields for black crush, purple smear, uniformity/DSE, floating black, and subpixel burn-in.
- **Localization & Sizing:** Full English and European Portuguese (`values-pt` / `values-pt-rPT`) localization; removed the hard 2 MB APK size cap while keeping R8 and resource shrinking enabled.
