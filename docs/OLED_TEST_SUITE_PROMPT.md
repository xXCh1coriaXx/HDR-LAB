# OLED suite: historical requirements

This checklist replaces the original implementation prompt. It records intent, not proof of scientific validity or a request to rebuild the suite. For current behaviour and constraints, read [ARCHITECTURE](ARCHITECTURE.md); for checks, read [DEVELOPMENT](DEVELOPMENT.md#verification).

- **Motion smear:** Moving blocks on black using `#050505`, `#080808`, `#0F0F0F`, `#181818`, `#202020`; horizontal/vertical speeds 100–2000 px/s. Synchronise with display refresh using Choreographer (including 60/90/120/144 Hz where supported). Show FPS/dropped frames and brightness controls.
- **Shadow steps:** Labelled 0.5%, 1%, 2%, 3%, 4%, 5%, 7.5%, 10% patches. Compare native Canvas/UI with packaged 8-bit SDR and 10-bit PQ/HLG SurfaceView videos to explore different rendering paths.
- **Uniformity:** Full-field 1%, 2%, 3%, 5%, 10%, 15% greys; static and slow horizontal motion over faint vertical patterns to help reveal banding/blotches.
- **White windows:** Centred 1%, 2%, 5%, 10%, 25%, 50%, 100% area windows on black. Observe surrounding glow and brightness reduction as area grows; power/thermal limits can affect results.
- **Reports:** Include active/supported refresh modes and optional observations of black crush threshold, smear severity, uniformity and surrounding black.

The original constraints remain in the current guides: package/signing continuity, SurfaceView/exact seeking, fullscreen restoration, EN/PT-PT resources, offline testing, optional reports and verification. High-quality assets take priority over APK size.
