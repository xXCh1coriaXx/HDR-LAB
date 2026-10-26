# OLED Tester 📱

Developing or reviewing the app? Start with the [development guide](docs/DEVELOPMENT.md) for setup, verification commands and the code map. [AGENTS.md](AGENTS.md) is the concise agent entry point; [architecture notes](docs/ARCHITECTURE.md) preserve hardware lessons and known issues.

> **Instant, privacy-first Android OLED & HDR display diagnostic.** Test in seconds if your screen delivers pure OLED blacks or suffers from black crush, purple motion smearing, dirty screen effect (DSE), ABL dimming, or raised grey blacks in HDR.

<p align="center">
  <img src="docs/screenshots/hdr-lab-light-tablet-ui.png" alt="OLED Tester Tablet Interface" width="850">
</p>

<p align="center">
  <a href="https://github.com/xXCh1coriaXx/HDR-LAB/releases/latest">
    <img src="https://img.shields.io/badge/Download-Latest_APK-brightgreen?style=for-the-badge&logo=android" alt="Download APK">
  </a>
  <img src="https://img.shields.io/badge/Languages-EN_|_PT--PT-purple?style=for-the-badge" alt="Languages">
</p>

---

## ⚡ Core Diagnostic Suites

1. **Check Your Hardware:** Instantly detects your device model, custom OS (MagicOS, HyperOS, One UI, OxygenOS...), supported HDR standards (HDR10, HLG, Dolby Vision), refresh rates, and declared peak brightness in nits.
2. **Quick Test (10s SDR vs HDR):** Launches an automated fullscreen test with 100% brightness boost:
   * **0s to 10s:** SDR near-black pattern.
   * **At 10s:** Automatically transitions to HDR10 (PQ) at the exact same frame.
3. **OLED Test Suite:** Fullscreen patterns for isolating OLED/AMOLED panel and driving artifacts:
   * **Black-to-Dark Smear (Motion):** Choreographer-synchronized `#050505`–`#202020` moving blocks on a `#000000` background across 100–2000 px/s horizontal and vertical motion, with an in-test brightness slider and live FPS / dropped-frame readout.
   * **Near-Black Ladder & Black Crush:** Dual-pipeline comparison between Pipeline A (native Canvas 0.5%–10% patches) and Pipeline B (Media3 `SurfaceView` playback of calibrated SDR, HDR10/PQ, and HLG reference step videos).
   * **Near-Black Uniformity / DSE:** 1%–15% uniform dark gray fields in both static and slow horizontal optokinetic pan modes to expose vertical banding and mottling.
   * **ABL / APL Windows & Floating Black:** 1%–100% display area white windows on `#000000` to observe Auto Brightness Limiter dimming and stray surround glow.

<p align="center">
  <img src="docs/screenshots/hdr-lab-light-test-player.png" alt="HDR Lab Light Fullscreen Test Player" width="850">
</p>

---

## 👁️ What to Look For

* **Background Surround (Pure Black):**  
  * **OLED / AMOLED:** Pixels must turn **completely off** (pitch black, seamlessly blending into a dark room).
  * **Raised Blacks Bug:** If the pitch-black background suddenly glows dark grey when HDR activates, your device is artificially elevating blacks.
* **6 Near-Black Steps (Shadow Detail):**  
  * All 6 subtle grey bars should be distinguishable without making the surrounding black background glow.
  * If the first 2 bars (1% and 2%) are invisible, your screen is **crushing shadows** ("black crush").

---

## 📋 Community Display Database

After running the test, tap **"Submit Community Report"** to optionally share your observations:
* Automatically detects and tags your device model, custom ROM (MagicOS, HyperOS, One UI, OxygenOS...), and hardware display specs.
* Evaluates SDR baseline vs. HDR transition black level and shadow step visibility.
* Directly appends your anonymous report to our open, crowd-sourced 10-column community database.
* Zero personal data, zero accounts, and 100% privacy guaranteed.

---

## 📥 Download

👉 **[Download Latest APK](https://github.com/xXCh1coriaXx/HDR-LAB/releases/latest)** *(~1.8 MB · Android 12+)*

* **100% Private & Open Source:** Zero trackers, zero ads, zero accounts.
* **Side-by-Side Coexistence:** Installs under `dev.hdrlab.light`, living peacefully alongside the pro version.
* **Pro Engineering Test Bench:** Looking for raw CTA-861 / ST 2086 metadata, YUV code values, or GPU tone mapping? Check the [`pro`](https://github.com/xXCh1coriaXx/HDR-LAB/tree/pro) branch.
