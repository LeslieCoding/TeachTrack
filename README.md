# TeachTrack / 师迹

TeachTrack is a local-first Android workspace for a teacher managing long-term student growth records.

## Current version

`TeachTrack 2.0 · TeachTrack Copilot` — Android `2.0.0-alpha.1`.

The 1.x local-first MVP remains usable without an account, cloud service, network, or AI. Version 2 adds a planned optional natural-language command layer: typed or voice input is interpreted by a configurable provider and converted into validated local actions.

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
