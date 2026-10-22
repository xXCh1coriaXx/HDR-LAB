# HDR Lab Light 📱

> **Are your blacks actually black, or are your dark movie scenes glowing grey?**  
> HDR Lab Light is a free, simple, privacy-focused Android app designed to test if your phone or tablet is messing up black levels when playing HDR videos.

---

## Why does this app exist?

Have you ever watched a dark movie at night on your OLED phone or tablet, but instead of true pitch-black, the dark scenes and letterbox bars look like a **glowing dark grey**?

That problem is called **"raised blacks"**. It happens when a phone's video processor, display driver, or tone-mapping curve boosts near-black code values too aggressively whenever HDR kicks in.

**HDR Lab Light gives you a simple, automated 10-second test so you can check your screen in under 2 minutes.**

---

## 📱 The Clean Two-Card Interface

HDR Lab Light features an adaptive dual-column layout optimized for smartphones and tablets (automatically switching between portrait single-column and landscape side-by-side):

<p align="center">
  <img src="docs/screenshots/hdr-lab-light-tablet-ui.png" alt="HDR Lab Light Tablet Interface" width="900">
</p>

### 1. `Screen Capabilities` / `Capacidades do Teu Ecrã`
Instantly reports what your display hardware actually supports in plain language:
* **Model & Android Version:** Confirms your device identity and OS level.
* **HDR Support:** Indicates whether your screen supports HDR playback (**HDR10**, **HLG**, **HDR10+**, **Dolby Vision**) or is limited to standard **SDR**.
* **Wide Color Gamut:** Shows whether your screen covers cinema-grade wide color (**DCI-P3** / **BT.2020**) or standard **sRGB**.
* **Estimated Peak Brightness:** Reports panel peak luminance capability (in nits) from Android display APIs.
* **Technical Report:** Need the full CTA-861.3 ST 2086 metadata and decoder enumeration? Tap the details button for an in-depth breakdown.

---

## 🎬 Automated 10-Second Near-Black Test

Tap **Start Fullscreen SDR vs HDR Test** to launch an automated, full-screen evaluation using the calibrated 6-patch near-black step pattern:

<p align="center">
  <img src="docs/screenshots/hdr-lab-light-test-player.png" alt="HDR Lab Light Fullscreen Test Player" width="900">
</p>

* **Automatic 10-Second Transition:**
  * **0 to 10 Seconds (SDR):** Plays the calibrated SDR test pattern. An on-screen countdown displays real-time observation tips.
  * **At 10 Seconds (HDR):** Automatically switches to the HDR10 (PQ) test pattern on the exact same frame.
* **Fullscreen Brightness Boost:** Automatically sets screen brightness to 100% during the test for true HDR presentation, then cleanly restores your original system brightness on exit.
* **On-Screen Player Controls:** Tap anywhere on the screen during playback to reveal controls:
  * `[ Watch SDR ]` / `[ Ver em SDR ]` — Force playback into SDR.
  * `[ Watch HDR ]` / `[ Ver em HDR ]` — Force playback into HDR10.
  * `[ 🔄 Restart ]` / `[ 🔄 Reiniciar ]` — Restart the 10-second comparison cycle.
  * `[ ✕ Exit ]` / `[ ✕ Sair ]` — Exit fullscreen and return to the main menu.
  * Controls automatically hide after 3 seconds of inactivity to preserve dark-room eye adaptation.

---

## 🔍 How to Read the Test Results

For the best results, test in a **dark room at night with lights turned off**, and disable auto-brightness, "Eye Comfort Shield", or "Night Light" in Android settings.

Look closely at two things: the **background surround** (pure black) and the **6 vertical grey bars** (near-black steps).

### 1. OLED vs LCD Black Levels (Background Surround)
* **OLED / AMOLED screens:** The background surround is encoded to reference black. On an OLED, those pixels should turn **completely off**. In a dark room, the screen should blend seamlessly into the darkness, looking like the device is powered off.
* **LCD / IPS screens:** Because LCD screens use a constant backlight behind the pixels, you will see a subtle grey glow or backlight bleed across the entire screen, even on pure black. This is a normal physical limitation of LCD technology.

### 2. Near-Black Grey Step Visibility (1%, 2% Bars)
In the center of the pattern, you will see six rectangular grey steps (from subtle near-black on the left to lighter grey on the right):
* **Correct Shadow Detail:** You should be able to distinguish the subtle dark grey bars step-by-step above the pitch-black floor.
* **"Black Crush" (Too Dark):** If the darker bars (1% and 2%) are invisible and blend completely into the black surround, your screen is crushing dark shadow details.

### 3. The SDR vs HDR Difference (Spotting "Raised Blacks")
Watch the screen closely when the video switches from SDR to HDR at the 10-second mark:
* **✅ Proper HDR Calibration (Pass):**  
  The black background remains **100% pitch black**, while the grey steps appear crisp, well-separated, and distinct.
* **❌ Raised Blacks Bug (Fail):**  
  If the pitch-black background suddenly **lights up with a glowing dark grey haze** when HDR activates, your phone's HDR software or screen processor is artificially elevating black levels.

---

## 🛠️ Main Features

* **Full Bilingual Support:** Automatically serves International English or European Portuguese (`pt-PT`) based on your device language.
* **Zero-Shader SurfaceView:** Playback connects directly to the Android Hardware Composer (`HWC`), preserving unaltered HDR presentation and dynamic metadata.
* **Automatic Brightness Handling:** Maximizes window brightness to 100% during fullscreen testing and guarantees restoration to system-managed brightness on exit.
* **Interactive A/B Switching:** Tap anytime during the test to jump back and forth between SDR and HDR to confirm differences with your own eyes.
* **Side-by-Side Coexistence:** Installs under `dev.hdrlab.light`, allowing it to live side-by-side on your device with HDR Lab Pro (`dev.hdrlab`) without package conflicts.

---

## 📥 Download & Install

1. Go to the [**Releases section**](https://github.com/xXCh1coriaXx/HDR-LAB/releases/latest).
2. Download the latest `HDR-Lab-Light-vX.Y.Z.apk`.
3. Tap the downloaded file to install it on your device (requires Android 12 or newer).

---

## 🔒 100% Private & Open Source

* **No internet needed:** The app works completely offline with zero network permissions.
* **No ads, accounts, or tracking:** Zero third-party trackers, analytics, or telemetry.
* **Zero unnecessary permissions:** Operates strictly within local sandboxed storage.

---

## 🔬 For Display Engineers & Video Specialists

Looking for deep color science specifications, CTA-861.3 ST 2086 metadata parsing, exact 10-bit YUV limited-range code values (`Y = 64`), SurfaceView zero-shader direct composition, or Media3 tone-mapping pipeline details?

👉 **Check the [HDR Lab Pro](https://github.com/xXCh1coriaXx/HDR-LAB/tree/pro) branch and the [Technical Architecture & Display Pipeline Specification (TECHNICAL_README.md)](TECHNICAL_README.md)**
