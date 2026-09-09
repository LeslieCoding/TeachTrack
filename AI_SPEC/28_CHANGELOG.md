# TeachTrack Changelog

## 3.0.0-alpha.1 - Architecture/performance hardening

- Scoped Student Workspace one-shot course and score reads to SQLite queries.
- Preserved existing schema, migrations, portable workspace format, and local-first behavior.

## 3.0.0-alpha.1 - Portable Workspace foundation

- Added portable workspace manifest and JSONL data exchange.
- Added SAF create/open/export controls.
- Added device identity, sync events/checkpoints and `.ttbackup` archive writer.
- Moved historical seed usage to user-selected external JSON; no historical student data is packaged into the APK.

## 3.0.0-alpha.1 - OAuth correction

## 3.0.0-alpha.1 - Cloud Provider routing correction

- Separated the active Cloud Provider API-key selector from the account OAuth selector.
- Added persisted provider identity/auth-mode metadata and API presets for OpenAI, Kimi/Moonshot, Kimi Code API, Claude API, DeepSeek and Zhipu.
- Prevented the generic OpenAI-compatible adapter from accepting OAuth routing; it returns an explicit authentication-mode mismatch.
- Added Provider routing metadata unit coverage and OnePlus UI evidence.

## 3.0.0-alpha.1 - OAuth command routing

- Added Kimi Code OAuth command interpretation using the encrypted OAuth session and Kimi Code chat-completions endpoint.
- Copilot routing now distinguishes logged-in Kimi OAuth from API Key Provider configuration.
- Fixed Kimi Code request temperature validation and verified a live command preview on OnePlus.

- Account OAuth and API Key are now represented as separate provider paths.
- Added OpenAI Codex, Claude account PKCE metadata and Kimi Code device-flow implementation based on the provider-specific pattern observed in OpenMinis.
- OAuth settings no longer presents API-console links as login actions.

## 2.0.0-alpha.1 — 2026-09-09

- Renamed the major product line to `TeachTrack 2.0 · TeachTrack Copilot`.
- Bumped Android version code to `200001` and version name to `2.0.0-alpha.1`.
- Added V2 requirements for home text/voice commands, configurable cloud providers, strict intent validation, all-module Action Registry, confirmation, audit, undo, privacy, and offline fallback.
- Added LAEP v3.1 verification matrix and invariant registry for V2.
- Added implementation prompts 10-16.
- Added provider-neutral gateway contracts, offline/failure gateways, and local provider configuration persistence with API-key exclusion from Room, backup, logs, and screenshots.

## 1.x baseline

- Local-first student management MVP, Room migrations, course quick resume, Student Timeline, scores, knowledge, plans, dashboard, backup/export entry points, green responsive UI, and OnePlus evidence.
