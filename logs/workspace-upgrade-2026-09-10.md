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

## 2026-09-11 - Copilot common-language regression coverage

- Added regression coverage for common Chinese requests: create student, record course, create task, and search student.
- All covered requests map to registered local actions; the suite also verifies score extraction and provider action aliases.
- `testDebugUnitTest` passed with 16 tests.

## 2026-09-11 - Knowledge-point Copilot operation

- Added exact-name knowledge-point lookup in the DAO/repository boundary.
- Natural language such as “把知识点动态电路标记为需巩固” now maps to `knowledge.update`, extracts level `2`, resolves the local knowledge-point ID, and writes StudentKnowledgeProgress after confirmation.
- Added regression coverage for knowledge-point intent and mastery level extraction.
- Latest APK installed on OnePlus `92f73a16`; Android instrumentation passed `OK (4 tests)`.
- Screenshot: `artifacts/screenshots/oneplus-v2/knowledge-command-latest.png`.

## 2026-09-11 - Copilot analytics query increment

- Added real local count queries for courses, exams, and scores.
- `analytics.query` now returns actual repository totals instead of a placeholder message.
- Added knowledge-point lookup and operation coverage remains intact.
- Latest Debug APK installed on OnePlus `92f73a16`; Android instrumentation passed `OK (4 tests)`.
- Screenshot: `artifacts/screenshots/oneplus-v2/analytics-command-latest.png`.

## 2026-09-11 - Copilot score/exam association

- Added exact-name Exam lookup at the DAO/repository boundary.
- Copilot score creation now reuses an existing exam with the same name and only creates a new exam when none exists, preventing duplicate exam records.
- Score writes continue to require a valid numeric score and use the existing transactional repository path.
- OnePlus `92f73a16` installed the latest APK and passed Android instrumentation: `OK (4 tests)`.
- Screenshot: `artifacts/screenshots/oneplus-v2/score-association-latest.png`.

## 2026-09-11 - Offline Copilot module coverage

- Extended offline intent classification to problem records, templates, tags, attachment queries and Dashboard queries.
- Added regression assertions for problem, tag and Dashboard requests; all map to registered local actions.
- Latest Debug APK installed on OnePlus `92f73a16`.
- Android instrumentation passed: `OK (5 tests)`.
- Screenshot: `artifacts/screenshots/oneplus-v2/copilot-module-coverage.png`.

## 2026-09-11 - Backup round-trip consistency test

- Added Android instrumentation coverage for Room → backup JSON → import into the same database.
- Verified student/course counts remain unchanged and permanent IDs are preserved; repeated import is idempotent.
- Both Debug and AndroidTest APKs installed on OnePlus `92f73a16`.
- Instrumentation passed: `OK (5 tests)`.

## 2026-09-11 - Copilot full-operation contract increment

- Revalidated the full path: provider interpretation, action normalization, confirmation gate, local registry execution, and repository writes.
- Added exam creation and score query intent classification to the offline fallback.
- Added Chinese/English student-name context extraction for phrases such as “给学生王晨然录入成绩”.
- Expanded cloud system instructions with the concrete TeachTrack action catalog and parameter contract, reducing invalid provider actions.
- Provider aliases are normalized before validation and execution; destructive/unknown actions remain rejected.
- Test additions cover Chinese task parsing, student/score/full-score extraction and action aliases.
- `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` passed.
- OnePlus physical test was not run in this increment because ADB reported no connected devices; OPPO was not used.

## 2026-09-11 - Copilot task action regression fix

- Root cause: `task.create` was recognized by the offline intent parser but was missing from `LocalCopilotActionRegistry.allowedActions`, so the service rejected the command before reaching the Room write path.
- Added `task.create` to the local action allow-list. The existing repository task write path is now reachable through confirmed Copilot commands.
- Rebuilt Debug and AndroidTest APKs successfully.
- Installed both APKs on OnePlus `92f73a16` only.
- Android instrumentation passed: `OK (6 tests)`, including the end-to-end natural-language flow for creating a student and adding a task for that student.
- Problem solved: a valid task command no longer reports `此操作未被允许：task.create`.

## 2026-09-11 - Dashboard bounded query layer

- Added dedicated Room dashboard projections for exams, homework, and communication records, each capped at the latest 200 rows.
- Added repository and ViewModel dashboard streams so the UI can consume bounded data without changing detail-page streams.
- Existing course and score dashboard projections remain capped at 200 rows; full streams remain available only to their respective management screens.
- Build verification passed through Kotlin compilation and Debug assembly; the bounded streams are isolated from existing student detail queries.
- Follow-up remains: switch the Dashboard composable itself to the new bounded streams and add a query-bound regression test.

## 2026-09-11 - Automatic SyncEvent coverage expansion

- Extended repository write methods for exams, lesson plans, problem records, knowledge points, knowledge progress, parent contacts, and templates to emit SyncEvents after successful local writes.
- Progress events use a stable composite entity key (`studentId:knowledgePointId`) because the current progress table is keyed by that relationship.
- Existing transaction boundaries for course and score writes were preserved; no destructive migration or database replacement was introduced.
- `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` passed.
- Physical-device installation was deferred for this source-only increment; the previous OnePlus instrumentation baseline remains `OK (6 tests)`.

## 2026-09-11 - Dashboard bounded streams connected

- Changed the Dashboard-facing ViewModel streams for courses, scores, exams, homework, and communications to use the dedicated latest-200 Room projections.
- Student detail screens continue to use student-scoped history streams; no historical student data is truncated at the source.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Full Android instrumentation passed: `OK (6 tests)`.

## 2026-09-11 - Sync conflict regression coverage

- Added an Android instrumentation test for the two-device revision rule: equal base/current revisions merge, divergent revisions are detected as conflicts.
- Rebuilt and installed both APKs on OnePlus `92f73a16`.
- Instrumentation suite expanded to 7 tests and passed: `OK (7 tests)`.

## 2026-09-11 - Build deliverables audit

- Confirmed only OnePlus `92f73a16` is currently connected; OPPO Pad is not connected and was not touched.
- Release build completed successfully with the existing unsigned release configuration.
- Copied deliverables to `artifacts/deliverables/`:
  - `TeachTrack-3.0.0-alpha.1-debug.apk`
  - `TeachTrack-3.0.0-alpha.1-release-unsigned.apk`
- Release signing remains an external prerequisite and is recorded in `AI_SPEC/27_KNOWN_ISSUES.md`; no signing key was invented or stored.

## 2026-09-11 - Delivery state audit

- Rechecked `docs/prompts/00-09`, AI_SPEC acceptance records, hardening backlog, current Git history, and connected Android devices.
- Current connected device is OnePlus `92f73a16`; OPPO Pad `2d64f765` is absent from ADB and was not operated.
- Updated project state to mark Dashboard bounded projections and current repository SyncEvent coverage as completed, while leaving revision unification, full conflict resolution simulation, expanded corruption testing, and second full physical workflow explicitly open.
- No completion claim was made for OPPO regression because the device is not currently available.

## 2026-09-11 - Backup corruption regression

- Added an instrumentation test proving malformed backup JSON is rejected and Room transaction rollback prevents partial import.
- Rebuilt Debug/AndroidTest APKs and installed them on OnePlus `92f73a16`.
- Full instrumentation suite passed: `OK (8 tests)`.

## 2026-09-11 - Persistent sync revision sequencing

- Changed `WorkspaceSyncCoordinator` from fixed `baseRevision=0/revision=1` events to per-workspace/entity persistent revision sequencing in Android preferences.
- Each automatic event now uses the previous revision as its base and increments the entity revision, while preserving best-effort behavior when no workspace is connected.
- Rebuilt Debug/AndroidTest APKs, installed them on OnePlus `92f73a16`, and ran the complete instrumentation suite.
- Result: `OK (8 tests)`.

## 2026-09-11 - Workspace health and attachment integrity regression

- Added a memory-backed Workspace health test covering a malformed JSONL line and a missing attachment reference.
- Verified health checking continues through invalid records and reports integrity issues instead of failing the entire workspace.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Full instrumentation suite passed: `OK (9 tests)`.

## 2026-09-11 - Idempotent conflict file handling

- Sync scanner now derives conflict filenames from the source event ID and avoids rewriting/counting an already persisted conflict file on repeated scans.
- Added a two-event repeated-scan regression test using an in-memory Workspace storage.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Full instrumentation suite passed: `OK (10 tests)`.

## 2026-09-11 - Tombstone deletion regression

- Added a sync-engine regression test proving DELETE events write a persistent tombstone with entity ID and revision.
- This protects offline devices from resurrecting deleted records during later workspace scans.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Full instrumentation suite passed: `OK (11 tests)`.

## 2026-09-11 - Dashboard projection limit regression

- Added a database-level test inserting 251 courses and verifying `observeDashboardCourses()` returns exactly 200 rows.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Full instrumentation suite passed: `OK (12 tests)`.

## 2026-09-11 - CSV export escaping regression

- Added a score CSV test covering commas in student names and quotes in exam names.
- Verified CSV quoting/doubling behavior preserves export structure.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Full instrumentation suite passed: `OK (13 tests)`.

## 2026-09-11 - Delivery evidence count synchronization

- Updated the delivery status matrix and regression-round document from 11 to 13 passing instrumentation tests.
- No source behavior changed in this documentation-only update.

## 2026-09-11 - Verification record consistency

- Updated current-state AI_SPEC references that still reported the older 4-test OnePlus baseline to the verified 13-test baseline.
- Historical earlier-round counts remain unchanged where they describe the original test run.

## 2026-09-11 - OnePlus launch smoke check

- Force-stopped and relaunched the installed Debug APK on OnePlus `92f73a16`.
- Process remained alive (`pidof com.leslie.teachtrack` returned a PID).
- UI hierarchy contained the expected greeting, Dashboard heading, empty-state content, navigation labels, and Copilot entry.
- Captured UI hierarchy at `artifacts/oneplus-final-smoke-ui.xml`; this is device evidence and is intentionally not treated as OPPO evidence.

## 2026-09-11 - OnePlus target resumed

- User confirmed OnePlus is the active test target; OPPO Pad is not used in this run.
- OnePlus `92f73a16` was detected, Debug and AndroidTest APK installation both returned `Success`.
- Full instrumentation completed successfully: `OK (13 tests)`.

## 2026-09-11 - AI module regression

- Ran all JVM Copilot tests through `testDebugUnitTest`, including command contract parsing, action validation, provider gateway fallback/configuration, OAuth configuration normalization, and provider/OAuth separation.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Ran the complete Android instrumentation suite: `OK (13 tests)`.
- Verified the local AI path remains functional: natural-language interpretation → confirmation gate → local action registry → Room write.
- Real cloud provider calls and vendor OAuth completion remain intentionally unclaimed because no external credentials/client registration were supplied.

## 2026-09-11 - Development monitor repair

- Corrected `scripts/dev-monitor.ps1` to use the actual local ADB path `D:\Leslie\Tools\platform-tools\adb.exe`.
- Added `-Once` mode for safe validation without waiting 30 minutes.
- Executed one-shot validation successfully: network heartbeat was `True`, target OnePlus state was `device`.

## 2026-09-11 - Regression round 3 record

- Added `docs/testing-round-3.md` as the current consolidated test record.
- It records build, installation, OnePlus instrumentation evidence, delivered APKs, hashes, and explicit OPPO/signing/provider limitations.

## 2026-09-11 - Final local artifact refresh

- Rebuilt both Debug and Release variants after the latest sync changes.
- Refreshed `artifacts/deliverables/` with the current APK binaries.
- Added `docs/delivery-status-2026-09-11.md` to distinguish verified local scope from external prerequisites.
- The connected-device check still shows only OnePlus `92f73a16`; OPPO Pad remains unavailable for a new regression run.

## 2026-09-11 - Final APK refresh and checksums

- Rebuilt `assembleDebug` and `assembleRelease` after the latest sync/test changes.
- Refreshed both APKs in `artifacts/deliverables/` and calculated SHA-256 checksums for handoff integrity.
- OPPO Pad remains absent from ADB; no OPPO installation or screenshot claim was made.

## 2026-09-11 - Full verification gate

- `git diff --check` passed.
- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, and `assembleRelease` passed.
- Latest Debug and AndroidTest APKs installed on OnePlus `92f73a16`.
- Full physical instrumentation passed: `OK (11 tests)`.

## 2026-09-11 - Delivery checksum manifest

- Added `artifacts/deliverables/SHA256SUMS.txt` for the two delivered APKs.
- Recomputed both hashes and confirmed they match the manifest.
- `git diff --check` passed.
- Device status remains OnePlus `92f73a16` only; OPPO Pad is still unavailable.

## 2026-09-11 - Sync revision concurrency guard

- Serialized `WorkspaceSyncCoordinator.record()` so concurrent local writes cannot allocate duplicate revisions for the same device process.
- Rebuilt and installed Debug/AndroidTest APKs on OnePlus `92f73a16`.
- Full instrumentation suite passed: `OK (11 tests)`.

## 2026-09-11 - Copilot intent precedence audit

- Reviewed the remaining uncommitted Copilot parser change: task phrases are intentionally evaluated before generic student-create phrases, so commands such as “给学生添加待办” cannot be misclassified as student creation.
- Revalidated the change through the existing end-to-end test suite; no unrelated working-tree files were staged.

## 2026-09-11 - OAuth account/provider handoff fix

- Investigated the reported OAuth failure against the OnePlus device state and source flow.
- Root cause: Kimi Code OAuth could save an encrypted account token while the separate cloud Provider preference remained in `API_KEY` mode. Copilot then ignored the OAuth session and asked for endpoint/model/API key.
- Updated successful Kimi Code device authorization to activate the Kimi Code OAuth adapter, enable the provider, set `providerId=kimi_code`, set `authMode=OAUTH_DEVICE`, and apply `kimi-for-coding` only when no model was already selected.
- Verified with `testDebugUnitTest` and installed the rebuilt Debug APK on OnePlus `92f73a16`; install returned `Success`.
- Limitation recorded: OpenAI/Claude account OAuth cannot be declared operational without provider-issued client registration and a real account/browser approval.

## 2026-09-11 - External browser launch fix

- Investigated the OnePlus message reporting that no Activity handled the official-console intent.
- Cause: settings screens receive the application context, while several URL buttons called `startActivity()` without `FLAG_ACTIVITY_NEW_TASK`.
- Added a shared external URL launcher with the required task flag and `resolveActivity` check. OAuth/API console links now either open the browser or show a clear browser-unavailable status.
- `testDebugUnitTest` and `assembleDebug` passed; rebuilt Debug APK installed successfully on OnePlus `92f73a16`.

## 2026-09-11 - Browser absence fallback

- Direct OnePlus package resolution for an HTTPS URL returned `No activity found`; the device currently has no browser handler installed.
- Added a non-exported in-app `ExternalWebActivity` WebView fallback with JavaScript/DOM storage enabled for official login pages. OAuth custom-scheme callbacks are forwarded to the app.
- Rebuilt and installed Debug APK successfully on OnePlus `92f73a16`; `testDebugUnitTest` passed.

## 2026-09-11 - OAuth entrypoint fallback completion

- The first fallback covered official-console links, but OAuth authorization itself still called the system browser directly.
- Updated both PKCE authorization and Kimi device verification URL launches to use the same external-browser-or-in-app-WebView fallback.
- Rebuilt, ran unit tests, and installed Debug APK successfully on OnePlus `92f73a16`.

## 2026-09-11 - OnePlus runtime verification

- Tapped the Kimi official-page action on the installed APK; ActivityManager showed `ExternalWebActivity` resumed over `MainActivity`.
- UIAutomator confirmed the foreground view is an enabled `android.webkit.WebView`.
- Renamed the Kimi device-flow action to `开始 Kimi OAuth 登录` to distinguish it from opening an API console.
- Rebuilt, passed unit tests, and installed the latest Debug APK on OnePlus `92f73a16`.

## 2026-09-11 - Full OnePlus regression after OAuth fixes

- Rebuilt `assembleDebugAndroidTest` and installed both application/test APKs on OnePlus `92f73a16`.
- Force-stopped both packages before execution to remove stale OAuth/WebView task state.
- Instrumentation result: `OK (13 tests)`.
- Coverage included Dashboard projection bounds, backup round trip, sync revision/conflict/tombstone behavior, local Copilot write path, workspace corruption handling, performance data volume, CSV escaping, and Compose dashboard smoke.
- OPPO Pad remains disconnected; no current OPPO evidence was created or claimed.

## 2026-09-11 - Final dashboard visual evidence

- Launched the installed Debug APK on OnePlus `92f73a16` and captured `artifacts/screenshots/oneplus-v2/final-dashboard-2026-09-11.png` using `adb pull` to preserve a valid PNG.
- Visual check confirms the green dashboard, greeting, quick actions, 41 local students, bottom navigation, and Copilot floating entry are rendered without clipping on the phone viewport.
- UIAutomator evidence was refreshed at `artifacts/oneplus-final-smoke-ui.xml`.

## 2026-09-11 - Release artifact refresh

- Ran `testDebugUnitTest` and `assembleRelease` after all OAuth/WebView changes; build completed successfully.
- Replaced the stale unsigned Release APK in `artifacts/deliverables/` with the current build.
- Updated `SHA256SUMS.txt`: Release unsigned SHA-256 is `E5AC0B165A67399D28073D3F18E680F351766EC6B19FF92E230B943A8A744EF0`.
- Release signing remains intentionally unavailable because no signing key/material was supplied.

## 2026-09-11 - Kimi OAuth device-flow entry regression

- On OnePlus, opened Settings and confirmed the selected provider is Kimi Code.
- The UI displayed a real device authorization code and the polling status (`App 正在轮询`), proving the request reached the device-flow stage.
- Tapping `开始 Kimi OAuth 登录` brought `ExternalWebActivity` to the foreground over `MainActivity`; this confirms the Kimi verification URL uses the in-app fallback on the browserless device.
- No account approval was performed, so no token/session success was claimed.

## 2026-09-11 - API 24 lint hardening and regression

- Full `gradlew check` initially exposed 11 API-level errors from direct `java.time.Instant.now()` calls in Workspace/sync code while minSdk is 24.
- Replaced those calls with an API-24-compatible UTC ISO-8601 timestamp utility; portable data format remains unchanged.
- Re-ran `gradlew check`: `BUILD SUCCESSFUL`.
- Rebuilt Debug, AndroidTest, and unsigned Release APKs; refreshed SHA256 sums.
- Installed refreshed Debug and AndroidTest APKs on OnePlus `92f73a16`.
- Instrumentation result after the hardening change: `OK (13 tests)`.

## 2026-09-11 - Lint warning hardening and final regression

- Added HTTPS intent visibility declaration for browser detection.
- Added Android 12+ data extraction rules that exclude private local data from cloud/device backup.
- Removed explicit activity orientation declarations to preserve phone/tablet and portrait/landscape behavior.
- Added adaptive launcher monochrome layer.
- Explicitly reviewed/suppressed the intentional WebView JavaScript lint warning at the narrow activity boundary.
- `gradlew check`: `BUILD SUCCESSFUL`.
- Rebuilt and installed Debug/AndroidTest; OnePlus instrumentation: `OK (13 tests)`.
- Refreshed Debug/Release unsigned APKs and SHA256 sums.

## 2026-09-11 - OAuth page content inspection

- UIAutomator inspected the foreground WebView, not only the Activity stack.
- The page loaded successfully with title `Kimi AI 官网 - K3 上线，专为智能体编程与知识工作打造` and displayed Kimi's phone-number login form, verification-code field, consent checkbox, and login button.
- This proves network/page rendering is working. The remaining step is an actual Kimi account login/consent, which cannot be completed or fabricated by the local test runner.

## 2026-09-11 - AI Natural Language Control Runtime

Task: T3-AI-RUNTIME-001 / V2-REQ-004, V2-REQ-005

What changed:

- Added structured `CopilotEffect` results and connected the Copilot composer to the main app shell. Successful commands now open the requested tab or student workspace and can start quick-resume/score capture.
- Rebuilt `LocalCopilotActionRegistry` around local repository operations for students, courses, exams, scores, homework, communication, lesson plans, tasks, knowledge progress, problems, dashboard, analytics, and navigation.
- Added `AiCommandRuntime.kt` with typed AppCommand boundary, risk levels, command specs, validation, conservative entity resolution, and command plans.
- Added multi-action planning for course requests containing knowledge reinforcement, homework, and next-lesson instructions. Execution stops on the first failed sub-command and reports actual results.
- Added audit callback wiring. Audit stores action/status metadata only and does not persist raw student parameters.
- Expanded provider system schema and offline parser for score labels, module navigation, student aliases, and structured content fields.

Problem solved:

The previous implementation often returned a textual confirmation without causing the corresponding TeachTrack screen or data workflow to update. The runtime now keeps model planning separate from local command execution and returns explicit execution effects.

Verification:

- `:app:testDebugUnitTest`: 21 passed.
- `:app:assembleDebug`: passed.
- OnePlus `92f73a16`: `:app:connectedDebugAndroidTest`, 13 passed.
- Debug APK installed successfully on the OnePlus test device.

Known limits:

- Real Kimi/OAuth authorization still depends on the user's external account approval.
- The current cloud envelope is one JSON command; the fully remote multi-tool loop can be added later, while the offline/local planner already supports the ordered plan path.
- No OPPO Pad claim is made; it was not the selected device for this run.

## 2026-09-12 - Cross-module student-data organization

Task: `T3-AI-RUNTIME-002`

- Implemented `student.data.organize.preview` as a local, Room-backed cross-module report. It groups real findings by student: no course record or next lesson plan, pending homework/tasks, open problem records, and recorded weak knowledge progress.
- The command is read-only by design. It does not infer school, family information, grades, or any other unknown field; it does not bulk-edit historical records.
- Added `knowledge.weakness.query`, cloud-planner policy for both API and Kimi OAuth routes, immediate execution for read-only commands, and explicit ambiguity feedback for student-name resolution.
- JVM: `:app:testDebugUnitTest` and `:app:assembleDebug` passed.
- OnePlus `92f73a16`: instrumentation report recorded 14 tests, 0 failures, 0 errors; the new Room end-to-end test proved the organization command leaves students/courses/homework/tasks unchanged.
- Direct post-test ADB deployment did not complete because Android 16 ADB package installation hung after the test runner cleanup. No package-install UI was shown and no false deployment success is recorded.

## 2026-09-12 - Explicit organization follow-up action

- Added `student.followup.tasks.create`: only an explicit request to turn organization findings into follow-up tasks can select it; it is confirmation-gated.
- On confirmation, it creates at most one open task per affected student, skips an existing matching task, and uses the normal repository/sync-event path.
- JVM build and unit suite passed after the change. A OnePlus UTP rerun stalled before result output and was stopped automatically; the new write-specific device test is deliberately recorded as pending.
- The newly built Debug app was then silently installed and started with ADB on OnePlus (process ID `22060`). No manual installation or user input was requested.
