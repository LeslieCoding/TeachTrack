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
- Added separate `Kimi Code` and `Claude / Anthropic` entries to the provider dropdown. Kimi Code opens `kimi.com/code/en`, while ordinary Kimi remains on the Moonshot platform console; Claude opens Anthropic Console.
- OnePlus verification opened the Kimi Code official page successfully; final regression after provider additions: `OK (4 tests)`.

## Known limitations kept explicit

- Conflict decisions are persisted as resolution records; applying an arbitrary event payload into Room is intentionally not automatic yet.
- Workspace import/export is the supported data exchange path; the Room database is never copied as a shared file.

## 2026-09-10 OAuth correction (OpenMinis-aligned)

- Rechecked OpenMinis Android auth structure before changing TeachTrack. OpenMinis uses provider-specific OAuth managers instead of treating every provider console as OAuth.
- Separated `oauthPresets` from `apiPresets`; the active OAuth selector now contains only account authorization entries: OpenAI/ChatGPT Codex, Claude account, and Kimi Code.
- Removed the previous semantic error where selecting an API-key provider from the OAuth card opened its API console. OAuth UI no longer exposes API-console actions.
- Added provider-specific Kimi Code RFC 8628 device authorization: request device code, open verification URL, poll token endpoint, handle pending/slow_down/denied/expired states, and store the returned token in Android Keystore-backed storage.
- Added provider-specific PKCE metadata for OpenAI Codex and Claude account login, including client IDs, scopes, authorization endpoints, token endpoints, state validation, code exchange, and encrypted token storage.
- Added a visible explanation in Settings that account OAuth and API Key are independent credential paths.
- Verification: `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` passed; debug APK installed on OnePlus `92f73a16`.
- OnePlus visual evidence: `artifacts/screenshots/oneplus-v2/oauth-separated-settings.png`, `oauth-separated-menu.png`.

## Limitations / next auth work

- OpenAI Codex and Claude browser callbacks currently use TeachTrack's app callback and still need provider-compatible local callback handling verification on a real account; no real credentials were used.
- API Key entry remains in the separate Provider card and is not mixed into the OAuth card.

## 2026-09-10 Cloud Provider routing correction

- Moved the API-provider selector into the active `ProviderSettingsCard`; the previous insertion had landed in an unused legacy OAuth composable.
- The Provider selector now exposes OpenAI/GPT API, Kimi/Moonshot API, Kimi Code API, Claude/Anthropic API, DeepSeek API and Zhipu GLM API.
- `ProviderConfig` now persists stable `providerId` and `authMode`. The generic OpenAI-compatible gateway rejects non-API-key routing with `AUTH_MODE_MISMATCH`, so OAuth account tokens are not treated as API keys.
- API keys remain Android Keystore-backed; provider metadata remains local preferences and is not put into Room, workspace exports, logs or screenshots.
- OnePlus UI inspection confirmed the selector and six entries. Build, unit tests and instrumentation passed (`OK (4 tests)`). Evidence: `artifacts/screenshots/oneplus-v2/provider-dropdown-visible.png`, `provider-dropdown-menu.png`.

## 2026-09-10 OAuth command routing completion

- Root cause of the user's endpoint/model/API Key message: `ConfigurableCopilotProvider` always routed Copilot interpretation to the API-key gateway, regardless of a completed OAuth login.
- Added encrypted OAuth access-token reading without exposing the token to Room, logs or UI.
- Added a Kimi Code OAuth chat-completions adapter using the Kimi Code endpoint and default model `kimi-for-coding`; it never reads the API-key keystore.
- Added routing selection for a logged-in Kimi Code account, while preserving the generic API-key path for API Provider mode.
- OnePlus live command verification completed: `识别并预览` returned `已由 Kimi Code OAuth 解析为可确认命令` and produced a `students.add` action. The UI no longer displayed the endpoint/model/API Key configuration error.
- During verification Kimi returned the precise 400 cause `invalid temperature: only 1 is allowed for this model`; the Kimi OAuth adapter now sends `temperature=1`.
- Final build and OnePlus instrumentation passed: `OK (4 tests)`.

## 2026-09-10 Kimi Code login status fix

- Root cause: the Kimi device-flow coroutine wrote progress/success into `OAuthPreferences`, but the Compose card kept a separate in-memory status initialized to the old text. Returning from the browser therefore continued to show “正在申请设备授权码” even after the token was saved.
- Fixed by initializing the card from persisted OAuth status, refreshing the status on Activity `ON_RESUME`, and handling coroutine success/failure explicitly.
- Kimi success now shows “Kimi Code 登录成功，账号令牌已安全保存”; failed/expired/device-denied flows surface an actionable failure message.
- Rebuilt, reinstalled and reran OnePlus instrumentation: `OK (4 tests)`.

## T3-HARDEN-001 - Architecture, performance and stability increment

### What changed

- Inspected the Room DAO/repository boundary and found that Student Workspace one-shot course and score reads loaded every course/score before Kotlin filtering.
- Added SQL-scoped suspend queries for `studentId`, retaining deterministic history ordering.
- Updated the repository to use those queries.

### Why

- Reduces Room cursor transfer, Kotlin allocations, and peak memory when the workspace contains many students and historical records.
- Keeps the existing schema, indexes, Local First behavior, backup format, and OAuth/provider boundaries unchanged.

### Verification plan/result

- Required: `testDebugUnitTest`, debug APK assembly, Android test APK assembly, OnePlus installation/instrumentation, and visual smoke check.
- Device scope: OnePlus serial `92f73a16` only. OPPO Pad remains untouched.

### Remaining issues

- Multi-entity save/import transaction boundaries need a separate low-risk pass.
- Dashboard-wide history queries should be reviewed for bounded projections before larger datasets are used.
- Automatic per-entity sync event emission and full second-device simulation remain incomplete.

### Follow-up stability changes

- Wrapped course save plus generated lesson-plan/problem records in one Room transaction when the production database is available.
- Wrapped exam/score/optional score-item save in one Room transaction.
- Changed attachment deletion to a revisioned tombstone (`deletedAt`) instead of physical deletion, preserving cross-device deletion semantics.
- Build and JVM tests passed. OnePlus physical rerun was attempted but blocked because serial `92f73a16` disconnected; only OPPO serial `2d64f765` remained visible, and it was intentionally not used.

## T3-HARDEN-002 - Dashboard bounded data source preparation

- Added Room-backed dashboard course and score projections capped at the latest 200 records.
- Kept the existing unbounded streams for full planning/analytics/history views; dashboard wiring remains the next safe UI integration step so historical analytics semantics are not changed accidentally.
- Build and JVM tests passed; physical verification remains blocked until OnePlus `92f73a16` reconnects.

### OnePlus physical verification resumed

- OnePlus `92f73a16` reconnected and was the only device used.
- Latest Debug APK installed successfully.
- App launch smoke check passed.
- Android instrumentation passed: `OK (4 tests)`.
- Screen evidence: `artifacts/screenshots/oneplus-v2/hardening-reconnected.png`.
- UI hierarchy evidence: `artifacts/teachtrack-hardening-reconnected-ui.xml`.

## 2026-09-11 - Copilot command execution hardening

- Root cause confirmed: the offline parser contained mojibake Chinese keywords, so normal Chinese requests could not be classified reliably.
- Replaced the parser with encoding-stable Unicode matching for student, score, task, course, communication, homework and lesson-plan requests.
- Added extraction for student name, score, full score, grade and subject, while retaining selected-student context.
- Added provider action aliases (`students.add`, `scores.add`, `tasks.add`, etc.) and normalize them before validation/execution so cloud output can reach the existing local action registry.
- Student-dependent actions can now resolve a selected student or an exact local student name before writing.
- Expanded the cloud planner contract with the actual allowed action catalog and parameter names.
- OAuth routing is now explicit: Kimi OAuth is used only when auth mode is `OAUTH_DEVICE`; other OAuth sessions no longer fall through to an API-Key configuration error.
- Build and unit tests passed. OnePlus physical install was attempted but serial `92f73a16` disconnected and no ADB device remained; no other device was used.

### Copilot parser regression fix

- Added tests for Chinese task recognition, student/score/full-score extraction, and provider action aliases.
- Fixed Chinese-name extraction so `给学生王晨然录入成绩 126 分，满分 160` does not absorb following command text into the student name.
- JVM test suite passed after the fix.

## 2026-09-11 - Copilot query action increment

- Added database-side count queries for active students, pending tasks, and knowledge points.
- Connected `dashboard.query` and `knowledge.query` to real repository data instead of placeholder success messages.
- The AI action boundary remains local and validated; no model can issue raw SQL or bypass confirmation for writes.
- `testDebugUnitTest` and `assembleDebug` passed.

## 2026-09-11 - AI write safety and OnePlus validation

- Added pre-write checks preventing missing scores from silently becoming `0`, and preventing blank course/homework/communication content from being written.
- OnePlus `92f73a16` reconnected; current Debug APK installed successfully.
- Android instrumentation passed: `OK (4 tests)`.
- Copilot validation screenshot: `artifacts/screenshots/oneplus-v2/copilot-validation.png`.

## 2026-09-11 - Copilot query result and clarification increment

- Student search now returns matched student names instead of only a count.
- Score query now returns the latest score/full-score pairs instead of only a record count.
- Existing required-field guards prevent incomplete writes from being silently accepted.
- OnePlus `92f73a16` installed the latest Debug APK and passed Android instrumentation: `OK (4 tests)`.
- Screenshot: `artifacts/screenshots/oneplus-v2/copilot-query-result.png`.

## 2026-09-11 - Copilot latest query regression

- Student search now returns up to five matched names.
- Score query now returns up to five recent score/full-score pairs.
- Latest Debug APK installed on OnePlus `92f73a16`.
- Android instrumentation passed: `OK (4 tests)`.
- Screenshot: `artifacts/screenshots/oneplus-v2/copilot-query-latest.png`.

## 2026-09-11 - Copilot full-operation contract increment

- Revalidated the full path: provider interpretation, action normalization, confirmation gate, local registry execution, and repository writes.
- Added exam creation and score query intent classification to the offline fallback.
- Added Chinese/English student-name context extraction for phrases such as “给学生王晨然录入成绩”.
- Expanded cloud system instructions with the concrete TeachTrack action catalog and parameter contract, reducing invalid provider actions.
- Provider aliases are normalized before validation and execution; destructive/unknown actions remain rejected.
- Test additions cover Chinese task parsing, student/score/full-score extraction and action aliases.
- `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` passed.
- OnePlus physical test was not run in this increment because ADB reported no connected devices; OPPO was not used.
