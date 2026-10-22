# HDR Lab 📱

> **Are your blacks actually black, or are your dark movie scenes glowing grey?**  
> HDR Lab is a free, simple Android app designed to test if your phone or tablet is messing up black levels when playing HDR videos.

---

## Why does this app exist?

Have you ever watched a dark movie at night on your OLED phone, but instead of true pitch-black, the black bars and dark scenes look like a **glowing dark grey**?

That problem is called **"raised blacks"**. It usually happens because your phone's software or screen processor is boosting dark areas too much when HDR kicks in.

**HDR Lab gives you simple test videos so you can see if your phone has this problem in under 2 minutes.**

---

## ⚡ Quick 2-Minute Test

You don't need any technical knowledge to test your device:

1. **Get in the dark:** Turn off the lights in your room (or do this at night).
2. **Turn off auto-brightness:** In your Android settings, disable automatic brightness and any "Eye Comfort" / "Night Light" filters.
3. **Open the app:** Tap the button that says **`Built-in black & near-black tests`**.
4. **Test Normal Black (SDR):** Tap **`SDR · reference black`**, then tap **`↗ Fullscreen / dark-room view`**.
   * *What you should see:* On an OLED screen, your phone should look like it is **completely powered off**.
5. **Test HDR Black:** Tap your screen to exit fullscreen, tap **`Built-in black & near-black tests`** again, and select **`HDR10 / PQ · reference black`**. Go fullscreen.
   * **If the screen is pitch black:** Your phone's HDR black floor is working properly!
   * **If the screen glows or looks dark grey:** Your phone's software is raising black levels in HDR mode.

---

## 🛠️ Main Features

* **Built-in Test Videos:** Comes with 9 test patterns built right in (Reference Black, Near-Black Steps, and Bright Highlight Windows) for SDR, HDR10, and HLG.
* **Dark-Room Fullscreen Switcher:** When you are in fullscreen at night, a subtle auto-dimming menu lets you switch modes without blinding your eyes with bright menus.
* **Frame Stepping (◀ / ▶):** Pause and nudge the video forward or backward by a single frame to inspect dark details and banding up close.
* **Compare HDR vs SDR:** Open any HDR video on your phone, tap **`Create SDR copy…`**, and instantly switch between the original HDR and the converted SDR version on the exact same frame to see what your screen is doing to the picture.
* **Save & Share Reports:** Export a diagnostic report with your phone's screen specs and your personal notes to share with reviewers or community forums.

---

## 📥 Download & Install

1. Go to the [**Releases section**](https://github.com/xXCh1coriaXx/HDR-LAB/releases/latest).
2. Download `HDR-Lab-v0.3.0.apk`.
3. Tap the downloaded file to install it on your device (requires Android 12 or newer).

---

## 🔒 100% Private & Open Source

* **No internet needed:** The app works completely offline.
* **No ads, accounts, or tracking:** Zero third-party trackers or telemetry.
* **Safe file access:** Uses Android's official file picker only when you choose to open a video.

---

## 🔬 For Display Engineers & Video Specialists

Looking for deep color science specifications, CTA-861.3 ST 2086 metadata parsing, exact 10-bit YUV limited-range code values, SurfaceView zero-shader direct composition, or Media3 tone-mapping pipeline details?

👉 **Read the [Technical Architecture & Display Pipeline Specification (TECHNICAL_README.md)](TECHNICAL_README.md)**
