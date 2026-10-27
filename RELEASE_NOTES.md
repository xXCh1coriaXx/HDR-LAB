# OLED Tester v0.3.0-beta.5

Fifth OLED suite preview for `dev.hdrlab.light`, delivering security hardening, crash and edge-case resilience, diagnostic reflection optimization, and storage cleanliness.

- **Security & Privacy Hardening**: Strict HTTPS transport enforcement on community report submission with reliable socket/stream cleanup in `finally` blocks; global cleartext traffic disabled via network security config; 500-character input bounds on public survey comments.
- **Crash & Edge-Case Resilience**: Null-safe display mode formatting and mode selection guards in diagnostics; guarded `Float.isFinite` on Android 14+ `display.getHdrSdrRatio()` to prevent non-finite JSON parser exceptions; `isFinishing()` check on asynchronous hardware reports preventing token exceptions.
- **Animation & Timing Fixes**: Resynchronized frame delta and drop counters in moving OLED tests after app pauses or backgrounding, avoiding false-positive drop spikes.
- **Performance & Cleanliness**: Cached `android.os.SystemProperties.get` reflection method statically, eliminating 10–15 repeated class and method lookups per diagnostic pass; removed unused FileProvider paths; report exports now use canonical cache storage to prevent storage accumulation.
