# TeachTrack Progress

## 2026-09-10 - V3 Portable Workspace

- Added external historical seed file flow; historical data is not packaged into the APK.
- Added SAF-backed `TeachTrackWorkspace` directory with JSONL data files.
- Added permanent workspace/device metadata and basic sync wire formats.
- Added workspace export/import and `.ttbackup` ZIP core.
- Verified build and OnePlus physical workspace creation.
- Added connected-write event journaling, conflict-file primitives, attachment SHA-256 metadata, Room 6→7 migration, and `.ttbackup` restore UI.
- Re-ran OnePlus instrumentation after migration: 4 tests passed.

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

## 2026-09-09 - OAuth login increment

- Added `copilot/OAuth.kt` as a provider-neutral login module using Authorization Code + PKCE.
- Added configurable authorization endpoint, token endpoint, public mobile Client ID, scopes, fixed app redirect URI, browser login, state validation, code exchange, and logout.
- OAuth access/refresh token response is encrypted with Android Keystore and kept outside Room, backup, logs, screenshots, and ordinary Provider API-key storage.
- Registered `teachtrack://oauth/callback` in the Android manifest and connected both cold-start and `onNewIntent` callback handling.
- Added OAuth configuration unit tests and verified the expanded OAuth settings card on OnePlus.
- Real OAuth login is not claimed because no external identity provider/client registration was supplied; callback cancellation/error handling remains safe.
