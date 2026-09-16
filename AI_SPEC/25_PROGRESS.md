# TeachTrack Progress

## 2026-09-10 - V3 Portable Workspace

- Added external historical seed file flow; historical data is not packaged into the APK.
- Added SAF-backed `TeachTrackWorkspace` directory with JSONL data files.
- Added permanent workspace/device metadata and basic sync wire formats.
- Added workspace export/import and `.ttbackup` ZIP core.
- Verified build and OnePlus physical workspace creation.
- Added connected-write event journaling, conflict-file primitives, attachment SHA-256 metadata, Room 6→7 migration, and `.ttbackup` restore UI.
- Re-ran OnePlus instrumentation after migration: 4 tests passed.
- Added workspace health checking, tombstone files and relative attachment copying with SHA-256 metadata.
- Final OnePlus regression after workspace continuation: 4 tests passed.

## 2026-09-14 - Delivery and Workspace Evidence Hardening

- Fresh single-process Release and Debug/JVM builds passed. Current JVM report is 28 tests with zero failures/errors; the current main Debug APK was installed over the existing OnePlus app without clearing data and launched to `MainActivity` successfully.
- Added compiled Android instrumentation coverage for actual two-device conflict/resolution, duplicate-event idempotence, and Room A -> backup -> fresh Room B restoration of linked records. It is deliberately recorded as compiled but not device-executed until the generated AndroidTest APK can be packaged and ColorOS permits its installation.
- Refreshed deliverables and SHA-256 values; the release candidate is accurately named `release-debug-signed`, not misrepresented as production signed.

## 2026-09-09 — V2 planning baseline

- Compiled the new cloud-assisted command workflow into `V2-EPIC-001` and requirements `V2-REQ-001` through `V2-REQ-053`.
- Named the major line `TeachTrack 2.0 · TeachTrack Copilot`.
- Bumped Android metadata to `2.0.0-alpha.1` / version code `200001`.
- Added V2 architecture, security policy, task tree, verification matrix, invariants, and prompts 10-16.
- Kept cloud AI optional and prevented direct model-to-database access in the design.

## Prior 1.x baseline

- Room schema v3 with explicit migrations and no destructive fallback.
- JVM, Room, Compose, performance, build, and historical physical-device evidence are recorded in prior round logs.
- Remaining 1.x issue: backup save reports success but immediate ColorOS restore picker did not reveal the saved file by filename; this remains open.

## Current device boundary

- All new physical-device testing is now locked to the OnePlus phone, serial `92f73a16`.
- OPPO Pad serial `2d64f765` is historical evidence only and must not receive further install, test, shell, or screenshot commands unless the user changes the target again.
- OnePlus is the active evidence device for the next regression round.

## 2026-09-09 - Provider boundary increment

- Added `ProviderConfig`, `ProviderGateway`, `OfflineProviderGateway`, and `UnconfiguredCloudProviderGateway`.
- Added Settings UI for vendor, endpoint, model, timeout, enable/disable, save, and safe connection-test feedback.
- Persisted only non-secret provider metadata in app preferences; API keys are deliberately not accepted or persisted.
- Added unit coverage for offline success, unconfigured cloud failure, and timeout normalization.
- OPPO Pad confirmed the expanded Provider settings and displayed `离线 Provider 可用` without network access.
- Build and OPPO instrumentation remain green; provider configuration is not a claim of real cloud connectivity.

## Next execution order

1. `V2-T02`: feature flag and Copilot package boundaries.
2. `V2-T10`–`V2-T12`: command envelope and validator with fake provider.
3. `V2-T20`–`V2-T25`: allow-listed action registry coverage.
4. `V2-T30`–`V2-T32`: provider settings and failure behavior.
5. Complete execution audit/undo, full semantic action acceptance, and two complete teacher workflow rounds on the active OnePlus target; real cloud success still requires user credentials.

## 2026-09-09 - Configurable external AI provider implementation

- Replaced the placeholder cloud boundary with a working generic OpenAI-compatible chat-completions adapter.
- Added provider routing: disabled or incomplete cloud configuration uses the deterministic offline provider; complete configuration uses the selected endpoint/model and falls back safely with a visible error when the network request fails.
- Added Android Keystore-backed AES/GCM storage for the API key. The key is never placed in Room, backup JSON, logs, screenshots, or the command envelope.
- Added optional `INTERNET` permission for the cloud path while preserving local-first operation.
- Settings now exposes vendor, endpoint, model, timeout, masked API-key entry, save/clear state, connection test, and the OpenAI-compatible format note.
- Build `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: PASS. OnePlus installs returned `Success`; instrumentation returned `OK (4 tests)` in 48.01 seconds.
- OnePlus evidence: `artifacts/screenshots/oneplus-v2/teachtrack-home-cloud-build.png` and `artifacts/screenshots/oneplus-v2/teachtrack-provider-fields.png`.
- Real cloud success is intentionally not claimed because no user credential was entered.

## 2026-09-10 - Kimi Code OAuth status refresh fix

## 2026-09-10 - Cloud Provider API/OAuth boundary correction

- Added stable `providerId` and `authMode` to cloud Provider configuration and persisted them separately from OAuth account preferences.
- Put the API-provider dropdown in the active Provider settings card with OpenAI, Kimi/Moonshot, Kimi Code API, Claude API, DeepSeek and Zhipu presets.
- Added a hard gateway boundary: OAuth modes are rejected by the generic API-key adapter with `AUTH_MODE_MISMATCH`; OAuth tokens are never silently reused as API keys.
- OnePlus UI inspection confirmed the selector and six entries. Unit/build/instrumentation verification passed.

## 2026-09-10 - OAuth command routing completion

- Copilot now routes a logged-in Kimi Code account to a dedicated OAuth chat-completions adapter instead of requiring API Provider endpoint/model/API Key fields.
- OAuth access tokens are read only from the existing encrypted token store; the generic API-key adapter remains isolated.
- Verified on OnePlus with a real `识别并预览` request: Kimi OAuth returned a valid command preview (`students.add`).
- Fixed Kimi Code's model-specific requirement that `temperature` be `1`. Build, unit tests and instrumentation passed.

- Fixed stale Compose status after returning from Kimi Code browser authorization.
- Settings now reloads persisted OAuth status on resume and handles device-flow success/failure explicitly.
- OnePlus rebuild/install/instrumentation passed: `OK (13 tests)`.

## 2026-09-10 - T3-HARDEN-001 query scoping

- Baseline inspection found `coursesOnce(studentId)` and `scoresOnce(studentId)` reading all rows and filtering in Kotlin.
- Added student-scoped suspend DAO queries with deterministic ordering.
- Updated `TeachTrackRepository` to use the scoped queries, reducing memory pressure and unnecessary Room-to-Kotlin transfer for Student Workspace history screens.
- No destructive migration or public data-format change.

## 2026-09-09 - OAuth login increment

- Added `copilot/OAuth.kt` as a provider-neutral login module using Authorization Code + PKCE.
- Added configurable authorization endpoint, token endpoint, public mobile Client ID, scopes, fixed app redirect URI, browser login, state validation, code exchange, and logout.
- OAuth access/refresh token response is encrypted with Android Keystore and kept outside Room, backup, logs, screenshots, and ordinary Provider API-key storage.
- Registered `teachtrack://oauth/callback` in the Android manifest and connected both cold-start and `onNewIntent` callback handling.
- Added OAuth configuration unit tests and verified the expanded OAuth settings card on OnePlus.
- Real OAuth login is not claimed because no external identity provider/client registration was supplied; callback cancellation/error handling remains safe.

- 2026-09-11: Dashboard now consumes bounded latest-200 projections for overview collections; repository writes emit SyncEvents across the current business write surface; OnePlus instrumentation reached 7 passing tests.

## 2026-09-10 - OpenMinis-aligned authentication separation

- Replaced the misleading OAuth catalog that mixed API-console providers with account authorization.
- Added separate OAuth/account catalog and API-key catalog. The OAuth card no longer opens API consoles.
- Added provider-specific Kimi Code RFC 8628 device authorization with browser verification URL, polling, backoff, expiry and encrypted token persistence.
- Added OpenAI Codex and Claude account OAuth metadata for provider-specific PKCE authorization/token exchange.
- Added OnePlus visual evidence: `artifacts/screenshots/oneplus-v2/oauth-separated-settings.png` and `oauth-separated-menu.png`.
- Build and unit/instrumentation APK assembly passed; no real provider credential was entered.

## 2026-09-11 - AI Command Runtime upgrade

- Added structured `CopilotEffect` results so successful commands drive real module and student-workspace navigation.
- Added typed `AppCommand` boundary, risk metadata, validation, conservative student resolution, and ordered multi-command plans.
- Added course-plan expansion for course + knowledge progress + homework + next-lesson requests.
- Wired command execution to the local audit store and expanded the Kimi-compatible structured command schema.
- Verified JVM Copilot tests (21 passing), Debug assembly, and OnePlus instrumentation (13 passing).

## 2026-09-11 - Silent OnePlus deployment

- Added `scripts/deploy-oneplus.ps1` as the default deployment entry point.
- It targets only OnePlus serial `92f73a16`, uses `adb install -r -d`, force-stops/restarts the app, and optionally runs connected instrumentation without requiring a user click.

## 2026-09-12 - AI cross-module organization runtime

- Implemented a real Room-backed organization workflow instead of treating “整理所有学生数据” as a navigation request or a chat reply.
- Added a local report projection spanning students, courses, homework, tasks, problem records, knowledge points and progress; it returns grouped, per-student follow-up facts without filling unknown data.
- Added planner policy for API and Kimi Code OAuth providers so their structured output can select the new read-only action; local allow-list validation remains authoritative.
- Added unit tests for intent mapping and non-fabrication, plus a OnePlus instrumentation test proving the report reads real Room data and does not mutate rows.
- `:app:testDebugUnitTest` and `:app:assembleDebug` passed. OnePlus test report: 14 tests, 0 failures, 0 errors.
- Added an explicit, confirmation-gated command to turn organization findings into de-duplicated student follow-up tasks. Its JVM contract test passed. The immediate OnePlus UTP rerun stalled without a result, was stopped automatically, and is marked pending rather than passed. The newly built Debug app was silently installed and launched on OnePlus after that interruption.

## 2026-09-14 - Agent bridge and historical-data reliability

- Historical partial-name students are searchable by their recorded `displayName`; no missing legal/full name is inferred or written.
- The Agent RPC file-payload resolver now enforces canonical rootfs containment.
- The device test suite no longer depends on, or mutates, a real persisted Workspace connection.
- OnePlus `92f73a16`: Debug and AndroidTest APKs were installed in place; `TeachTrackDatabaseInstrumentedTest` passed **27/27**. The run included `displayNameOnlyHistoricalStudentIsSearchableAndResolvable` and `agentRpcFilePayloadCannotEscapeRootfs`.
