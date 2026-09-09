# TeachTrack 2.0 AI_SPEC

## Identity

- Product: TeachTrack / 师迹 · 教师学生管理工作台
- Major version: `2.0.0-alpha.1`
- Codename: `TeachTrack Copilot`
- Task: `V2-EPIC-001`
- Source: `USER_EXPLICIT` request on 2026-09-09, compiled under LAEP v3.1

## User intent

The user wants a home-page input box that accepts typed or voice descriptions, sends the description to a configurable cloud-model provider, understands which TeachTrack operation is intended, and can operate all application modules to add or update records.

## Explicit requirements

- `V2-REQ-001`: Add a prominent home-page natural-language input box.
- `V2-REQ-002`: Support voice input as an input method.
- `V2-REQ-003`: Allow the user to configure which cloud-model provider is used.
- `V2-REQ-004`: Interpret the input as an intended TeachTrack operation.
- `V2-REQ-005`: Cover all existing business modules through callable actions.
- `V2-REQ-006`: Treat this as a major product version and rename the version.

## Inferred requirements

- `V2-INF-001`: Model output must be a structured command, not free-form code or direct SQL.
- `V2-INF-002`: Normal local-first operation must not depend on a provider, network, account, or API key.
- `V2-INF-003`: Write operations need preview, validation, confirmation, audit, and undo where possible.
- `V2-INF-004`: Sensitive student and family data must not be uploaded without explicit user action and visible disclosure.

## AI recommendations

- `V2-REC-001`: Start with typed commands and provider abstraction before enabling voice streaming.
- `V2-REC-002`: Use an allow-listed Action Registry and versioned JSON intent envelope.
- `V2-REC-003`: Default to read-only interpretation or confirmation for ambiguous and sensitive operations.
- `V2-REC-004`: Support an OpenAI-compatible adapter plus provider-specific adapters without coupling UI to a vendor.

## Non-goals for 2.0 alpha

- No autonomous background agent.
- No automatic sending of WeChat, SMS, phone calls, or parent messages.
- No model access to raw Room or filesystem.
- No silent bulk edits or deletes.
- No mandatory cloud account or forced provider subscription.
- No automatic upload of student/family information.

## Version rule

The Android artifact is bumped from `0.1.0` / version code `1` to `2.0.0-alpha.1` / version code `200001`. It is an alpha planning/implementation line, not a claim that every 2.0 capability is already complete.
