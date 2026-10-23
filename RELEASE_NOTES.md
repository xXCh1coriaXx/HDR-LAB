# HDR Lab Light v0.1.6

## v0.1.6 (4-Question SDR vs HDR Community Survey)

HDR Lab Light v0.1.6 introduces an updated 4-question display survey report structure that separates SDR and HDR evaluation phases, enabling precise tracking of raised blacks and tone mapping shadow detail across both modes.

### What's New in v0.1.6

- **Updated 4-Question Survey Structure:**
  - **Section 1: SDR (First 10s):**
    - Question 1 (SDR Black Background Level): Pure absolute black (OLED / Pixels off) vs. subtle grey glow (Standard LCD).
    - Question 2 (SDR Near-Black Steps 1% to 6%): Visibility rating from all 6 steps down to black crush.
  - **Section 2: HDR (After transition at 10s):**
    - Question 3 (HDR Transition Black Level): Pure black vs. elevated grey (raised blacks bug) vs. standard LCD glow.
    - Question 4 (HDR Near-Black Steps 1% to 6%): Tone mapping step accuracy rating from all 6 steps down to crushed shadows.
  - **Section 3: Notes:**
    - Question 5: Optional qualitative observations.
- **Enhanced Data Ingestion Schema:**
  - Extended payload parameters: `sdr_black`, `sdr_steps`, `hdr_black`, `hdr_steps` alongside backwards-compatible aliases `black_level` and `steps_visible`.
- **Full Bilingual Localization:**
  - Complete English and European Portuguese (`pt-PT`) localization for all 4 questions and option choices.
