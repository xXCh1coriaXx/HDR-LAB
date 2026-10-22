# HDR Lab — Technical Architecture & Display Pipeline Specification

Native Android diagnostic engine designed for investigating raised black levels, electro-optical transfer function (EOTF) tracking, tone-mapping curves, and hardware decoder behavior across Android 12+ (API level 31 through 36).

---

## 1. Core Architecture & Surface Composition

```
  +------------------------------------------------------------------+
  |                        Android Display System                    |
  |                                                                  |
  |  +-----------------------+           +------------------------+  |
  |  |   UI / Controls       |           |   SurfaceView Window   |  |
  |  |   (Standard SDR Canvas|           |   (Direct HW Pipeline) |  |
  |  |    BT.709 sRGB)       |           |   Zero Shader Filters  |  |
  |  +-----------+-----------+           +-----------+------------+  |
  +--------------|-----------------------------------|---------------+
                 |                                   |
                 v                                   v
           [SurfaceFlinger]                 [Hardware Composer (HWC)]
                 |                                   |
                 +-----------------+-----------------+
                                   |
                                   v
                    [Display Processor / DDIC Panel]
```

* **Zero-Shader SurfaceView Pass-Through:** Video frames play exclusively through an Android `SurfaceView`. Unlike a `TextureView`, which forces video frames into the application's OpenGL/Vulkan rendering pipeline as an SDR texture, `SurfaceView` allocates a dedicated hardware surface directly composited by Android's Hardware Composer (`HWC`). No color transforms, tone curves, or shaders are applied by the app.
* **Decoder Instance Retention:** Responsive layout transitions between phone (single-column) and tablet (25% controls / 75% video viewport) reparent existing View hierarchies without detaching the active Surface or resetting the underlying `MediaCodec` instance.
* **Exact-Frame Alignment:** Playback and variant comparison seek calls enforce `androidx.media3.exoplayer.SeekParameters.EXACT`. This bypasses default nearest-sync-keyframe rounding, enabling frame-accurate A/B comparisons across long-GOP HEVC streams.

---

## 2. Signal Specifications & Pattern Matrix

All bundled test clips use identical container parameters: 1280×720, 24.00 fps, 4.0 seconds, limited-range YUV 4:2:0, encoded via HEVC Main 10 profile at level 4.1.

| Signal | Transfer Function (EOTF) | Primaries | Matrix | Black Y (10-bit) | Neutral Cb/Cr | Near-Black Patches (Y, 10-bit) | 10% Bright Window (Y) |
|---|---|---|---|---|---|---|---|
| **SDR** | BT.709 | BT.709 | BT.709 | 64 | 512 | 68, 72, 80, 96, 128, 192 | 940 (Peak White) |
| **HDR10** | SMPTE ST 2084 (PQ) | BT.2020 | BT.2020 Non-Constant | 64 | 512 | 65, 66, 68, 72, 80, 96 | 723 (~1000 nits signal target) |
| **HLG** | ARIB STD-B67 | BT.2020 | BT.2020 Non-Constant | 64 | 512 | 65, 66, 68, 72, 80, 96 | 940 (Nominal peak signal) |

### Test Pattern Design Intent
1. **Reference Black (`*_black.mp4`):** All pixels in every frame are encoded to code value `Y = 64` (limited-range code-black for 10-bit video) and `Cb = Cr = 512`. If an OLED panel exhibits any perceptible photon emission in a completely dark room during PQ playback while SDR reference black remains fully extinguished, the fault lies within the device's HDR processing, tone-mapping curve, or panel drive path.
2. **Near-Black Steps (`*_steps.mp4`):** Six rectangular patches centered horizontally across a reference-black surround.
   * *Note on cross-transfer comparisons:* Patches are signal-level increments, **not luminance-matched steps**. Step visibility is designed for evaluating quantization tracking within a single format, not for comparing SDR directly to PQ or HLG.
3. **10% Bright Window (`*_window.mp4`):** Reference black background with a centered 10% window. Evaluates whether localized high-nit highlights trigger unintended global black-floor elevation, local-dimming blooming, or ABL (Average Picture Level) tone-curve adjustments.

Generation is executed directly at the YUV planar buffer layer via [`tools/generate_patterns.py`](tools/generate_patterns.py) without intermediate RGB round-tripping. Verification is performed by [`tools/verify_patterns.py`](tools/verify_patterns.py).

---

## 3. Metadata Extraction & CTA-861.3 ST 2086 Decoder

When inspecting container streams via `MediaExtractor`, the app extracts and parses binary `KEY_HDR_STATIC_INFO` payloads complying with CTA-861.3 / SMPTE ST 2086:

```
CTA-861.3 Type 1 Payload Layout (25 bytes, Little-Endian):
+--------+----------------------------+------------------+-------------------+------------------+---------------+----------------+
| Byte 0 | Bytes 1-12                 | Bytes 13-16      | Bytes 17-18       | Bytes 19-20      | Bytes 21-22   | Bytes 23-24    |
| Type ID| Primaries (G, B, R in xy)  | White Point (xy) | Max Mastering Lum | Min Mastering Lum| MaxCLL (nits) | MaxFALL (nits) |
| (0x00) | uint16 * 0.00002           | uint16 * 0.00002 | uint16 (1 nit)    | uint16 (0.0001)  | uint16 (1 nit)| uint16 (1 nit) |
+--------+----------------------------+------------------+-------------------+------------------+---------------+----------------+
```

The resulting parsed structure is injected into the diagnostic schema:

```json
{
  "descriptorType": 0,
  "displayPrimaries": {
    "red":   { "x": 0.7080, "y": 0.2920 },
    "green": { "x": 0.1700, "y": 0.7970 },
    "blue":  { "x": 0.1310, "y": 0.0460 }
  },
  "whitePoint": { "x": 0.3127, "y": 0.3290 },
  "maxMasteringLuminanceNits": 1000,
  "minMasteringLuminanceNits": 0.0001,
  "maxContentLightLevelNits": 1000,
  "maxFrameAverageLightLevelNits": 400
}
```

---

## 4. Tone Mapping & SDR Comparison Engine

To isolate HDR-to-SDR differences, the app uses AndroidX Media3 `Transformer` to execute deterministic local conversions:
1. **Android MediaCodec Tone Mapper:** Uses `Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC` to query platform vendor hardware decoder extensions for tone mapping.
2. **Media3 OpenGL Tone Mapper:** Uses `Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL` to run Media3's internal GLSL shader tone-mapping implementation.

### Validation & Pipeline Isolation
* **Audio Elimination:** Tone mapping invocations enforce `EditedMediaItem.Builder.setRemoveAudio(true)`. Test files containing unsupported multi-channel streams (Dolby TrueHD, DTS-HD MA, 7.1 LPCM) are stripped of audio, guaranteeing that transcoding is tested purely on video surface capabilities.
* **Output Color Verification:** Before accepting an exported comparison, the output stream is validated with `MediaExtractor`. If `KEY_COLOR_TRANSFER` fails to explicitly register as `COLOR_TRANSFER_SDR_VIDEO`, or if HDR flags remain signaled, the comparison target is rejected.

---

## 5. Android Display APIs & Diagnostics Snapshot

During session execution, the diagnostic subsystem inspects:
* **`Display.getHdrCapabilities()`:** Advertised HDR types (`HDR_TYPE_HDR10`, `HDR_TYPE_HLG`, `HDR_TYPE_DOLBY_VISION`, `HDR_TYPE_HDR10_PLUS`) and OS-reported desired min/max/average luminance.
* **`Display.getHdrSdrRatio()` & Dynamic Headroom:** On Android 14+ (API 34+), the app attaches a `java.util.function.Consumer<Display>` via `registerHdrSdrRatioChangedListener` to record real-time shifts in available HDR headroom caused by thermal constraints or ambient brightness changes.
* **`MediaCodecList` Enumeration:** Hardware vs software codec declarations, profile/level integer limits, and fallback availability across HEVC, AV1, VP9, and Dolby Vision.
* **Dropped Frame Analytics:** Tracks `AnalyticsListener.onDroppedVideoFrames` to correlate stutter or frame loss with specific decoder pipelines.

---

## 6. Build & CI/CD Pipeline

Requirements:
* JDK 17 or 21 (Temurin / OpenJDK)
* Android SDK Platform 36 (`platforms;android-36`)
* Build-Tools 36.0.0 (`build-tools;36.0.0`)
* Gradle 8.13 / AGP 8.13.0

```sh
# Run Lint and Robolectric regression tests (7 test cases)
./gradlew testDebugUnitTest lintRelease

# Build optimized release APK (R8 minification and resource stripping enabled)
./gradlew assembleRelease
```

Automated builds, Robolectric regressions, and GitHub Releases are orchestrated via [`.github/workflows/release.yml`](.github/workflows/release.yml).
