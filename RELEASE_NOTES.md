# HDR Lab Light v0.1.6.1

## v0.1.6.1 (Survey RadioGroup State Tracking Fix)

HDR Lab Light v0.1.6.1 resolves a critical RadioGroup selection capture issue in the community survey dialog, ensuring default options (Option A) are reliably serialized without requiring manual touch taps.

### What's New in v0.1.6.1

- **Reliable Programmatic RadioGroup Tracking:**
  - Assigned dynamic view IDs via `View.generateViewId()` to programmatically created survey radio buttons.
  - Replaced direct `RadioButton.setChecked(true)` with `RadioGroup.check(id)` to update internal `mCheckedId` state across all vendor skins.
  - Added multi-tier fallback `getCheckedText()` helper verifying both checked ID and active child selection state.
- **SDR vs HDR 4-Question Comparison Matrix:**
  - Retains the full comparative SDR (baseline black level and 1%-6% steps) and HDR (raised black bug detection and tone mapping steps) matrix.
- **10-Column Community Database Integration:**
  - Direct real-time ingestion to the crowd-sourced Google Sheets database.
