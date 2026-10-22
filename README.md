# HDR Lab

A native Android diagnostic player focused on raised blacks during HDR playback.
For Android phones and tablets running Android 12 or newer, including Android 16. HDR playback and tone-mapping support depend on the device and source format.

## Version 0.3.0

- In-dark-room comparison switching: Toggle Original, SDR Codec, and SDR GPU directly inside Fullscreen view with an auto-dimming HUD that preserves human dark adaptation.
- Exact-frame seeking & frame stepping: Pause and step frame-by-frame (◀ / ▶) on the test bench and in fullscreen. ExoPlayer uses `SeekParameters.EXACT` to lock comparison alignment across long-GOP keyframes.
- Decoded CTA-861.3 / ST 2086 metadata: Generates structured JSON for mastering display primaries, D65 white point, min/max mastering luminance, MaxCLL, and MaxFALL.
- Audio-isolated tone mapping: Diagnostic SDR transcoder strips audio streams to ensure complex multichannel audio formats cannot cause false-negative tone-mapping failures.
- Real-time HDR/SDR headroom logging: Monitors dynamic ratio adjustments on Android 14+ via `Display.registerHdrSdrRatioChangedListener`.

## Version 0.2.0

- Clear comparison selection: a filled lime button, checkmark, explicit Selected label, and an active-view heading above the video. Uncreated SDR copies are visibly unavailable.
- Adaptive layout: wider windows use a roughly 25% controls sidebar and 75% testing area with independent scrolling. The sidebar stays at least 224 dp wide. Narrow windows use a single column; rotation and split-screen adapt without rebuilding the player.
- Refined button spacing, rounded cards, stronger visual hierarchy, and clearer primary actions.
- Device-neutral guidance and notes.
- Capability enumeration runs off the UI thread and reports reuse the cached device snapshot. Selecting the already-active view does not reload the video. Video decoder and player instances survive responsive layout changes.

The v0.2.0 download is an optimized build: R8 optimization and unused-resource stripping are enabled, and debugging is disabled. It is signed with the same local testing key as the first build. No percentage improvement in speed or battery use is claimed.

Install the v0.2.0 APK over the earlier version to retain private files and notes. It uses the same app ID and signing key with a higher version code.

## Use on your devices

1. Download `HDR-Lab-v0.2.0.apk` from this repository’s Releases section and install it over the existing installation. Android may ask you to allow installation from the app opening the APK.
2. Open **Built-in black & near-black tests**. Compare **SDR reference black**, **HDR10/PQ reference black**, and **HLG reference black** in fullscreen in a dark room. Controls hide; Back exits fullscreen.
3. Keep brightness and viewing conditions consistent. Disable automatic brightness and any device video enhancement/boost or eye-comfort processing that is available on your firmware. Record exact settings in the notes.
4. Start with the all-black clips. If only the HDR rectangle glows, you have evidence of a difference in the HDR playback/display path. It does not by itself identify an OS, firmware, player, panel, or metadata fault.
5. Use near-black tests to record patch visibility within each format. The patches are not luminance matched across SDR, PQ, and HLG.
6. Open a local HDR video. Pause at a useful scene and choose **Create SDR copy…**. Create the codec and GPU copies separately. Switch Original / SDR codec / SDR GPU to compare the same timeline position within the converted interval.
7. Add notes and share a diagnostic JSON report from each device. Select an SDR copy and choose **Save selected SDR video** to keep it outside the app.

The bright-window clips add a centered 10% area window on reference black: PQ code Y=723 (about 1000 nits encoded), SDR/HLG code Y=940. These are signal targets, not measured output; SDR and HLG are not luminance matched to PQ. Use these to investigate processing that changes black levels when highlights are present.

The built-in reference black clips isolate the black floor more clearly than a random movie scene. They contain no bright patches, captions, audio, or overlays in the encoded picture. The near-black files contain six patches on a reference-black surround.

## What this app reports

- HDR types and desired luminance advertised by Android, active display mode, wide-gamut support, and an HDR/SDR ratio snapshot if Android exposes it.
- Hardware/software HEVC, AV1, VP9, and Dolby Vision decoder declarations and profile/level integers.
- Container track dimensions, frame rate when present, transfer, color standard/range, and raw static HDR metadata when available.
- Actual selected video decoder and session dropped-frame events during playback.
- Android MediaCodec and Media3 OpenGL HDR-to-SDR conversions, with output metadata checked before accepting an SDR comparison.
- Per-window fixed brightness control, observation notes, conversion errors and timings, and shareable reports.

The original plays through a **SurfaceView**, allowing Android to handle HDR video separately from the SDR interface. No shader is applied to the original. The app does not force your display’s internal tone curve or claim to detect its actual HDR state. SDR exports involve decoding, tone mapping, and H.264 re-encoding; they are comparisons, not a calibrated reference. Codec/GPU conversion can fail when a source profile or device pipeline is unsupported. The app reports that failure rather than labeling the unconverted file as SDR. Dynamic Dolby Vision/HDR10+ metadata is not preserved in SDR exports, and these test assets do not test those dynamic formats.

## Pattern specification

All formats use the same HEVC Main10 codec and bit depth to reduce unrelated playback differences. All nine test assets are 1280×720, 24 fps, 4 seconds, neutral limited-range YUV 4:2:0. They loop in the player.

| Signal | Codec / depth | Black Y | Neutral Cb/Cr | Near-black patch Y, left to right |
| --- | --- | --- | --- | --- |
| SDR BT.709 | HEVC Main10 / 10-bit | 64 | 512 | 68, 72, 80, 96, 128, 192 |
| HDR10, BT.2020 PQ | HEVC Main10 / 10-bit | 64 | 512 | 65, 66, 68, 72, 80, 96 |
| HLG, BT.2020 | HEVC Main10 / 10-bit | 64 | 512 | 65, 66, 68, 72, 80, 96 |

PQ files include mastering-display metadata (BT.2020 primaries, D65, 1000-nit maximum, 0.0001-nit minimum) and nominal MaxCLL=1000 / MaxFALL=400. These are deliberately fixed test metadata, **not measured content maxima**. Black and step tests contain black or near-black pixels; the window tests add the documented bright center patch. HLG has no PQ mastering metadata. The generator writes YUV directly without an RGB conversion; verification decodes first and last frames and checks black, neutral chroma, patch centers, range, and transfer labels. Compression may affect patch edges.

```sh
python3 tools/generate_patterns.py  # requires ffmpeg with 10-bit libx265
python3 tools/verify_patterns.py
```

## Build

Open this folder in Android Studio with JDK 17 or 21, Android SDK Platform 36 and Build Tools 36.0.0. Let Gradle sync, then run `app` on either device or build an APK:

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
./gradlew assembleRelease lintRelease  # optimized sideload build
```

Uses Gradle 8.13, Android Gradle Plugin 8.13.0, and AndroidX Media3 1.5.1. The debug APK remains available for development. The release build uses R8 and resource shrinking and is signed with the local debug key for continuity with the first sideloaded version; configure a dedicated release key before store distribution.

## Limits and storage

No Internet permission, analytics, accounts, camera access, or broad storage permission. Android's document picker grants access to selected videos. The last source URI is remembered locally. SDR exports and reports remain in private app storage until cleared or the app is uninstalled. Use **Clear generated SDR copies** to reclaim export storage. Clip names and device/build details are included only in reports you explicitly share; source content URIs are omitted.

Conversions run while the app is in the foreground and cancel if it leaves the foreground. Short 10-second conversions are the default to limit heat and storage use. Converted files survive until cleared, but comparison buttons reset if the process is recreated or a new source is opened. Device capability details in shared reports are from the most recently cached snapshot (initial scan or a manual details query). Fixed window brightness does not override firmware brightness limits, thermal behavior, HDR boost, or ambient adaptation elsewhere in the display pipeline.

Neither screenshots nor Android's reported luminance capabilities measure the panel's actual black floor. Use a suitable meter for quantitative luminance measurements. This build must be tested on your physical devices to establish HDR behavior.
