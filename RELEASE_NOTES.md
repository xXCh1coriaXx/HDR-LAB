# HDR Lab Light v0.1.8

## v0.1.8 (Calibrated PQ Near-Black Steps & Session Asset Cache Refresh)

HDR Lab Light v0.1.8 re-encodes the HDR10 / PQ near-black test pattern (`pq_steps.mp4`) with calibrated perceptual luminance steps ($0.01\text{ to }5.00\text{ nits}$) and refreshes extracted video assets on app launch so APK updates immediately apply new test patterns.

### What's New in v0.1.8

- **Calibrated 10-bit PQ Near-Black Steps (`pq_steps.mp4`):**
  - Replaced raw 1-to-32 code value increments (`[65..96]`, which mapped to $0.00005\text{ to }0.03\text{ nits}$ and fell below mobile OLED DDIC cutoffs and ambient reflections) with calibrated 10-bit limited-range SMPTE ST 2084 code values `[83, 104, 137, 167, 214, 281]`:
    - **Reference Black:** Code Value `64` ($0.00\text{ nits}$)
    - **Step 1:** Code Value `83` ($0.01\text{ nits}$)
    - **Step 2:** Code Value `104` ($0.05\text{ nits}$)
    - **Step 3:** Code Value `137` ($0.20\text{ nits}$)
    - **Step 4:** Code Value `167` ($0.50\text{ nits}$)
    - **Step 5:** Code Value `214` ($1.50\text{ nits}$)
    - **Step 6:** Code Value `281` ($5.00\text{ nits}$)
  - Verified frame-accurate decoding and HDR10 static metadata (`max-cll=1000,400`, `smpte2084`, `bt2020nc`).
- **Session Asset Cache Refresh:**
  - Updated `getPatternFile()` in `MainActivity.java` to refresh extracted `.mp4` assets into `getFilesDir()/patterns/` once per app session (`extractedPatterns`), ensuring in-place APK upgrades immediately replace previously cached video files without requiring users to clear app storage.
