# HDR Lab Light v0.1.7

## v0.1.7 (Event-Sourced JSON Drive Ingestion & Dynamic Device Discovery)

HDR Lab Light v0.1.7 replaces direct spreadsheet cell editing with an event-sourced JSON file architecture in Google Drive and introduces 100% dynamic, universal brand and model auto-discovery across all Android devices worldwide.

### What's New in v0.1.7

- **100% Dynamic Brand & Model Auto-Discovery:**
  - Zero hardcoded OEM lists or brand dropdowns: automatically detects hardware from `Build.MANUFACTURER`, `Build.BRAND`, `Build.MODEL`, `Build.DEVICE`, `Build.PRODUCT`, and `Build.HARDWARE`.
  - Smart brand capitalization and prefix deduplication (e.g. `samsung SM-S928B` -> `Samsung SM-S928B`, `motorola motorola edge 50` -> `Motorola edge 50`).
  - Universal custom ROM detection with generic `ro.modversion` and `ro.rom.version` fallback.
- **Event-Sourced JSON Google Drive Ingestion:**
  - Submissions are stored as individual, immutable JSON report files directly in the designated Google Drive folder (`HDR lab reports`).
  - Eliminates spreadsheet concurrency locks, write collisions, cell formatting glitches, and mobile editing friction.
  - Generates clean, traceable filenames: `report_YYYY-MM-DD_HH-mm-ss_<DeviceName>.json`.
- **Enriched Hardware & Display Telemetry:**
  - Extracts full hardware specs: peak and minimum luminance (nits), wide color gamut (`isWideColorGamut`), display mode & refresh rate (e.g. `3120 × 1440 @ 120.00 Hz`), supported HDR types (`Display.HdrCapabilities`), and SoC model (`Build.SOC_MODEL` on API 31+).
