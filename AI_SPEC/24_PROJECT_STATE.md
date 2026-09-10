# TeachTrack Project State

## Current V3 state (2026-09-10)

The active line is TeachTrack 3.0.0-alpha.1 (version code 300001). Portable Workspace foundation is implemented: SAF workspace create/open, JSONL export/import, device identity, basic sync event/checkpoint formats and `.ttbackup` writer. Full conflict UI, automatic per-entity event emission and second-device simulation remain in progress.

## Product line

- 1.x MVP: local-first teacher workspace, implemented and previously verified.
- Current line: `TeachTrack 2.0 · TeachTrack Copilot`, Android `2.0.0-alpha.1`, version code `200001`.
- Current stage: V2 foundation implementation with continued 1.x completion; command contract, offline provider, validator, home composer, confirmation flow, local action adapters, Room-backed student edit/archive, and provider configuration boundary are implemented. Voice/audit/undo and full action semantics remain in progress.
- Real-device target for all subsequent testing: OnePlus phone serial `92f73a16`; OPPO Pad `2d64f765` is retained as historical evidence only.

## 1.x baseline

Students, workspaces, profiles, courses, quick resume, timeline, exams/scores, knowledge progress, homework/problems, communication, plans/tasks/calendar, dashboard, analytics, local backup/restore entry points, CSV, templates, tags, attachments, Room migrations, and green responsive UI remain the baseline.

## 2.0 scope now defined

Home text command composer, configurable-provider boundary, strict command envelope, partial local Action Registry, preview/confirmation/ambiguity handling, and 1.x regression protection. Voice, provider settings, audit/undo, redaction, and complete action semantics remain planned.

## Current verification state

- Version metadata is `2.0.0-alpha.1`; Copilot verification is PARTIAL and tracked in the V2 matrix.
- V2 verification matrix: `AI_SPEC/35_V2_VERIFICATION_MATRIX.yaml`.
- V2 invariants: `AI_SPEC/36_V2_INVARIANTS.yaml`.
- Existing OPPO Pad evidence remains historical baseline evidence; all new physical-device evidence must be captured on OnePlus `92f73a16`.

## Risks and rollback

- R3: cloud model network, provider secrets, voice permission, and model-directed writes.
- Rollback: disable the Copilot feature flag and keep all 1.x screens/repositories; revert version metadata only if release tooling requires it.
- No real provider credential has been supplied or stored.
- Kimi Code OAuth command routing is now physically verified on OnePlus: a live Copilot request returned a valid `students.add` preview without API Key/endpoint/model configuration.
- OnePlus `92f73a16` is the active connected device with the current Debug APK installed; the latest screenshot/UI dump is under `artifacts/screenshots/oneplus-v2/`. OPPO evidence remains under `artifacts/screenshots/oppo-v2/`.

## T3-HARDEN-001 (2026-09-10)

- Student-scoped one-shot course and score queries now execute in SQLite instead of loading complete tables and filtering in memory.
- No schema change was required; existing indexes remain applicable.
- Remaining candidates: transaction boundaries around multi-entity saves/imports, bounded dashboard queries, and automatic per-entity sync event emission.

## Persistent hardening backlog (user requested)

- [ ] T3-HARDEN-002 Dashboard bounded projections/queries
- [ ] T3-HARDEN-003 complete automatic per-entity SyncEvent generation
- [ ] T3-HARDEN-004 Workspace two-device conflict simulation and resolution tests
- [ ] T3-HARDEN-005 complete backup/restore round-trip validation
- [ ] T3-HARDEN-006 expanded data-consistency and corruption tests
