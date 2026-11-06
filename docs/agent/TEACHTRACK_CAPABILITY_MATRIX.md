# TeachTrack Agent Capability Matrix

## Question-bank UI bridge update (2026-11-06)

`exam.analysis.text.fill` lets the Agent replace or append text in 题库 → 试卷分析
and open that panel. It only changes shared UI state: importing, analysis and
teacher approval remain explicit user actions. Bundled teachtrack skill v1.7.3
documents the action and its 200000-character OCR payload path.

## P4 batch + skill-sync update (2026-09-27)

4 batch actions added (`batch.scores.create`, `batch.courses.create`,
`batch.tags.assign`, `batch.tasks.complete`) — each loops the SAME shared
per-record helper as its single-op counterpart (`createScoreRecord`,
`createCourseRecord`, `assignTagToStudent`, `resolveOpenTaskId`), max 50
items, per-item failure isolation, strict per-item `{"index","ok","message"}`
array in `data.items`. Bundled teachtrack skill bumped to v1.7.0 covering all
68 actions plus batch/navigation guidance; audit entries now persist the
Agent chat `sessionId` (spec §53).

## P2 coverage-audit update (2026-09-27)

14 actions added (`course.query`, `exam.query`, `score.trend`,
`homework.query`, `communication.query`, `problem.query`, `task.query`,
`lesson_plan.query`, `timeline.query`, `search.global`, `tag.student.query`,
`ui.open.tab`, `ui.open.student`, `batch.tasks.create`) — see
`TEACHTRACK_AGENT_COVERAGE_REPORT.md` (repo root) for the full per-module
UI-vs-Agent diff and `TEACHTRACK_CAPABILITIES.json` for the machine-readable
mirror (sync-guarded by `CapabilitiesJsonSyncTest`).

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
| `course.query` | Dashboard/Planning course lists | AVAILABLE_TO_AGENT | P2: per-student or global recent courses |
| `exam.create` | Student Workspace → 录入成绩 flow | AVAILABLE_TO_AGENT | |
| `exam.query` | Dashboard 最近考试 | AVAILABLE_TO_AGENT | P2: latest 15 exams with ids |
| `score.create` | Student Workspace → 录入成绩 | AVAILABLE_TO_AGENT | validates `score <= fullScore` |
| `score.query` | Student Workspace → scores | AVAILABLE_TO_AGENT | |
| `score.trend` | Student Workspace → ScoreTrendCard | AVAILABLE_TO_AGENT | P2: last-10 score-rate line + direction |
| `communication.create` | Student Workspace → 家校沟通 | AVAILABLE_TO_AGENT | |
| `communication.query` | Student Workspace → 家校沟通 summary | AVAILABLE_TO_AGENT | P2 |
| `homework.create` | Student Workspace → 作业 | AVAILABLE_TO_AGENT | |
| `homework.query` | Student Workspace → 作业 summary | AVAILABLE_TO_AGENT | P2: pending by default, `status` for all |
| `lesson_plan.create` | Student Workspace → 计划 | AVAILABLE_TO_AGENT | |
| `lesson_plan.query` | Planning → 日历 | AVAILABLE_TO_AGENT | P2 |
| `task.create` | Student Workspace → tasks | AVAILABLE_TO_AGENT | fixed: now returns `data.taskId` (previously had no way to reference the new task) |
| `task.status.update` | Student Workspace → tasks | AVAILABLE_TO_AGENT | needs `taskId` (preferred) or a `title` substring match |
| `task.query` | Planning → 待办 | AVAILABLE_TO_AGENT | P2: pending by default, returns taskIds |
| `batch.tasks.create` | Planning → 待办 | AVAILABLE_TO_AGENT | P2: JSON-array bulk create, max 50, per-item failure isolation |
| `batch.scores.create` | Student Workspace → 录入成绩 | AVAILABLE_TO_AGENT | P4: N × score.create (shared helper), per-item ok/error in data.items |
| `batch.courses.create` | Student Workspace → 记录课程 | AVAILABLE_TO_AGENT | P4: N × course.create (shared helper), per-item ok/error in data.items |
| `batch.tags.assign` | Student Workspace → tags | AVAILABLE_TO_AGENT | P4: N × tag.assign (shared helper), per-item ok/error in data.items |
| `batch.tasks.complete` | Planning → 待办 | AVAILABLE_TO_AGENT | P4: N × task.status.update(已完成) (shared resolver), per-item ok/error in data.items |
| `knowledge.create` | Student Workspace → knowledge tree | AVAILABLE_TO_AGENT | rejects duplicate names |
| `knowledge.update` | Student Workspace → knowledge tree | AVAILABLE_TO_AGENT | fixed: `knowledgePointId` lookup and `notes` param were both broken (see DECISIONS.md/BACKLOG.md) |
| `knowledge.query` | Student Workspace → knowledge tree | AVAILABLE_TO_AGENT | fixed: with a student it now returns real progress data instead of a no-op navigation message |
| `problem.create` | Student Workspace → 错题 | AVAILABLE_TO_AGENT | fixed: now returns `data.problemId` (previously had no way to reference the new record) |
| `problem.status.update` | Student Workspace → 错题 | AVAILABLE_TO_AGENT | needs `problemId` — no fallback, errors immediately if missing |
| `problem.query` | Student Workspace → ProblemSummaryCard | AVAILABLE_TO_AGENT | P2: open problems by default, `status` filter, returns problemIds |
| `timeline.query` | Student Workspace → StudentTimeline | AVAILABLE_TO_AGENT | P2: same five event sources + sort as the UI card |
| `search.global` | — | AGENT_ONLY | P2: one query across students/knowledge/templates/tags; the UI only has per-screen search bars |
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
| `tag.student.query` | Student Workspace → tags | AVAILABLE_TO_AGENT | P2: tags assigned to one student |
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
| `navigation.open` | (drives the UI itself) | AVAILABLE_TO_AGENT | switches TeachTrack's own tab/student, see `CopilotEffect`; P2: "agent" now maps to tab 5 |
| `ui.open.tab` | (drives the UI itself) | AVAILABLE_TO_AGENT | P2: alias of `navigation.open` in the spec's `ui.open.*` namespace |
| `ui.open.student` | Student Workspace | AVAILABLE_TO_AGENT | P2: opens a student's workspace via `CopilotEffect.OpenStudent` |

## Explicitly out of Agent scope (reasoned exclusion, not a gap)

| Capability | Status | Reason |
|---|---|---|
| Provider/OAuth self-configuration (switch provider, change endpoint/model, start OAuth, clear API key) | NOT_APPLICABLE | Permanent security boundary — the model must never reconfigure its own outbound credentials (`INV-V2-004`, `STATE/BACKLOG.md`) |
| `workspace.create` / `workspace.open` / `.ttbackup` create+restore | NOT_APPLICABLE | Android SAF requires a one-time human directory-grant gesture; Agent navigates + instructs instead |
| Hard delete (true physical removal) of course/score/homework/exam/communication | NOT_APPLICABLE | Standing "archive before delete" project principle |
| Attachment add (SAF file pick) | NOT_APPLICABLE | Attachment records store a content URI from the SAF picker; the Agent cannot mint one — same SAF-grant class as workspace create/open |
| Homework status edit | NOT_APPLICABLE | The UI itself has no write path for homework status (read-only display); nothing to mirror |
| Historical-seed import (Settings → Seed V1.0) | NOT_APPLICABLE | One-off migration tool behind an SAF file pick, not a recurring business operation |

## Coverage summary

- 69 actions in `LocalCopilotActionRegistry.allowedActions` (49 pre-P2 + 14
  added in the P2 coverage audit + `ui.open.tab` alias + 4 P4 batch actions
  + `exam.analysis.text.fill`), all reachable from the Agent via
  `TeachTrackOffloadHandler` → `CopilotService.execute()` → the same registry
  the in-app Copilot chat window uses (LAEP MASTER TASK sections 12-13: one
  business logic per feature, no duplicate implementation for the Agent path).
- 66 map to a real UI screen; 3 (`student.data.organize.preview`,
  `csv.export.scores`, `search.global`) are Agent-reachable capabilities the
  DAO/Repository layer already had, with no dedicated UI screen of their own.
- 0 actions are silently unsupported (`UNSUPPORTED`) — every action in the
  registry is either `AVAILABLE_TO_AGENT` or `AGENT_ONLY`.
- The machine-readable mirror is `TEACHTRACK_CAPABILITIES.json` (repo root);
  `app/src/test/.../CapabilitiesJsonSyncTest.kt` fails the build if the two
  drift apart (spec §69).

## Verified (see `LAEP/STATE/BACKLOG.md` for the full evidence trail)

`teachtrack capabilities`, executed for real inside the PRoot Alpine shell on
OnePlus `92f73a16`, returned this exact action list — OBSERVED, not
ASSUMED. `teachtrack rpc`'s code path is the same `CopilotService.execute()`
already covered by 28/28 JVM tests and 26/26 on-device instrumented tests
from the T3-AI-COVERAGE-001 work earlier in this project.
