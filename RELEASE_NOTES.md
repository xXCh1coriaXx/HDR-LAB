# HDR Lab v0.2.0

Native Android HDR diagnostics for phones and tablets (Android 12+).

- Clear active comparison states, with a filled button, checkmark, and Selected label.
- Adaptive tablet layout: approximately 25% controls and 75% testing area, with a single-column layout for narrow windows.
- Refined controls, device-neutral guidance, and capability queries off the UI thread.
- Retains the player across responsive layout changes and avoids reloading the active comparison mode.
- Optimized APK with R8 and resource shrinking, signed with the same local testing key as v0.1.0 for in-place updates.

Includes SDR, HDR10/PQ, and HLG black, near-black, and bright-window test clips; original playback; codec/GPU SDR comparisons; and shareable reports.

## Validation

Release build and lint passed; five Robolectric UI regression tests passed. The nine test assets are byte-identical to v0.1.0. Physical-device HDR playback and performance still require verification; no speed or battery improvement percentage is claimed.

## Release assets

- `HDR-Lab-v0.2.0.apk` — installable optimized build (local testing signature).
- `HDR-Lab-v0.2.0-source.zip` — source project.
- `HDR-Lab-v0.2.0-guide.md` — usage and build guide.

APK SHA-256: `411a019d03ed19bdbadcc8f111293c2952e405defc9dbf4d08977be01f7303c2`.
