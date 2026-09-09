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

## Continuation

- Added a best-effort `WorkspaceSyncCoordinator`; local writes remain successful if the workspace is disconnected, while connected writes journal CREATE/UPDATE/DELETE events.
- Added conflict data model and conflict-file writer primitives.
- Added Attachment SHA-256 and byte-size calculation, plus Room migration 6→7.
- Added `.ttbackup` restore flow in Settings: select archive, select new target folder, unpack, import and report issues.
- OnePlus second regression: `OK (4 tests)` after the 6→7 migration.
- OnePlus screenshot: `artifacts/screenshots/oneplus-v2/v3-settings.png`.
- Added Workspace Health Check, DELETE tombstone files, and portable attachment byte copying using relative paths.
- Final OnePlus regression after these changes: `OK (4 tests)`.
- Added recursive SyncEvent scanner with duplicate-event suppression, revision mismatch detection, checkpoints, and conflict file generation.
- Added Settings actions to scan events, inspect conflict summaries, and record VERSION_A / VERSION_B / MANUAL_MERGE decisions without silent overwrite.
- Rebuilt and reinstalled debug + androidTest APK on OnePlus `92f73a16`; second regression: `OK (4 tests)`.
- OnePlus visual check: `artifacts/screenshots/oneplus-v2/sync-settings-workspace2.png` confirms the workspace, health check, event scan, and conflict actions are visible and usable.
- Hardened `.ttbackup`: archive creation now recursively includes workspace files, sync metadata, device records, and binary attachments; restore writes raw bytes instead of UTF-8 text conversion.
- Added `WorkspaceStorage.readBytes` and guarded SAF reads so directories or temporarily unavailable nodes do not crash scanning/archiving.
- OnePlus regression after archive hardening: `OK (4 tests)`; installation succeeded and existing seed-backed local data remained available.
- Wrapped Room backup/workspace imports in `RoomDatabase.withTransaction`; partial restore failures now roll back instead of leaving a half-imported database.
- OnePlus regression after transactional import change: `OK (4 tests)`.
- Workspace Health Check now validates attachment relative paths and SHA-256 hashes, reporting missing or corrupted binary files without crashing.
- OnePlus regression after attachment health validation: `OK (4 tests)`.
- Added OAuth provider catalog with clickable OpenAI/GPT, Kimi/Moonshot, DeepSeek, 智谱 GLM, and custom OAuth 2.0/PKCE choices.
- Common providers that officially use API Keys now open their official console instead of pretending ChatGPT/Kimi web login returns an API token; custom OAuth keeps browser authorization-code + PKCE callback support.
- OnePlus visual check: OAuth provider selector is visible in Settings; final device regression: `OK (4 tests)`.
- Simplified OAuth UI per product feedback: removed visible endpoint, token endpoint, client ID, scope, and provider text inputs from the active card. It is now a single provider dropdown plus one browser/console action and session status.
- Cleared stale failed-login status from the simplified card and migrated unknown legacy selection display to the first common provider option.
- OnePlus screenshot: `artifacts/screenshots/oneplus-v2/oauth-simple-final.png`; final regression after simplification: `OK (4 tests)`.

## Known limitations kept explicit

- Conflict decisions are persisted as resolution records; applying an arbitrary event payload into Room is intentionally not automatic yet.
- Workspace import/export is the supported data exchange path; the Room database is never copied as a shared file.
