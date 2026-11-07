# TeachTrack / 师迹

TeachTrack is a local-first Android workspace for a teacher managing long-term student growth records.

## Current version

`TeachTrack 3.0 · TeachTrack Copilot` — Android `3.0.0-alpha.5`.

The 1.x local-first MVP remains usable without an account, cloud service, network, or AI. Version 2 added an optional natural-language command layer: typed or voice input is interpreted by a configurable provider and converted into validated local actions. Version 3 adds the Portable Workspace foundation and completes Copilot's Action Registry coverage across every implemented module (see `AI_SPEC/31_V2_REQUIREMENTS.md` `V2-REQ-040`).

## Safety model

The model cannot access Room or execute arbitrary code. It can only produce a versioned command envelope consumed by an allow-listed local Action Registry. Sensitive writes require preview and confirmation. Provider secrets are never included in logs or backups.

## Build

```powershell
./gradlew.bat assembleDebug
```

## Documents

- `AI_SPEC/30_V2_AI_SPEC.md`
- `AI_SPEC/31_V2_REQUIREMENTS.md`
- `AI_SPEC/32_V2_ARCHITECTURE.md`
- `AI_SPEC/33_V2_TASK_TREE.md`
- `AI_SPEC/34_V2_SECURITY_AND_PROVIDER.md`
- `AI_SPEC/35_V2_VERIFICATION_MATRIX.yaml`
- `AI_SPEC/36_V2_INVARIANTS.yaml`
- `docs/prompts/10-v2-command-composer.prompt.md` through `16-v2-privacy-testing.prompt.md`
- `docs/UPDATE_GUIDE.md`

## 3.0 delivery status

- Current Android version: `3.0.0-alpha.5`.
- Current Debug and debug-signed Release build artifacts are in `artifacts/deliverables/`; checksums are in `SHA256SUMS.txt`.
- The embedded Agent tab (OpenMinis) drives TeachTrack through the `teachtrack` sandbox CLI → Native Offload bridge → 69-action Copilot Action Registry (see `docs/agent/TEACHTRACK_CAPABILITY_MATRIX.md`). Access mode (Owner Trusted / standard), integration status page, and audit + undo live in Settings → TeachTrack Integration.
- Latest OnePlus instrumentation result: `OK (52 tests)` on OnePlus Ace 5 (PKG110, Android 16) — database + agent actions + literal PRoot sandbox e2e (`artifacts/oneplus-ace5-androidtest-20260916.txt`); tablet (OPD2101) rendering verified via uiautomator/screencap artifacts.
- Real provider OAuth approval and Release signing require external credentials/material.
