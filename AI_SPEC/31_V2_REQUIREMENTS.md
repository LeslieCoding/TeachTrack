# TeachTrack 2.0 Requirements

## Command entry

- `V2-REQ-010`: Home page shows a text field with clear command affordance.
- `V2-REQ-011`: Submit supports keyboard action and explicit send button.
- `V2-REQ-012`: Voice button requests microphone permission only when used and shows recording/transcription state.
- `V2-REQ-013`: Transcription can be edited before model submission.
- `V2-REQ-014`: Network/provider failure leaves the typed/transcribed text available and does not affect local data.

## Provider configuration

- `V2-REQ-020`: Settings exposes provider selection, endpoint where applicable, model name, timeout, and enable/disable state.
- `V2-REQ-021`: Secrets are stored locally through a protected mechanism and excluded from backups/logs.
- `V2-REQ-022`: Provider test is explicit, reports a safe non-sensitive result, and is never automatic on app launch.
- `V2-REQ-023`: Provider adapters share one domain interface and can be replaced without changing business repositories.

## Intent and action execution

- `V2-REQ-030`: Model output uses a versioned JSON envelope with action ID, parameters, confidence/ambiguity state, and user-facing explanation.
- `V2-REQ-031`: Unknown action IDs, invalid parameters, missing student references, and malformed output are rejected locally.
- `V2-REQ-032`: Read-only actions may show results directly; write actions show a preview before commit.
- `V2-REQ-033`: Sensitive actions require explicit confirmation and identify affected fields/entities.
- `V2-REQ-034`: Bulk operations require count, scope, and an additional confirmation.
- `V2-REQ-035`: Every executed action creates a local audit entry with source text, action ID, result, and redacted metadata.
- `V2-REQ-036`: Undo is offered for reversible actions; irreversible actions are not presented as reversible.

## Module coverage

- `V2-REQ-040`: Action Registry covers students, profile/parent contact, courses, exams/scores, knowledge progress, homework/problems, communication, lesson plans, tasks, templates, tags, attachments, dashboard queries, analytics queries, backup/export entry points, and navigation.
- `V2-REQ-041`: Student context can be selected explicitly and can be resolved from a unique name; ambiguous names require a choice.
- `V2-REQ-042`: Natural language can create a draft record but never invents missing required values without marking them for confirmation.

## Privacy and local-first

- `V2-REQ-050`: Copilot disabled or unavailable leaves all 1.x functions usable.
- `V2-REQ-051`: Sensitive fields are redacted or excluded from model context by default.
- `V2-REQ-052`: The user sees what information will leave the device before a request containing sensitive data is sent.
- `V2-REQ-053`: No API key, raw prompt, family contact, or full score history appears in logs, crash reports, or backup files.
