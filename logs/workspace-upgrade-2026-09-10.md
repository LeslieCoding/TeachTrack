# TeachTrack 3.0 Workspace Upgrade Log

## Implemented

- Bumped Android line to `3.0.0-alpha.1` / version code `300001`.
- Added `workspace.json` validation with permanent workspace ID.
- Added SAF tree URI attach/create flow and persisted URI permission.
- Added standard `data/*.jsonl` export/import for current Room entities.
- Added `.teachtrack/workspace.id`, `schema.version`, `state.json`.
- Added generated device UUID and `sync/devices/<deviceId>.json`.
- Added JSONL issue collection for malformed individual lines.
- Added idempotent import through existing permanent entity IDs.
- Added basic `SyncEngine` event and checkpoint writers.
- Added `.ttbackup` ZIP writer and safe path-checked restore core.
- Kept historical seed outside APK as `seed/teacher_seed_v1.json`.

## Verification

- `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest`: passed.
- OnePlus `92f73a16` debug APK install: passed.
- OnePlus workspace creation: passed.
- OnePlus folder evidence: 29 `students.jsonl` lines, 30 `courses.jsonl` lines, all declared data files, `workspace.json`, `.teachtrack`, and device record present.
- Screenshot: `artifacts/screenshots/oneplus-v2/workspace-confirm.png`.

## Remaining

Full per-entity sync event emission, conflict editor, attachment copy/hash verification, restore UI, and second-device simulation remain in the next execution slice. No Room database file is used as a shared workspace file.
