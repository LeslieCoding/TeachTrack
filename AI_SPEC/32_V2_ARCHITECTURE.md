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

## Full module coverage (T3-AI-COVERAGE-001)

`LocalCopilotActionRegistry` is the single landing point for every action; it never exposes DAO/SQL to a provider. It takes an optional `WorkspaceManager` and an optional local `backupDirectory` (`java.io.File`) so Portable Workspace and backup/CSV actions can run without either JVM unit tests (which pass both as `null` and skip those branches with a clear "not supported in this environment" result) or the real Android runtime (which the Compose ViewModel supplies) needing different code paths.

Two capability areas are intentionally excluded from the Action Registry, not missing by oversight:

- **Provider/OAuth self-configuration.** Switching provider, changing endpoint/model, starting OAuth login, or clearing the API key is never an AI-executable action (`INV-V2-004`). Letting the model change its own outbound configuration or credentials is a self-privilege-escalation risk.
- **Workspace creation/attach and `.ttbackup` create/restore.** Android Scoped Storage requires a user gesture through a system picker (`ActivityResultContracts.OpenDocumentTree`/`CreateDocument`) to grant access to a *new* directory or file; there is no legitimate way to satisfy that from a background text command. Once a workspace has been attached once through that one-time picker, `WorkspaceManager.storage()` reuses the persisted URI permission, so `workspace.export`/`workspace.check`/`workspace.sync.scan`/`workspace.conflict.list`/`workspace.conflict.resolve`/`workspace.status.query` run as ordinary AI actions with no further picker interaction. Requests for the picker-gated operations get a `navigation.open`-style effect pointing at Settings plus a plain-language instruction, which is the AI_APPLICATION profile's "software-mediated" capability tier, not silent failure.

Local backup/CSV actions (`backup.export`, `backup.list`, `backup.restore`, `csv.export.scores`) write into the app's own external-files directory (`context.getExternalFilesDir(null)/ai-backups`), which requires no runtime permission or picker on API 24+ scoped storage — this is deliberately a *separate* location from the user-chosen SAF backup file created by Settings' "导出完整备份" button, so an AI-triggered backup can never silently overwrite a manually created one.
