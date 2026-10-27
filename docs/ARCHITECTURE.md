# Runtime constraints and known limits

Read the section relevant to your change. Requirements below are not proof of physical display behaviour.

## Identity and playback

Keep application ID `dev.hdrlab.light`, Java namespace `dev.hdrlab`, manifest authority `${applicationId}.files` and Java `getPackageName() + ".files"` for coexistence with full HDR Lab.

Video uses Media3 ExoPlayer, `surface_type="surface_view"` and `SeekParameters.EXACT`; no TextureView or application shader. Exact seeking avoids keyframe snapping but does not prove a seamless physical SDR/HDR transition. SurfaceView cannot bypass or rule out all OS, decoder, vendor or panel processing. Reported nits/HDR formats are declarations, not measurements.

## Fullscreen and lifecycle

- Quick Test counts ten seconds reported as playing by Media3, excluding loading/buffering, then requests HDR10/PQ. This is not confirmation of frames presented by the panel.
- Fullscreen initially requests `screenBrightness = 1.0f`; the slider changes the window override. Restore the previous override (normally `BRIGHTNESS_OVERRIDE_NONE`, `-1.0f`) on exit, `onPause()`, `onStop()` and Back.
- Use transient system bars revealed by swipe. Hide revealed controls after three seconds (respecting Android accessibility timeouts); touch exploration or keyboard focus keeps controls visible. An explicit Hide controls action exposes the pattern. Keep at least 76 dp bottom HUD clearance and account for display cutouts.
- Cancel `testCountdownTicker` and `hideFullscreenHud` on stop, manual mode changes and fullscreen exit.
- Restore the captured colour mode, API 35 desired HDR headroom, brightness and refresh overrides on exit. Pause exits fullscreen; stop detaches/releases the player. Playback errors exit and offer guidance to start again.

## Layout and widgets

`cardsContainer` in `cardsLayout` holds Screen Capabilities and Quick Test. Keep full-width `oledCard` below it, outside two-card sizing.

Wide threshold: `widthDp >= 720 || (widthDp >= 580 && isLandscape)`. Wide: horizontal, `setBaselineAligned(false)`, children `LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)`. A `height=0, weight=1f` spacer above `btnDetailedReport` aligns buttons. Narrow/portrait: vertical, children `MATCH_PARENT/WRAP_CONTENT` (−1/−2).

Call `setSingleLine(true)` before minimum dimensions; apply padding after `setBackground()`. RadioButtons need `View.generateViewId()` and `RadioGroup.check(id)`; `setChecked(true)` alone can leave the group ID unsynchronised. Keep `getCheckedText()` fallback traversal of checked children.

Dashboard buttons wrap long labels. Fullscreen buttons remain single-line with 48 dp minimum targets; controls stack when their text cannot fit horizontally. The HUD scrolls vertically to keep large text and all actions reachable on short displays; its brightness row also stacks when needed. RGB UI accents use readable tints on neutral black surfaces, independently of pattern reference values.

## Device and ROM discovery

Discover hardware from `Build.MANUFACTURER`, `BRAND`, `MODEL`, `PRODUCT`, `DEVICE`, `BOARD`, `HARDWARE` and API-guarded `SOC_MODEL` (31+). No brand allowlist. `Diagnostics.getCleanDeviceName()` capitalises brands and removes repeated prefixes.

ROM properties are optional hints, not evidence of video processing. `Diagnostics.detectOsInfo()` contains the detection order/properties for MagicOS, HyperOS/MIUI, One UI, OxygenOS, ColorOS, Realme UI, Vivo, Pixel UI and Nothing OS. Preserve custom-ROM `ro.modversion`/`ro.rom.version` and manufacturer/model/Android-version fallbacks.

## Pattern integrity and extraction

`tools/generate_patterns.py` generates SDR/PQ/HLG black, steps and window videos. Preserve limited-range 10-bit PQ steps `[83, 104, 137, 167, 214, 281]` on reference black 64: approximately `[0.01, 0.05, 0.20, 0.50, 1.50, 5.00]` nominal ST 2084 nits. These are encoded references, not measured brightness or evenly spaced percentages. Very low levels may be invisible in real viewing conditions.

`tools/generate_oled_hdr_patterns.py` generates separate static OLED HDR10/PQ reference videos. Their limited-range 10-bit BT.2020 YCbCr signals use nominal encoded targets; real output depends on the decoder, compositor and display. Lossless x265 needs SAO and deblocking disabled for exact decoded codes; FFmpeg output color conversion flags can also alter colored YCbCr samples. The generator sets color signalling in x265 and marks Level 4.1 with a bitstream filter because x265 will not set a level in lossless mode. This marker alone does not establish decoder compatibility or level conformance; check playback on devices. Preserve the existing nine videos when generating OLED references.

After asset changes, review generation parameters and [verify patterns](../tools/README.md#results-and-assets). `getPatternFile()` extracts to `getFilesDir()/patterns/`. Refresh after APK updates; file existence alone is insufficient. `extractedPatterns` is per Activity, not process-wide. Direct writes can leave partial files; atomic extraction is deferred.

## OLED suite

`OledTestView` draws Canvas patterns on black: motion smear, 0.5%–10% ladder, 1%–15% uniformity (static or 60 px/s horizontal pan), 1%–100% area white windows, nine burn-in slides (RGB, white, 50%/20% grey, cyan, magenta, yellow), and seven SDR ramps (grayscale, RGB and CMY).

Canvas percentages are full-range 8-bit codes (`Math.round(percent * 2.55f)`), not luminance. The ladder's original video path reuses `sdr_steps`, `pq_steps`, `hlg_steps` on SurfaceView. All suites have a 1%–100% window-brightness slider. Starting a suite resets its mode to SDR.

Static ladders, uniformity, APL, color slides and ramps also have HDR10/PQ video references on the existing SurfaceView. APL selects nominal 400, 1,000 or 4,000-nit peaks, initially 1,000; ramps and color slides use a 1,000-nit channel scale. Near-black source codes map through the sRGB transfer curve to a nominal 100-nit reference white and then to quantized PQ; labels describe encoded values, not measured output. Motion smear and uniformity pan remain SDR. If HDR is unavailable, retain the test in SDR and explain why. Window HDR headroom is independent of SurfaceView content; neither requested HDR nor decoder capability proves panel output.

SDR ramps use contiguous integer-pixel bands with code 0 on the left and 255 on the right, plus localized signal labels. HDR ramps use evenly spaced PQ signal levels. These visual references reveal transitions, banding or apparent clipping; they do not measure accuracy or luminance. Views narrower than 256 physical pixels cannot show every SDR code in a separate column. Each suite's guidance asks users to record an observation and explains its limits; screen readers can read the active pattern description.

Motion uses `Choreographer.FrameCallback` and requests the highest refresh mode at the current resolution via `preferredDisplayModeId`. Static patterns schedule no frame callbacks. Detached views stop callbacks and restart visible animated patterns on attach. Dropped frames are estimated from deltas exceeding 1.5× the nominal display period; app stutter can resemble panel smear.

## Reports and language

Keep reports optional, asynchronous, non-blocking and safe offline; [payload/backend reference](COMMUNITY_DATABASE.md). No unsolicited networking, analytics, ads, crash beacons, accounts or persistent identifiers.

English source/comments/IDs/log tags; every UI string in resources. English `res/values/strings.xml` is fallback; European Portuguese uses `values-pt` and `values-pt-rPT`. Android selects the locale; no custom language switch.

## Known gaps

- Exposed release key/credentials: [signing rules](DEVELOPMENT.md#signing-and-releases).
- Media3 playback timing and requested HDR modes cannot establish physical panel output. Vendor behaviour still requires device checks.
- Submission is accessible before test completion; HTTP 2xx/3xx is accepted without server acknowledgement validation. Real network, redirects and backend rejection need targeted tests.
- Survey defaults, translated response values, percentage labels and schema versioning need scientific review. Existing tests do not establish research validity.
