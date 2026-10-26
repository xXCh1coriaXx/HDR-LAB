# Agent quick start

Academic/research Android app. Read only the references relevant to your task:

- Setup, checks, code map, releases: [DEVELOPMENT](docs/DEVELOPMENT.md).
- Playback, layout, diagnostics, patterns, surveys: relevant [ARCHITECTURE](docs/ARCHITECTURE.md) sections.
- Report payload/backend: [COMMUNITY_DATABASE](docs/COMMUNITY_DATABASE.md).
- Script options: [tools](tools/README.md). Historical docs are not implementation instructions.

## Working rules

- Start with `git status --short` and the relevant diff. Preserve unrelated changes; keep scope narrow. Do not push, tag or publish unless requested.
- Use `rg`, targeted reads and compact output. Avoid duplicate facts, speculative layers and unused helpers; preserve functionality.
- Inspect the final diff and run unit tests before claiming a fix. Build/manifest/dependency changes require release compilation; assets require pattern verification. See [checks](docs/DEVELOPMENT.md#verification).
- Report checks and blockers, requirement coverage, assumptions, low confidence and likely regressions. Consider lifecycle, multi-window, detached views, offline failures and OLED/LCD differences. Software tests cannot certify physical HDR.
- Record new technical lessons in the relevant reference, not in duplicate summaries.

## Token use

- Search symbols/paths first; read relevant sections. Expand context when dependencies or uncertainty require it; accuracy comes first.
- Reuse findings; reread when files change or uncertainty requires it.
- Bound command output. Save verbose logs under build output; return status and relevant failures.
- Run required checks; repeat only after relevant changes, failures or unresolved concerns.
- When delegation is authorised, give subagents narrow tasks, minimal necessary context and concise reporting requirements.
- Keep updates and final answers proportionate; retain material risks, assumptions and verification results.

## Constraints

- Preserve `dev.hdrlab.light` identity, signing continuity and dynamic FileProvider authority. The tracked release key/credentials are exposed; never copy credentials, silently rotate/delete the key or substitute debug signing. See [signing](docs/DEVELOPMENT.md#signing-and-releases).
- Preserve SurfaceView/exact seeking, fullscreen restoration/timers/HUD, responsive layout/RadioGroup behaviour and pattern reference values: [runtime rules](docs/ARCHITECTURE.md).
- English source, IDs, comments and log tags. UI strings use English and European Portuguese resources with native locale selection.
- Normal use stays offline. Network access is only for optional user-triggered reports; no analytics, ads, crash beacons, accounts or persistent identifiers.
- Preserve R8/resource shrinking. No hard 2 MB limit; never reduce pattern integrity to save bytes.
