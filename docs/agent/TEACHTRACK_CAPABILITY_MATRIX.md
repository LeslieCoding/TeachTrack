# TeachTrack Agent Capability Matrix

## Reliability update (2026-09-14)

- Student/entity resolution includes recorded `displayName`, so a partial
  historical name such as `永进` is reachable without guessing a full name.
- `teachtrack rpc --file` canonicalizes a guest file path and rejects any
  resolution that leaves the PRoot rootfs. Both conditions passed in the
  27/27 OnePlus instrumentation run:
  `artifacts/oneplus-androidtest-20260914.txt`.

Generated against `LocalCopilotActionRegistry.allowedActions`
(`app/src/main/java/com/leslie/teachtrack/copilot/LocalCopilotActionRegistry.kt`),
the actual code — not a design doc written ahead of it. Regenerate this file
by re-reading that `Set<String>` whenever it changes; the two must never
drift apart (LAEP MASTER TASK section 68).

Status legend:
- `AVAILABLE_TO_AGENT` — reachable via `teachtrack rpc`, real UI screen exists.
- `AGENT_ONLY` — reachable via `teachtrack rpc`; no equivalent single UI screen
  (the capability exists in TeachTrack's data model but the UI expresses it
  as a side-effect of another screen, e.g. csv export via Settings).
- `NOT_APPLICABLE` — deliberately excluded (see reason column), not a gap.

| Action | UI screen | Status | Notes |
|---|---|---|---|
| `student.create` | Students → 新学生 | AVAILABLE_TO_AGENT | |
| `student.update` | Student Workspace → edit | AVAILABLE_TO_AGENT | `status` param (在读/暂停/结束) added post-launch — see DECISIONS.md/BACKLOG.md for the bug this fixed |
| `student.search` | Students search bar | AVAILABLE_TO_AGENT | optional `status` filter (在读/暂停/结束) added post-launch alongside the `student.update` fix — previously silently ignored if passed |
| `student.archive` | Student Workspace → archive | AVAILABLE_TO_AGENT | soft archive, not hard delete (standing "archive before delete" principle) |
| `student.overview.query` | Student Workspace (summary) | AVAILABLE_TO_AGENT | profile + latest course + recent scores + pending homework + open problems + weak knowledge |
| `student.data.organize.preview` | — | AGENT_ONLY | duplicate-tag/empty-field analysis; report-only, makes no writes |
| `student.followup.tasks.create` | Student Workspace → tasks | AVAILABLE_TO_AGENT | |
| `knowledge.weakness.query` | Student Workspace → knowledge | AVAILABLE_TO_AGENT | |
| `course.create` | Student Workspace → 记录课程 | AVAILABLE_TO_AGENT | |
| `course.resume` | Student Workspace → course history | AVAILABLE_TO_AGENT | |
| `course.timeline` | Student Workspace → timeline | AVAILABLE_TO_AGENT | |
| `exam.create` | Student Workspace → 录入成绩 flow | AVAILABLE_TO_AGENT | |
| `score.create` | Student Workspace → 录入成绩 | AVAILABLE_TO_AGENT | validates `score <= fullScore` |
| `score.query` | Student Workspace → scores | AVAILABLE_TO_AGENT | |
| `communication.create` | Student Workspace → 家校沟通 | AVAILABLE_TO_AGENT | |
| `homework.create` | Student Workspace → 作业 | AVAILABLE_TO_AGENT | |
| `lesson_plan.create` | Student Workspace → 计划 | AVAILABLE_TO_AGENT | |
| `task.create` | Student Workspace → tasks | AVAILABLE_TO_AGENT | fixed: now returns `data.taskId` (previously had no way to reference the new task) |
| `task.status.update` | Student Workspace → tasks | AVAILABLE_TO_AGENT | needs `taskId` (preferred) or a `title` substring match |
| `knowledge.create` | Student Workspace → knowledge tree | AVAILABLE_TO_AGENT | rejects duplicate names |
| `knowledge.update` | Student Workspace → knowledge tree | AVAILABLE_TO_AGENT | fixed: `knowledgePointId` lookup and `notes` param were both broken (see DECISIONS.md/BACKLOG.md) |
| `knowledge.query` | Student Workspace → knowledge tree | AVAILABLE_TO_AGENT | fixed: with a student it now returns real progress data instead of a no-op navigation message |
| `problem.create` | Student Workspace → 错题 | AVAILABLE_TO_AGENT | fixed: now returns `data.problemId` (previously had no way to reference the new record) |
| `problem.status.update` | Student Workspace → 错题 | AVAILABLE_TO_AGENT | needs `problemId` — no fallback, errors immediately if missing |
| `parent_contact.create` | Student Workspace → 家长联系方式 | AVAILABLE_TO_AGENT | |
| `parent_contact.query` | Student Workspace → 家长联系方式 | AVAILABLE_TO_AGENT | |
| `template.create` | Settings → Templates | AVAILABLE_TO_AGENT | |
| `template.list` | Settings → Templates | AVAILABLE_TO_AGENT | |
| `template.update` | Settings → Templates | AVAILABLE_TO_AGENT | |
| `template.delete` | Settings → Templates | AVAILABLE_TO_AGENT | |
| `template.render` | Settings → Templates → 预览 | AVAILABLE_TO_AGENT | reuses `TemplateRenderer` |
| `tag.create` | Student Workspace → tags | AVAILABLE_TO_AGENT | |
| `tag.assign` | Student Workspace → tags | AVAILABLE_TO_AGENT | creates the tag first if it doesn't exist |
| `tag.query` | Student Workspace → tags | AVAILABLE_TO_AGENT | |
| `attachment.list` | Student Workspace → attachments | AVAILABLE_TO_AGENT | |
| `attachment.query` | Student Workspace → attachments | AVAILABLE_TO_AGENT | |
| `attachment.delete` | Student Workspace → attachments | AVAILABLE_TO_AGENT | soft delete (`deletedAt`) |
| `backup.export` | Settings → Backup | AVAILABLE_TO_AGENT | writes to app's own `ai-backups/`, separate from user-chosen SAF backup |
| `backup.list` | Settings → Backup | AVAILABLE_TO_AGENT | |
| `backup.restore` | Settings → Backup | AVAILABLE_TO_AGENT | non-destructive ID-based merge, not an overwrite |
| `csv.export.scores` | — | AGENT_ONLY | no single dedicated UI button; reuses the same CSV export the app can produce |
| `workspace.status.query` | Settings → Workspace | AVAILABLE_TO_AGENT | |
| `workspace.export` | Settings → Workspace | AVAILABLE_TO_AGENT | |
| `workspace.check` | Settings → Workspace | AVAILABLE_TO_AGENT | integrity check |
| `workspace.sync.scan` | Settings → Workspace | AVAILABLE_TO_AGENT | |
| `workspace.conflict.list` | Settings → Workspace | AVAILABLE_TO_AGENT | |
| `workspace.conflict.resolve` | Settings → Workspace | AVAILABLE_TO_AGENT | |
| `dashboard.query` | Dashboard (首页) | AVAILABLE_TO_AGENT | |
| `analytics.query` | 统计 tab | AVAILABLE_TO_AGENT | |
| `navigation.open` | (drives the UI itself) | AVAILABLE_TO_AGENT | switches TeachTrack's own tab/student, see `CopilotEffect` |

## Explicitly out of Agent scope (reasoned exclusion, not a gap)

| Capability | Status | Reason |
|---|---|---|
| Provider/OAuth self-configuration (switch provider, change endpoint/model, start OAuth, clear API key) | NOT_APPLICABLE | Permanent security boundary — the model must never reconfigure its own outbound credentials (`INV-V2-004`, `STATE/BACKLOG.md`) |
| `workspace.create` / `workspace.open` / `.ttbackup` create+restore | NOT_APPLICABLE | Android SAF requires a one-time human directory-grant gesture; Agent navigates + instructs instead |
| Hard delete (true physical removal) of course/score/homework/exam/communication | NOT_APPLICABLE | Standing "archive before delete" project principle |

## Coverage summary

- 49 actions in `LocalCopilotActionRegistry.allowedActions`, all reachable
  from the Agent via `TeachTrackOffloadHandler` → `CopilotService.execute()`
  → the same registry the in-app Copilot chat window uses (LAEP MASTER TASK
  sections 12-13: one business logic per feature, no duplicate
  implementation for the Agent path).
- 47 map to a real UI screen; 2 (`student.data.organize.preview`,
  `csv.export.scores`) are Agent-reachable capabilities the DAO/Repository
  layer already had before this UI existed, with no dedicated UI screen of
  their own to point at.
- 0 actions are silently unsupported (`UNSUPPORTED`) — every action in the
  registry is either `AVAILABLE_TO_AGENT` or `AGENT_ONLY`.

## Verified (see `LAEP/STATE/BACKLOG.md` for the full evidence trail)

`teachtrack capabilities`, executed for real inside the PRoot Alpine shell on
OnePlus `92f73a16`, returned this exact action list — OBSERVED, not
ASSUMED. `teachtrack rpc`'s code path is the same `CopilotService.execute()`
already covered by 28/28 JVM tests and 26/26 on-device instrumented tests
from the T3-AI-COVERAGE-001 work earlier in this project.
