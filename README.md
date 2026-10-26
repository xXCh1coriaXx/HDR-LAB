# OLED Tester

Check how your Android screen shows dark detail, motion and HDR. For academic and personal observation; this app does not measure or calibrate your panel.

**[Download the APK](https://github.com/xXCh1coriaXx/HDR-LAB/releases/latest)** · Android 12+ · English / European Portuguese

The link opens the latest release. For a beta, choose its preview entry on the [releases page](https://github.com/xXCh1coriaXx/HDR-LAB/releases).

## Start a test

1. Open the app and review your device's reported display capabilities.
2. Start **Quick Test**. It opens fullscreen at maximum window brightness, counts ten seconds of SDR playback (excluding loading or buffering), then requests HDR10.
3. Compare the black background and visible grey steps. Tap to show controls; leave fullscreen to restore your previous brightness setting.
4. Use the OLED suites for other checks:

| Test | Look for |
| --- | --- |
| Motion smear | Coloured trails behind moving dark blocks |
| Near-black ladder | Dark grey steps that disappear into black (black crush) |
| Uniformity | Uneven grey, bands or blotches (dirty screen effect) |
| White windows | Dimming as the bright area grows, or glow around it |
| Burn-in / ageing | Persistent shapes or uneven colour on solid slides |
| SDR color and grayscale ramps | Bands, abrupt transitions or highlights that appear to merge across grayscale, RGB and CMY ramps |

The suites include brightness controls and SDR/HDR selection, except for the SDR-only ramps. Read each pattern's observation prompt and record the variant, brightness, display mode and room lighting when comparing results. Tap to reveal controls; **Hide controls** exposes the pattern. Controls scroll on small screens and stay available during touch exploration.

## Understand the results

On OLED, compare black areas with the dark room around the screen. LCD backlights can produce a glow. Note changes between modes rather than assuming any glow proves a defect.

Count the steps you can see. Canvas percentages and HDR video reference levels differ; they are not measured brightness. Room light, display settings and vendor processing affect results. Reported HDR formats and brightness in nits are device claims, not confirmation of physical output.

![App overview](docs/screenshots/hdr-lab-light-tablet-ui.png)
![Fullscreen test](docs/screenshots/hdr-lab-light-test-player.png)

## Optional reports

Tap **Submit Community Report** to share observations, device/OS details and reported display specs. Testing works offline; only submission needs internet. No accounts, ads or tracking identifiers are used. Avoid personal information in optional notes. See [report details and limitations](docs/COMMUNITY_DATABASE.md).

The app installs as `dev.hdrlab.light` alongside full HDR Lab. The [pro branch](https://github.com/xXCh1coriaXx/HDR-LAB/tree/pro) contains the engineering test bench.

Developers: [setup and checks](docs/DEVELOPMENT.md). Agents: [working rules](AGENTS.md).
