# HDR Lab 📱

> **Are your blacks actually black, or are your dark movie scenes glowing grey?**  
> HDR Lab is a free, simple Android app designed to test if your phone or tablet is messing up black levels when playing HDR videos.

---

## Why does this app exist?

Have you ever watched a dark movie at night on your OLED phone, but instead of true pitch-black, the dark scenes and letterbox bars look like a **glowing dark grey**?

That problem is called **"raised blacks"**. It happens when a phone's video processor, display driver, or tone-mapping curve boosts near-black code values too aggressively whenever HDR kicks in.

**HDR Lab gives you a simple, automated 10-second test so you can check your screen in under 2 minutes.**

---

## 📱 The Simplified Two-Button Interface

HDR Lab is designed with a focused, two-card interface so anyone can run an instant diagnostic:

```
+-----------------------------------------------------------+
|                        HDR Lab                            |
|                                                           |
|  [ Card 1: Capacidades do Teu Ecrã ]                      |
|  * HDR Support: Yes (HDR10, HLG, Dolby Vision) or SDR     |
|  * Color Gamut: Wide (DCI-P3 / BT.2020) or Standard       |
|  * Peak Brightness: Estimated Nits & Display Tech         |
|  * [ Ver Relatório Técnico ] (Optional detailed report)   |
|                                                           |
|  [ Card 2: Teste Rápido: SDR vs HDR ]                     |
|  * 10 seconds SDR ➔ 10 seconds HDR (PQ)                   |
|  * Calibrated near-black test pattern                     |
|  * [ ▶ Iniciar Teste SDR vs HDR em Ecrã Cheio ]           |
+-----------------------------------------------------------+
```

### 1. `Capacidades do Teu Ecrã` (Display Capabilities at a Glance)
Instantly tells you what your hardware actually supports without confusing technical jargon:
* **Model & Android Version:** Confirms your device and OS level.
* **HDR Support:** Indicates whether your screen supports HDR playback (**HDR10**, **HLG**, **Dolby Vision**) or is limited to standard **SDR**.
* **Wide Color Gamut:** Shows whether your screen covers cinema-grade wide color (**DCI-P3** / **BT.2020**) or standard **sRGB**.
* **Estimated Peak Brightness:** Reports panel peak luminance capability (in nits) when available from Android display APIs.
* **Technical Report:** Need the full CTA-861.3 ST 2086 metadata and display enumeration? Tap the details button for an in-depth breakdown.

### 2. `Teste Rápido: SDR vs HDR` (10-Second Automated Comparison)
Tap the test button to launch an automated, full-screen comparison using calibrated near-black steps:
* **Automatic 10-Second Transition:**
  * **0 to 10 Seconds (SDR):** Plays the calibrated SDR test pattern. An on-screen countdown displays tips.
  * **At 10 Seconds (HDR):** Automatically switches to the HDR10 (PQ) test pattern on the exact same frame.
* **Fullscreen Brightness Boost:** Maximizes screen brightness during the test for true HDR presentation, then automatically restores your previous brightness when you exit.
* **On-Screen Player Controls:** Tap anywhere on the screen during playback to reveal controls:
  * `[ Ver em SDR ]` — Force playback into SDR.
  * `[ Ver em HDR ]` — Force playback into HDR10.
  * `[ 🔄 Reiniciar ]` — Restart the 10-second comparison cycle.
  * `[ ✕ Sair ]` — Exit fullscreen and return to the main menu.
  * Controls automatically hide after 3 seconds of inactivity so you can observe the screen uninterrupted.

---

## 🔍 How to Read the Test Results

For the best results, test in a **dark room at night with lights turned off**, and disable auto-brightness, "Eye Comfort Shield", or "Night Light" in Android settings.

Look closely at two things: the **background surround** (pure black) and the **6 vertical grey bars** (near-black steps).

### 1. OLED vs LCD Black Levels (Background Surround)
* **OLED / AMOLED screens:** The background surround is encoded to reference black. On an OLED, those pixels should turn **completely off**. In a dark room, the phone screen should blend seamlessly into the darkness, looking like the device is powered off.
* **LCD / IPS screens:** Because LCD screens use a constant backlight behind the pixels, you will see a subtle grey glow or backlight bleed across the entire screen, even on pure black. This is a normal physical limitation of LCD technology.

### 2. Near-Black Grey Step Visibility (1%, 2% Bars)
In the center of the pattern, you will see six rectangular grey steps:
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

* **Instant Two-Button Workflow:** Check screen capabilities in one tap, and launch a 10-second comparison in the next.
* **Hardware Composer Direct Surface:** Video plays through native `SurfaceView` without OpenGL/Vulkan app shaders, ensuring untouched HDR delivery directly to the display hardware.
* **Automatic Brightness Handling:** Automatically optimizes window brightness to maximum during fullscreen testing and cleanly restores your original brightness on exit.
* **Interactive A/B Switching:** Tap anytime during the test to jump back and forth between SDR and HDR to confirm differences with your own eyes.

---

## 📥 Download & Install

1. Go to the [**Releases section**](https://github.com/xXCh1coriaXx/HDR-LAB/releases/latest).
2. Download the latest `HDR-Lab.apk`.
3. Tap the downloaded file to install it on your device (requires Android 12 or newer).

---

## 🔒 100% Private & Open Source

* **No internet needed:** The app works completely offline.
* **No ads, accounts, or tracking:** Zero third-party trackers, analytics, or telemetry.
* **Zero unnecessary permissions:** Operates strictly within local sandboxed storage.

---

## 🔬 For Display Engineers & Video Specialists

Looking for deep color science specifications, CTA-861.3 ST 2086 metadata parsing, exact 10-bit YUV limited-range code values (`Y = 64`), SurfaceView zero-shader direct composition, or Media3 tone-mapping pipeline details?

👉 **Read the [Technical Architecture & Display Pipeline Specification (TECHNICAL_README.md)](TECHNICAL_README.md)**
