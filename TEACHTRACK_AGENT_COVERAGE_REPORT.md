# TeachTrack Agent Coverage Report (P2, spec §90)

Date: 2026-09-27 (P2); P4 batch + skill-sync addendum same day. Baseline: commit 8da1391 + P2/P4 changes in this commit range.

Scope: every user-facing business operation in the normal TeachTrack UI
(`MainActivity.kt`, `FeatureScreens.kt`) vs. what the Agent can do via
`teachtrack` CLI → `TeachTrackOffloadHandler` → `CopilotService` →
`LocalCopilotActionRegistry` → `TeachTrackRepository` → Room. The Agent path
and the UI path share one business-logic implementation by construction; this
report audits *surface coverage*, not implementation duplication (there is
none — the registry calls the same repository methods the ViewModels call).

## Method

- **Agent actions** enumerated from `LocalCopilotActionRegistry.allowedActions`
  (64 actions after this phase; machine mirror: `TEACHTRACK_CAPABILITIES.json`,
  kept in sync by `CapabilitiesJsonSyncTest`).
- **UI operations** enumerated from every button/dialog in
  `MainActivity.kt` + `FeatureScreens.kt` and every `TeachTrackViewModel`
  method.
- Test status column: `JVM` = covered by `app/src/test` unit tests,
  `device` = covered by the 39/39 OnePlus instrumentation run
  (`artifacts/oneplus-androidtest-20260927.txt`; P2/P4 additions verified in
  `AgentNewActionsInstrumentedTest`, earlier 27 actions in
  `artifacts/oneplus-androidtest-20260914.txt`), `manual` = verified by
  inspection/build only. Embedded Agent tab + 「问 Agent」entry verified on
  device via uiautomator (`artifacts/agent-tab-ui-20260927.xml`,
  `artifacts/ask-agent-ui-20260927.xml`).

## Per-module matrix

Legend: ✅ full / 🟡 partial / ❌ missing → action added this phase is marked **(P2 new)**.

### student.*
| UI operation | Agent action | Bridge command | Test |
|---|---|---|---|
| Add student (新学生 dialog) | ✅ `student.create` | `teachtrack rpc {"action":"student.create","params":{"studentName":"…"}}` | device |
| Edit student (StudentEditDialog) | ✅ `student.update` | rpc | device |
| Archive student | ✅ `student.archive` (soft) | rpc | device |
| Search/filter/sort students | ✅ `student.search` (+status filter) | rpc | device |
| Student workspace summary | ✅ `student.overview.query` | rpc | device |
| Organize-data preview | ✅ `student.data.organize.preview` | rpc | device |
| Follow-up task generation | ✅ `student.followup.tasks.create` | rpc | device |

### course.*
| UI operation | Agent action | Test |
|---|---|---|
| Record course (CourseDialog) | ✅ `course.create` | device |
| Resume last plan (快速续课) | ✅ `course.resume` | device |
| Course history in timeline | ✅ `course.timeline` | device |
| Browse course list (Dashboard/Planning) | ✅ `course.query` **(P2 new)** | device |

### exam.* / score.*
| UI operation | Agent action | Test |
|---|---|---|
| Create exam (implicit in score flow) | ✅ `exam.create` | device |
| Browse exams (Dashboard 最近考试) | ✅ `exam.query` **(P2 new)** | device |
| Record score (ScoreDialog) | ✅ `score.create` (validates score ≤ fullScore) | device |
| View scores | ✅ `score.query` | device |
| Score trend card (ScoreTrendCard) | ✅ `score.trend` **(P2 new)** | device |
| Export scores CSV (Settings) | ✅ `csv.export.scores` | device |

### knowledge.*
| UI operation | Agent action | Test |
|---|---|---|
| Record knowledge point (KnowledgeDialog) | ✅ `knowledge.create` | device |
| Set mastery level | ✅ `knowledge.update` | device |
| View mastery (KnowledgeProgressCard) | ✅ `knowledge.query` | device |
| Weakness lookup | ✅ `knowledge.weakness.query` | device |

### homework.* / problem.*
| UI operation | Agent action | Test |
|---|---|---|
| Assign homework (HomeworkDialog) | ✅ `homework.create` | device |
| View homework (SummaryLine/Dashboard) | ✅ `homework.query` **(P2 new)** | device |
| Change homework status | 🟡 no UI write path either — the UI never edits homework status; nothing to mirror. Out of scope for both. | — |
| Record problem (快速记录) | ✅ `problem.create` | device |
| Update problem status (ProblemStatusDialog) | ✅ `problem.status.update` | device |
| View problems (ProblemSummaryCard) | ✅ `problem.query` **(P2 new)** | device |

### plan.* / task.*
| UI operation | Agent action | Test |
|---|---|---|
| Set next-lesson plan | ✅ `lesson_plan.create` | device |
| View plans (PlanningScreen/CalendarAgenda) | ✅ `lesson_plan.query` **(P2 new)** | device |
| New todo (TaskDialog) | ✅ `task.create` | device |
| Toggle todo status (TaskCard AssistChip) | ✅ `task.status.update` (same 已完成/待处理 vocabulary) | device |
| View todo list | ✅ `task.query` **(P2 new)** | device |
| Bulk todo entry | ✅ `batch.tasks.create` **(P2 new)** (Agent-only convenience; UI has no bulk entry) | device |
| Bulk score entry | ✅ `batch.scores.create` **(P4 new)** — shared `createScoreRecord` with `score.create`, strict per-item ok/error in `data.items` | device |
| Bulk course entry | ✅ `batch.courses.create` **(P4 new)** — shared `createCourseRecord` | device |
| Bulk tagging (标记重点关注) | ✅ `batch.tags.assign` **(P4 new)** — shared `assignTagToStudent` | device |
| Bulk todo complete | ✅ `batch.tasks.complete` **(P4 new)** — shared `resolveOpenTaskId` | device |

### communication.*
| UI operation | Agent action | Test |
|---|---|---|
| Record communication (CommunicationDialog) | ✅ `communication.create` | device |
| View communications (SummaryLine/Dashboard) | ✅ `communication.query` **(P2 new)** | device |
| Add parent contact | ✅ `parent_contact.create` | device |
| View parent contacts | ✅ `parent_contact.query` | device |

### timeline.* / analytics.* / search.global
| UI operation | Agent action | Test |
|---|---|---|
| StudentTimeline card | ✅ `timeline.query` **(P2 new)** (same five event sources, same sort) | device |
| Dashboard headline | ✅ `dashboard.query` | device |
| Analytics tab counts | ✅ `analytics.query` | device |
| Global search | ✅ `search.global` **(P2 new)** (students+knowledge+templates+tags; the UI has per-screen search only) | device |

### template.* / tag.*
| UI operation | Agent action | Test |
|---|---|---|
| Template create/edit/delete/use | ✅ `template.create/update/delete/render` + `template.list` | device |
| Tags | ✅ `tag.create/assign/query`; UI has no tag screen — Agent + data model only | device |
| Tags of one student | ✅ `tag.student.query` **(P2 new)** | device |

### attachment.*
| UI operation | Agent action | Test |
|---|---|---|
| Add attachment (SAF picker dialog) | 🟡 NOT_APPLICABLE — Android SAF requires a human file-grant gesture; the record stores a content URI the Agent cannot mint. Same exclusion class as workspace create/open. | — |
| View attachments | ✅ `attachment.list/query` | device |
| Delete attachment | ✅ `attachment.delete` (soft `deletedAt`) | device |

### workspace.* / backup
| UI operation | Agent action | Test |
|---|---|---|
| Workspace status/export/check/scan/conflicts | ✅ `workspace.status.query/export/check/sync.scan/conflict.list/conflict.resolve` | device |
| Workspace create/open/.ttbackup restore | ❌ NOT_APPLICABLE — SAF one-time human directory grant | — |
| Full backup export/list/restore (Settings) | ✅ `backup.export/list/restore` (restore is ID-merge, non-destructive) | device |

### ui.open.*
| UI operation | Agent action | Test |
|---|---|---|
| Bottom-nav tab switch | ✅ `navigation.open` / `ui.open.tab` **(P2 new alias; now also maps "agent" → tab 5)** | JVM/device |
| Open student workspace | ✅ `ui.open.student` **(P2 new)** via `CopilotEffect.OpenStudent` (the existing effect mechanism; MainActivity already consumes it at `TeachTrackApp`'s `LaunchedEffect(copilotEffect)`) | device |

## Confirmation / destructive policy (spec §44)

- `CopilotService.execute(command, confirmed)`: any action **not** in
  `READ_ONLY_ACTIONS` has `confirmationRequired=true`; the in-app Copilot shows
  `preview()` and only executes on confirm. The offload bridge
  (`TeachTrackOffloadHandler`) defaults to OWNER_TRUSTED with `confirmed=true`
  because the shell has no UI to confirm in — so `READ_ONLY_ACTIONS`
  membership decides what the Agent can fire silently. All 13 P2 read/navigation
  additions were added to `READ_ONLY_ACTIONS`; `batch.tasks.create` was not.
- **Access mode (spec §43/§45, P5)**: persisted in SharedPreferences
  `teachtrack_integration` / key `access_mode` (`TeachTrackIntegrationSettings`),
  editable on the TeachTrack Integration settings screen. Default
  `owner_trusted` (owner's personal app, previous behavior). In `standard`
  mode the bridge refuses every non-`READ_ONLY_ACTIONS` action with
  `{"ok":false,"error":{"code":"CONFIRMATION_REQUIRED",…}}` so the agent can
  relay the refusal; read-only/navigation actions always run.
- Hard boundaries: no permanent delete, no DB wipe, no workspace overwrite
  exists anywhere in the registry. The only hard delete is `template.delete`
  (templates carry no student history). `student.archive`,
  `attachment.delete`, `backup.restore` are soft/merge operations.
  Destructive flags per action are in `TEACHTRACK_CAPABILITIES.json`.
- Provider/OAuth self-configuration remains a permanent exclusion (INV-V2-004):
  the model must never reconfigure its own credentials.

## Audit session linkage (spec §53, P4)

The offload protocol already carries the chat session id: the sandbox
exports `MINIS_CHAT_SESSION_ID`, `NativeOffloadServer` forwards it as
`NativeOffloadRequest.sessionId`, and `TeachTrackOffloadHandler` sets it as
the command's `clientRequestId`. P4 makes it a first-class audit field:
`CopilotAuditEntry.sessionId` (persisted as `sessionId` in the audit JSON,
null for in-app Copilot calls and pre-P4 entries), so every Agent-originated
operation can be traced back to the Agent conversation that caused it.

## Gaps found and resolution

Found (now closed): `course.query`, `exam.query`, `score.trend`,
`homework.query`, `communication.query`, `problem.query`, `task.query`,
`lesson_plan.query`, `timeline.query`, `search.global`, `tag.student.query`,
`ui.open.tab`, `ui.open.student`, `batch.tasks.create` — 14 actions, all
implemented through pre-existing repository/DAO paths (only additive repo
wrappers: `communicationsOnce`, `allExamsOnce`, `allCoursesOnce`,
`allLessonPlansOnce`, `allHomeworkOnce`; one DAO query:
`communicationsOnce`).

Remaining uncovered, deliberately:
1. **SAF-gated operations** (attachment add, workspace create/open/.ttbackup
   restore) — require a human gesture; Agent navigates + instructs instead.
2. **Provider/OAuth self-configuration** — security boundary.
3. **Hard deletes** — project principle (archive before delete).
4. **Historical-seed import** (Settings → Seed V1.0) — SAF file pick +
   one-off migration tool; not a recurring business operation.
5. **Homework status edit** — no UI write path exists to mirror.

Coverage: 68/68 registry actions Agent-reachable (64 after P2 + 4 P4 batch
actions); every recurring UI business
operation has an Agent equivalent except the five reasoned exclusions above.

## P5 hardening batch 1 (spec §43/§45/§47/§55/§65)

- **§47 Integration status page**: `TeachTrackIntegrationScreen` in the
  OpenMinis settings tree (route `teachtrack_integration`, entry row
  "TeachTrack Integration" in `SettingsScreen`). Shows live bridge-handler
  registration (`NativeOffloadServer.registeredHandlers`), offload server
  state (`NativeOffloadServer.isRunning` + abstract socket name), capability
  count (registry `allowedActions.size`), skill version
  (`TEACHTRACK_SKILL_VERSION`), and workspace health from the existing
  `WorkspaceHealthChecker`. Application-scope state only — no Activity
  coupling, so the embedded AgentHost gets it automatically. Verified on
  device (`artifacts/integration-screen-ui-20260927.xml`).
- **§43/§45 access mode**: `TeachTrackIntegrationSettings` (SharedPreferences
  `teachtrack_integration`, key `access_mode`, values `standard` /
  `owner_trusted`, default `owner_trusted`). `TeachTrackOffloadHandler`
  refuses non-`READ_ONLY_ACTIONS` in standard mode with
  `CONFIRMATION_REQUIRED`. Radio selector on the integration screen; skill
  doc bumped to 1.7.1 documenting the mode and error code.
- **§55 undo**: `CopilotUndoTokens` (URL-safe Base64 JSON) issued by
  `task.status.update`, `batch.tasks.complete` (per-task previous statuses)
  and `student.create` (archive as soft reversal).
  `LocalCopilotActionRegistry.undo()` reverses via the same repository ops
  (`updateTaskStatus`, `archiveStudent`) — no new delete paths. The
  integration screen's "Agent 操作记录" section lists
  `CopilotAuditStore` entries with a 撤销 button for token-bearing successes.
  Deliberately unsupported (would need hard deletes the project's
  archive-before-delete principle excludes): score/course/tag/task/… creates
  and `tag.assign` — no token is issued, UI hides the button.
- **§65 workspace health**: already covered by the existing
  `workspace.check` read action (registry → `WorkspaceHealthChecker`, present
  in `TEACHTRACK_CAPABILITIES.json`); no new action added. The integration
  screen surfaces the same checker for humans.
- Tests: JVM `CopilotUndoTokensTest`; device
  `AgentNewActionsInstrumentedTest` gains 4 tests (undo task status / batch
  completion / student archive, standard-mode gate) — 43/43 OK on OnePlus
  PKG110 (`artifacts/oneplus-androidtest-20260927-p5.txt`).
