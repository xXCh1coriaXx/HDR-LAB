# OLED Tester v0.3.0-beta.4

Fourth OLED suite preview for `dev.hdrlab.light`, adding HDR10/PQ reference patterns and fixing SDR ramp seams.

- SDR grayscale and color ramps use pixel-aligned bands to remove dark seams.
- HDR10/PQ references for static near-black ladders, uniformity, white windows, color slides, and grayscale/RGB/CMY ramps. Moving smear and panning tests remain SDR.
- White-window HDR targets of 400, 1,000, and 4,000 nominal nits, with 1,000 as the default. HDR ramps and color slides use a fixed 1,000-nit channel scale.
- HDR playback failures or unsupported formats return the selected test to SDR with an explanation. Existing SDR/PQ/HLG ladder videos remain available.
- Red technical report accents, blue OLED test controls, and green Quick Test controls; English and European Portuguese labels describe requested formats and nominal signal levels.

On an Honor tablet, the user confirmed clean SDR/HDR ramps and visible differences among the HDR window targets. The system reported a BT.2020/PQ video surface. Encoded nits are reference values, not measured panel output; tone mapping, decoder support, device behavior, and viewing conditions can change the result.
