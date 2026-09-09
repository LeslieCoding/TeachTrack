# TeachTrack 2.0 Architecture and Data Contract

## Domain modules

1. `copilot.ui`: home composer, voice state, preview, confirmation, result, error, and history.
2. `copilot.domain`: `CommandRequest`, `CommandEnvelope`, `ActionDefinition`, `ActionResult`, `ConfirmationLevel`.
3. `copilot.provider`: provider-neutral gateway and adapters.
4. `copilot.security`: secret storage, redaction, consent, network policy, and audit redaction.
5. `copilot.registry`: allow-listed action definitions and dispatch.
6. `copilot.execution`: validation, preview, confirmation, and undo coordination.

## Command envelope

```json
{
  "schemaVersion": "teachtrack.command.v1",
  "action": "score.create",
  "mode": "WRITE",
  "parameters": {"studentId": "...", "examName": "...", "score": 91, "fullScore": 100},
  "missingRequired": [],
  "ambiguity": null,
  "explanation": "记录学生本次考试成绩",
  "sensitiveFields": ["score"],
  "requiresConfirmation": true
}
```

The implementation must validate with a strict parser and reject unknown or unsafe action semantics.

## Action definition contract

Each action declares: stable ID, read/write mode, parameters, required fields, entity lookup policy, sensitive fields, confirmation level, repository operation, audit label, undo support, and tests. The model never chooses arbitrary methods.

## State flow

`Idle -> Capturing -> DraftText -> Interpreting -> Preview/Clarification/Error -> Confirmed -> Executing -> Success/Failure -> UndoWindow/History`

No write is allowed from `Interpreting` directly to `Executing`.

## Persistence

Store provider preferences and audit metadata locally. Do not persist API secrets in Room. A future schema migration must preserve all 1.x records. Command history defaults to redacted metadata and has a retention/clear policy.
