# OLED Tester v0.3.0-beta.3

Third OLED suite preview for `dev.hdrlab.light`, focused on repeatable observations, fullscreen reliability and SDR ramps.

- Seven labeled SDR ramps: grayscale, red, green, blue, cyan, magenta and yellow, with full-range 8-bit signal codes from 0 to 255.
- Black interface with readable RGB accents and an updated launcher icon.
- Quick Test counts ten seconds of Media3-reported playback, excluding loading and buffering, before requesting HDR10/PQ.
- Ladder SDR/HDR controls follow the selected reference video. Fullscreen exit restores the previous brightness, refresh mode, color mode and API 35 HDR headroom; playback errors return to the dashboard with recovery guidance.
- Scrollable fullscreen controls, 48 dp minimum button targets, wrapping dashboard labels and accessibility descriptions. Touch exploration keeps controls available; Hide controls exposes the pattern.
- Brief observation prompts in English and European Portuguese explain what to record and the limits of each test. Animation statistics reset correctly after views detach and reattach.

Software checks cover Android 12 rotation/resizing, large Portuguese text, lifecycle restoration, playback timing/errors and ramp pixel values. Physical HDR, OLED/LCD behavior and TalkBack still require device validation; patterns are visual references, not panel measurements.
