# TeachTrack Development Log — 2026-09-09 — V2 planning and OPPO Pad handoff

## Scope

This round converted the requested major-version idea into an auditable TeachTrack 2.0 specification and restored OPPO Pad as the only active physical-test target.

## Implemented in this round

1. Renamed the product line to `TeachTrack 2.0 · TeachTrack Copilot`.
2. Bumped Android metadata to version `2.0.0-alpha.1`, version code `200001`.
3. Added V2 requirement, architecture, task-tree, security/provider, verification-matrix, and invariant documents.
4. Added implementation prompts `10` through `16` for command composition, voice boundary, provider gateway, intent contract, action registry, audit, privacy, and testing.
5. Kept the model behind a strict command envelope and local allow-listed action registry; the cloud model is not allowed direct Room/database access.
6. Updated project state, progress, known issues, and final audit records to make OPPO Pad the only future physical-test device.

## Verification

- LAEP audit: PASS; no broken links or missing required files.
- Gradle: `testDebugUnitTest assembleDebug assembleRelease` PASS.
- Device visibility: OPPO Pad `2d64f765` is visible to ADB.
- OnePlus `92f73a16`: retained as historical evidence only; no further commands are authorized.
- Post-version-bump instrumentation: not claimed; the earlier run was interrupted when the device target changed and must be rerun on OPPO Pad.

## Problems addressed

- Removed stale OnePlus acceptance wording from active progress/audit records.
- Removed misleading implication that V2 planning equals V2 implementation.
- Recorded the exact boundary between completed 1.x baseline and unimplemented 2.0 Copilot behavior.

## Remaining work

- Implement V2-T02 onward, beginning with feature-flag/package boundaries and a fake provider.
- Install the current APK on OPPO Pad, capture screenshots/UI dumps, and execute two physical regression rounds.
- Rerun connected instrumentation with OPPO Pad as the only test target.
- Implement and test the full command/action coverage before claiming 2.0 acceptance.
- Real cloud provider acceptance remains blocked until the user supplies endpoint/model configuration and credentials through the app settings; no secret is stored in the repository.

## V2 implementation increment

- Added `copilot/CopilotContracts.kt` with the `teachtrack.command.v1` command envelope, offline fake provider, provider-neutral service boundary, allow-list validator, preview/confirmation contract, and action-registry interface.
- Added unit coverage for offline task recognition, unknown-action rejection, confirmation semantics, and allow-list validation.
- `testDebugUnitTest assembleDebug`: PASS after fixing the first test-only `org.json` JVM limitation; the production codec remains Android-runtime code and is not falsely marked as unit-tested.
- Added `LocalCopilotActionRegistry` with local repository writes for `student.create` and `task.create`, plus explicit placeholders for the remaining module actions.
- Added the homepage composer UI source with text input, preview, confirmation, and an intentionally unimplemented microphone adapter; homepage wiring is the next code task.
- Rebuilt after the increment: `testDebugUnitTest assembleDebug`: PASS.
- Reinstalled to OPPO Pad only and captured `artifacts/screenshots/oppo-v2/after-copilot-contracts.png`; homepage regression visually passed.
- Wired the Copilot composer into the running app through `TeachTrackShell`; OPPO screenshot `copilot-open.png` confirms the input, microphone affordance, and preview button render on the Pad.
- OPPO coordinate automation initially missed the preview button because the ColorOS keyboard changed its bounds; a controlled retry used the UI-dump bounds and produced the expected preview (`task.create`).
- A controlled retry after hiding the ColorOS keyboard produced the expected preview (`task.create`) and confirmation button in `after-preview-tap.png`.
- The confirmation path initially exposed a real bug (`confirmed=false`); fixed it to `confirmed=true`, rebuilt, and retained the fix under unit-tested code.
- OPPO test APK installation required the device's explicit “继续安装” confirmation. After that, manual `am instrument` on OPPO only completed all 3 instrumentation tests: `OK (3 tests)` in 13.313s.
- Final OPPO flow: typed command → preview → explicit confirmation → local task write → force-stop/relaunch; the Dashboard showed `待处理 = 1` after relaunch, proving persistence. Evidence: `copilot-confirmed-fixed.png` and `copilot-persisted.png`.
- Final build after the confirmation fix: `testDebugUnitTest assembleDebug assembleRelease`: PASS. Current APKs were copied to `artifacts/apk/`.
- LAEP audit after all edits: PASS (`file_count=126`, no broken links or missing files).
- Restarted the single hidden 30-minute monitor process as PID `31620`; its heartbeat records `network=True`, `device=device`, and target `OPPO-Pad/2d64f765`.
- Expanded the local action adapter beyond student/task to real repository writes for course, score/exam, homework, communication, lesson plan, knowledge progress, problem record, and template actions; missing student context returns a safe clarification error.
- Rebuilt after the coverage expansion: `testDebugUnitTest assembleDebug`: PASS.
- Rebuilt all deliverables after the coverage expansion: `assembleDebug assembleRelease assembleDebugAndroidTest`: PASS; latest Debug APK reinstalled on OPPO and the 3-test instrumentation suite returned `OK (3 tests)` in 4.797s.
- Replaced stale test-plan and second-round records that still named OnePlus as the active device.
- Completed LAEP 4.1 REG baseline: added project vision, user requirements, scope, acceptance criteria, engineering baseline, assumption register, requirement traceability, evidence index, and compatibility state pointers. `gate`: all G-REG/G0-G4 checks PASS; `status`: RESTORABLE / REG READY.

## Latest continuation

- Added runtime microphone permission handling and a `RecognizerIntent` boundary to the Copilot composer; recognized speech is returned to the editable input field rather than sent directly to a model.
- OPPO Pad showed the microphone permission dialog and accepted the one-time permission. The ColorOS/system recognizer then returned without a result and left the app; this is recorded as a device/system recognition limitation, not as successful voice acceptance.
- Added a visible fallback status when speech returns no text, directing the teacher to check the system voice service or type manually.
- Added real task status toggling in the Planning screen; the change is persisted through Room and the pending-task counter updates from the database.
- Rebuilt `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest`: PASS.
- Re-ran OPPO-only instrumentation manually with `OK (3 tests)` and reinstalled the latest Debug APK on `OPD2101` (`2d64f765`).
- Captured the current OPPO Pad screenshot at `artifacts/screenshots/oppo-v2/final-current.png`; the green responsive dashboard and Copilot entry render correctly in landscape.
- LAEP audit, gate, and status were rerun: audit PASS, all gates PASS, status RESTORABLE / REG READY.
- Monitor heartbeat remains configured for the OPPO Pad only at a 30-minute interval; the active log is `logs/monitor.log`.

## Student maintenance increment

- Added Room-backed student update and archive operations. Archive changes only the student status to `ARCHIVED`; historical courses, scores, homework and communication records are not deleted.
- Added an edit affordance to each student card with fields for name, grade, school, subject and notes.
- Added a two-step archive confirmation that explicitly explains historical data retention.
- Rebuilt all requested artifacts successfully after this change.
- OPPO Pad manual verification opened the editor, wrote `OPPO_edit_test` into the notes field, saved, reopened the editor, and read the value back from the database-backed UI.
- OPPO Pad displayed the archive confirmation dialog; confirmation was intentionally cancelled to avoid hiding the existing user/test student during verification.
- Captured `artifacts/screenshots/oppo-v2/students-edit.png` for the responsive student-list visual check.
- Added `archiveKeepsStudentHistoryAndHidesActiveListEntry` to the Room instrumentation suite; it verifies archived status, retained course history, and exclusion from the active count.
- Latest build: `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest` PASS.
- Latest OPPO-only instrumentation: `OK (4 tests)` in 4.586s.
- Latest OPPO Pad launch screenshot: `artifacts/screenshots/oppo-v2/final-edit-current.png`; dashboard layout remains visually correct after the student-maintenance change.
- Final LAEP audit/gate/status after this increment: audit PASS, all gates PASS, RESTORABLE / REG READY.
- Replaced the course-only student timeline with a real mixed timeline combining course, score, homework, communication and problem records, sorted by date and bounded to the latest 30 events.
- Added Room Flow queries for all scores, exams, homework and communications, then added Dashboard sections for follow-up reminders, recent exams and recent courses.
- First OPPO regression caught that the new Dashboard sections pushed the required Quick Start card below the initial viewport; moved insights after Quick Start and re-ran the build/test cycle.
- Final build after the Dashboard/timeline increment: `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest` PASS.
- Final OPPO-only instrumentation after the fix: `OK (4 tests)` in 4.696s.
- OPPO UI dump confirms `快速开始`, `待跟进提醒`, `最近考试`, and `最近课程`; screenshot saved as `artifacts/screenshots/oppo-v2/dashboard-insights-final.png`.
- OPPO student workspace scroll verification confirms `学生时间线` and its empty state; screenshot saved as `artifacts/screenshots/oppo-v2/student-timeline-scroll.png`.
- OPPO Pad backup round completed end-to-end: tapped `导出完整备份`, used the system save picker, saved `TeachTrack-Backup.json` in Downloads, reopened `恢复备份`, selected the visible file, verified the preview (`2026-09-09 06:09`, 2 core records), confirmed the second confirmation dialog, and observed `已合并导入 2 条记录`.
- Backup evidence screenshot: `artifacts/screenshots/oppo-v2/backup-restore-result.png`; UI dumps: `artifacts` were captured from the save picker, restore preview, and restore result states.

## OPPO Pad second-round export/cancellation supplement

- Device lock was respected: all commands in this supplement targeted OPPO Pad serial `2d64f765`; no OnePlus command was sent.
- Backup cancellation was tested by opening the SAF save picker from Settings > Export full backup and pressing Back. The app returned to Settings and did not show the success message `备份已保存`.
- Score CSV export was tested end-to-end. DocumentsUI displayed the expected filename `TeachTrack-Scores.csv` in Downloads. After pressing Save, the app returned to Settings and displayed `成绩 CSV 已导出`.
- CSV evidence screenshot: `artifacts/screenshots/oppo-v2/csv-export-result.png`.
- Result: backup save cancellation PASS; CSV export PASS. Remaining physical gap is invalid-backup-file handling plus a second complete teacher workflow.
- Invalid backup handling was then tested with an exact device-side fixture `TeachTrack-invalid.json` containing non-JSON content. OPPO DocumentsUI displayed the fixture, and after selection the app returned to Settings with the user-facing message `恢复失败，请确认文件格式`.
- Invalid-backup evidence screenshot: `artifacts/screenshots/oppo-v2/invalid-backup-result.png`.
- The temporary invalid fixture was test-only and was removed from the OPPO Pad after capture.
- Result update: backup cancellation PASS; CSV export PASS; invalid backup rejection PASS. Only the second complete teacher workflow remains in the current physical regression gap.
- Final documentation integrity check after this supplement: LAEP `audit` PASS (`file_count=126`, no missing files or broken links); `gate` PASS; `status` RESTORABLE / REG READY.

## Provider gateway and settings increment

- Added `ProviderConfig` and provider-neutral `ProviderGateway` contracts.
- Added `OfflineProviderGateway` for deterministic local use and `UnconfiguredCloudProviderGateway` for safe failure before credentials exist.
- Added `ProviderPreferences` using app-local preferences for non-secret vendor, endpoint, model, enable flag, and normalized timeout metadata.
- Added Settings UI with enable/disable, configuration expansion, save, connection test, and explicit API-key safety boundary. No API key is accepted or written to Room, backup, logs, or screenshots.
- Added `ProviderGatewayTest` covering offline success, unconfigured cloud failure, and timeout normalization.
- Build command `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest`: PASS.
- OPPO Pad install: `Success`; OPPO instrumentation: `OK (4 tests)` in 4.554s.
- OPPO UI dump confirmed provider fields and safe test result `离线 Provider 可用`; screenshot: `artifacts/screenshots/oppo-v2/provider-test.png`.
- Remaining: real cloud adapter requires a user-selected vendor endpoint and credential; full second teacher workflow and audit/undo remain open.

## Action Registry coverage increment

- Expanded the local allow-list to include student update/search/archive, course resume/timeline, exam create, score query, knowledge query, problem status update, tag create, attachment list, dashboard query, analytics query, and navigation open.
- Added repository-backed handlers for each new local action; read/query actions do not mutate data, and write actions remain behind the existing confirmation gate.
- Added local search, one-shot student/course/score queries, exam/tag persistence helpers, and kept archived student history intact.
- Full build after expansion: `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest` PASS.
- OPPO Pad reinstall: both Debug APK and AndroidTest APK returned `Success`; instrumentation returned `OK (4 tests)` in 4.837s.
- Remaining: semantic UI acceptance for every action, execution audit/undo, and real provider integration still require further work or external credentials.

## Active device boundary update

## 2026-09-09 - Configurable external AI provider implementation

- Implemented the generic OpenAI-compatible cloud adapter and provider routing.
- Added Android Keystore AES/GCM API-key storage; no secret enters Room, backup, logs, or screenshots.
- Added `INTERNET` permission for the optional cloud path while preserving offline Copilot behavior.
- Build after the Android-24 Base64 compatibility fix: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` PASS.
- OnePlus `92f73a16` Debug and AndroidTest installs: PASS (`Success`). All-device instrumentation: PASS (`OK (4 tests)` in 48.01s).
- OnePlus screenshots: `artifacts/screenshots/oneplus-v2/teachtrack-home-cloud-build.png` and `artifacts/screenshots/oneplus-v2/teachtrack-provider-fields.png`.
- Cloud vendor success was not fabricated; no real credential was entered, so live network acceptance remains explicitly pending.

## OAuth login increment

- Added browser-based Authorization Code + PKCE login configuration for alternate AI/provider accounts.
- Added configurable authorization/token endpoints, client ID, scopes, fixed `teachtrack://oauth/callback`, state verification, token exchange and logout.
- Added separate Android Keystore encrypted token storage; OAuth tokens never enter Room, backup, logs or screenshots.
- Added Manifest callback registration and MainActivity cold-start/new-intent handling.
- Added OAuth unit tests. Rebuilt and installed Debug + AndroidTest on OnePlus; instrumentation remained `OK (4 tests)` in 47.25 seconds.
- OnePlus screenshot `artifacts/screenshots/oneplus-v2/oauth-expanded.png` confirms the OAuth configuration form, PKCE safety text and login controls.
- No external login was fabricated; end-to-end OAuth remains pending identity-provider registration.

- The user explicitly changed the active physical test target from OPPO Pad to OnePlus phone serial `92f73a16`.
- From this point forward, installs, instrumentation, manual UI checks, screenshots, and monitor heartbeats must target OnePlus only.
- OPPO Pad evidence in this log remains historical evidence and is not deleted.

## OnePlus greeting regression fix and active verification

- On the newly active OnePlus target `92f73a16`, the user reported that the original top greeting had disappeared.
- Root cause: `DashboardInsights` was emitted as a sibling overlay after the dashboard scroll column, covering the greeting and upper content.
- Fix: moved `DashboardInsights` into the dashboard content flow so the greeting, today section, quick start, reminders, and recent exams share one scroll hierarchy.
- Latest Debug and AndroidTest APK installs on OnePlus both returned `Success`.
- OnePlus UI dump now contains `早上好，老师` and `今天关注谁？`; screenshot saved to `artifacts/screenshots/oneplus-v2/home-greeting-fixed.png`.
- OnePlus instrumentation after the fix: `OK (4 tests)` in 47.898s.
- Screenshot was visually checked: greeting is visible at the top, the green theme is intact, and dashboard insight cards appear below Quick Start instead of covering the header.
