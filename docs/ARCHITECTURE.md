# Architecture and hardware lessons

Detailed constraints behind [AGENTS.md](../AGENTS.md). Requirements describe intended behavior; known gaps below describe current limitations. Read relevant code before treating historical documentation as proof.

## Identity and playback

Application ID is `dev.hdrlab.light`; source namespace remains `dev.hdrlab`. Preserve `${applicationId}.files` in the manifest and `getPackageName() + ".files"` in Java, allowing coexistence with full `dev.hdrlab`.

Keep Media3 ExoPlayer with SurfaceView, never TextureView or an application shader, and `SeekParameters.EXACT`. Exact seeking avoids intentionally snapping to a different keyframe; it does not establish a seamless physical SDR/HDR transition. SurfaceView permits native surface composition but cannot guarantee that OS, decoder, vendor tone mapping or panel processing leaves the image unmodified. A stream label is not proof of panel output. Display-reported nits are declarations, not photometer measurements.

Fullscreen currently requests `screenBrightness = 1.0f`. Restore the previous window brightness override (normally `BRIGHTNESS_OVERRIDE_NONE`, `-1.0f`) on fullscreen exit, `onPause()`, `onStop()` and Back. This is a window override, not measured panel drive. Hide system bars with `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Dismiss revealed controls after three seconds and retain at least 76 dp bottom HUD margin for gestures. Cancel `testCountdownTicker` and `hideFullscreenHud` on stop, manual switches and fullscreen exit.

## Layout and widget ordering

`cardsContainer` inside `cardsLayout` holds Screen Capabilities and Quick Test. `oledCard` is a full-width card added to `cardsLayout` below `cardsContainer` so it does not affect the two-card responsive sizing. Wide threshold: `widthDp >= 720 || (widthDp >= 580 && isLandscape)`.

- Wide: horizontal container, `setBaselineAligned(false)`, child `LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)`.
- Flexible spacer (`height=0`, `weight=1f`) above `btnDetailedReport` aligns primary buttons at the bottom.
- Portrait: vertical, child `MATCH_PARENT`/`WRAP_CONTENT` (`-1`, `-2`). Check rotation/multi-window for jitter and clipping.

Call `setSingleLine(true)` before `setMinHeight(...)` and `setMinimumWidth(...)`. Set custom padding after `setBackground(...)`, which can reset it.

Programmatic RadioButtons start with `View.NO_ID`. Assign `View.generateViewId()` and select through `RadioGroup.check(id)`; `setChecked(true)` alone may leave `getCheckedRadioButtonId()` unsynchronized. Retain `getCheckedText()` fallback traversal inspecting `RadioButton.isChecked()`.

## Device and ROM discovery

Discover hardware dynamically from `Build.MANUFACTURER`, `BRAND`, `MODEL`, `PRODUCT`, `DEVICE`, `BOARD`, `HARDWARE` and API-guarded `SOC_MODEL` (31+). Never restrict supported brands to an allowlist. `Diagnostics.getCleanDeviceName(manufacturer, model)` capitalizes brands and removes repeated brand prefixes.

Vendor properties supplement universal detection and can be absent/inaccessible:

| ROM | Properties/hints |
| --- | --- |
| Honor MagicOS | `ro.build.version.magic`, `ro.honor.build.version.incremental` |
| Xiaomi HyperOS / MIUI | `ro.mi.os.version.name`, `ro.miui.ui.version.name` |
| Samsung One UI | `ro.build.version.oneui`, `ro.build.version.sep` |
| OnePlus OxygenOS | `ro.oxygen.version`, `ro.build.version.opporom` |
| OPPO ColorOS | `ro.build.version.opporom` |
| Pixel UI | `ro.build.flavor`, Google vendor build tags |
| Generic/custom ROM | `ro.modversion`, `ro.rom.version`, then `Android <version>` |

Retain manufacturer/model/Android-version fallbacks for every device. Vendor enhancers, contrast and tone mapping can affect observations; a ROM label does not establish causation.

## Pattern integrity and extraction

`tools/generate_patterns.py` produces SDR/PQ/HLG black, steps and window MP4s. Preserve PQ limited-range 10-bit steps `[83, 104, 137, 167, 214, 281]`, approximately `[0.01, 0.05, 0.20, 0.50, 1.50, 5.00]` nominal ST 2084 nits. These are encoded references, not measured panel brightness or evenly spaced percentages. Very low raw PQ increments may be invisible under real panel/ambient conditions.

Run `tools/verify_patterns.py` after asset changes. It checks signaling and decoded first/last frames, not all frames or physical playback. Review generation parameters too.

`getPatternFile()` extracts to `getFilesDir()/patterns/`. Refresh after APK updates rather than trusting `file.exists()`. Current `extractedPatterns` is an Activity instance field: the cache is per Activity, not process-wide. Do not document it as process-wide or regress freshness while optimizing. Direct writes can leave partial files after failure; atomic extraction is deferred.

## Optional reports and privacy

Intended flow: ten-second SDR-to-HDR test followed by voluntary reporting. Current UI can expose submission before test completion. Reports cover SDR/HDR black appearance, visible steps, optional notes, ROM and declared display specs (nits/HDR types). Older documentation calls this ten columns; inspect `ReportSubmitter` for actual JSON rather than assuming a strict column contract.

The documented Apps Script saves individual immutable JSON reports to the `HDR lab reports` Drive folder (`1VEHauaaZFqlIzDBNgHXb0RLlgMnkwoeb`), named `report_YYYY-MM-DD_HH-mm-ss_<device_model>.json`. [COMMUNITY_DATABASE.md](COMMUNITY_DATABASE.md) describes the proposed backend; it does not establish deployed configuration. Keep submission asynchronous, optional, non-blocking and safe offline. Avoid persistent identifiers or personal-data prompts; optional free text can still contain user-entered personal data.

## OLED test suite (`OledTestView`)

`OledTestView` renders Pipeline A canvas patterns (black-to-dark motion smear, 0.5%–10% near-black step ladder, 1%–15% uniformity/DSE static and optokinetic horizontal pan, 1%–100% APL white windows, and 9 subpixel burn-in/aging slides) on a `#000000` background. Levels are full-range 8-bit signal percentages (`Math.round(percent * 2.55f)`), not calibrated luminance. Pipeline B in the ladder suite reuses the packaged `sdr_steps`, `pq_steps` and `hlg_steps` reference videos on Media3 `SurfaceView`. The fullscreen HUD exposes a universal 1%–100% window brightness slider across all test modes and an SDR/HDR toggle (`ActivityInfo.COLOR_MODE_HDR` + API 35 `setDesiredHdrHeadroom`, restored on exit).

Motion patterns use `Choreographer.FrameCallback` and request the highest supported refresh mode at the current resolution via `WindowManager.LayoutParams.preferredDisplayModeId` (restored on exit). Static patterns do not schedule frame callbacks. Dropped frames are estimated from frame deltas exceeding `1.5x` the nominal display period so app stutter is not confused with panel smearing.

## Known gaps outside developer QoL cleanup

- Release key and credentials are tracked/exposed. Preserve signing continuity; deleting a working-tree file cannot revoke previous copies. Secure storage/key migration need separate work.
- Countdown measures callbacks rather than confirmed playback time; HDR status does not confirm physical output.
- Submission accepts HTTP 2xx/3xx without validating server acknowledgement. Real network, redirection and backend rejection need targeted tests and changes.
- Survey defaults, translated response text, percentage labels and schema versioning need a scientific-data pass. Existing tests do not establish scientific validity.

## Language and scope

English source/comments/IDs; UI text in resources. English `res/values/strings.xml` is fallback and European Portuguese lives in `res/values-pt/strings.xml`, selected natively by Android. No custom language switch.

Normal use stays offline. INTERNET is exclusively for requested community reports; no analytics, ads, crash beacons, accounts or persistent identifiers. Preserve R8 and resource shrinking; there is no hard 2 MB APK size limit. Avoid speculative layers and do not remove existing functionality merely to simplify.
