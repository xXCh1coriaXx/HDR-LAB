# HDR Lab Light v0.1.5.1

## v0.1.5.1 (Live Community Webhook Integration)

HDR Lab Light v0.1.5.1 activates the live Google Sheets community database webhook. User calibration reports now directly append to the central crowd-sourced display calibration database in real time.

### What's New in v0.1.5.1

- **Live Community Database Webhook:**
  - Connected the production Google Apps Script webhook endpoint to [`ReportSubmitter.java`](file:///f:/HDR-LAB/app/src/main/java/dev/hdrlab/ReportSubmitter.java).
  - Submissions from the test player dialog are now automatically stored in the Google Sheets database (`HDR Tester Database`).
  - Verified atomic ingestion with zero client-side API secrets.
- **Custom OS & ROM Detection:**
  - Automatic vendor skin detection (Honor MagicOS, Xiaomi HyperOS/MIUI, Samsung One UI, OnePlus OxygenOS, OPPO ColorOS, Google Pixel UI, etc.).
- **Anonymous & Offline-Safe:**
  - Zero personal telemetry, accounts, or persistent advertising IDs.
  - Graceful fallback and offline handling.
