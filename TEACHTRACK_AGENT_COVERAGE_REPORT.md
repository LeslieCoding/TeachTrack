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

## P5 hardening batch 2 (spec §52/§57/§48/§58 + release refresh)

- **§58 literal sandbox e2e**: `SandboxEndToEndInstrumentedTest` runs inside
  the app's own process and drives the CLI through the REAL path —
  `ShellExecutor` → proot exec of the `/usr/local/bin/teachtrack` stub →
  native_offload extension → abstract socket → `NativeOffloadServer` →
  `TeachTrackOffloadHandler` → registry. Asserts: `--help` documents
  capabilities/rpc, `capabilities` returns strict JSON with all 68 actions,
  and a student seeded into the real Room DB is found by
  `student.search` over the socket (soft-archived afterwards). 6/6 green
  (`artifacts/oneplus-sandbox-e2e-20260927.txt`). No fallback to
  direct-dispatch was needed — the full PRoot path works in instrumentation.
- **§52 lifecycle**: (c) register/start idempotency is a test in the same
  class (re-register + re-start, handler set unchanged, bridge still
  answers); (a)+(b) are covered by `scripts/verify-agent-lifecycle.sh`,
  which force-stops + cold-starts the app and rotates the display, re-running
  the capabilities probe after each — both OK
  (`artifacts/lifecycle-20260927.txt`).
- **§57/§48 native-ability smoke (no LLM key)**: same e2e class —
  `uname -a`, `busybox`, `/etc/alpine-release` execute inside the sandbox,
  and `/var/minis/skills/teachtrack/SKILL.md` (bind-mounted from
  `filesDir/minis-global/skills`) reads back with `version: 1.7.1`.
- **Release deliverable**: `3.0.0-alpha.2` (versionCode 300002). Release
  build keeps the established debug-signing approach
  (`signingConfig = signingConfigs.getByName("debug")` in the release type).
  `artifacts/deliverables/TeachTrack-3.0.0-alpha.2-release-debug-signed.apk`
  (SHA-256 `061E2DD0…B721F5`) and `…-alpha.2-debug.apk`
  (`5441D78C…B2FEAC17`); both appended to `SHA256SUMS.txt` and verified with
  `sha256sum -c`. The release APK was install-smoke-tested on the OnePlus
  (installs, launches to MainActivity), then the debug build was restored
  for future instrumented runs.

## P5 hardening batch 3 (spec §7/§46/§66/§71 + final sweep)

- **§71 error recovery**: student-resolution failures are now stable and
  machine-readable everywhere. `CopilotResult.errorCode` carries
  `AMBIGUOUS_STUDENT` (with `data.candidates`) / `STUDENT_NOT_FOUND`; the
  offload bridge maps them into `error.code` (with `error.data` for
  candidates) instead of flattening everything to `EXECUTION_FAILED`, and
  batch actions repeat the SAME codes per item in `data.items[].code`
  (`batch.scores/courses/tags` share one `resolveItemStudent` helper).
  3 new device tests (ambiguous single, not-found single, batch per-item
  codes) — full suite 52/52 (`artifacts/oneplus-androidtest-full-20260927.txt`).
- **§46 privacy guidance**: new "Privacy" section in the bundled skill
  (query minimally, no whole-table dumps, no parent-contact/sensitive
  fields unless asked, summarize instead of echoing raw JSON). Skill
  bumped to **1.7.2** (one bump covering §46 + §71 error-code docs).
- **§7 tablet (OPD2101, 2d64f765, 1600x2560@360)**: no code change needed.
  `ChatSplitScaffold` decides two-pane from `currentWindowAdaptiveInfo`
  (width ≥ 840dp + height ≥ 480dp), which measures the full app window —
  so the embedded AgentHost already renders the session-list + chat split
  in landscape (`artifacts/tablet-agent-20260927.png`), and the student
  workspace adapts cleanly (`artifacts/tablet-workspace-20260927.png`).
  Minor observation, not fixed: workspace cards cap at ~half width on the
  very wide screen — cosmetic, left for a future tablet-polish pass.
- **§66 data organization**: already Agent-reachable —
  `student.data.organize.preview` exposes `StudentDataOrganizer.analyze`
  read-only (findings over students/courses/homework/tasks/problems/
  knowledge), and `student.followup.tasks.create` is its explicit-write
  counterpart. No new action; no auto-delete, per spec.
- **Release**: `3.0.0-alpha.3` (versionCode 300003) — app code changed in
  §71/§46, so a new debug-signed release APK + debug APK were cut and
  appended to `SHA256SUMS.txt`.
- **OnePlus Ace 5 acceptance (92f73a16, PKG110 / OP5D2BL1, Android 16 /
  SDK 36)**: upgrade install of alpha.3 release-debug-signed over the
  alpha.2 debug build preserved real user data （陈浠怡 etc.,
  `artifacts/ace5-upgrade-data-preserved-20260916.xml`). Full suite 52/52
  via a split run — 46 core tests + 6 sandbox tests
  (`artifacts/oneplus-ace5-androidtest-20260916.txt`).
  UI acceptance passed: home, Agent tab (embedded OpenMinis + TeachTrack
  nav), 问 Agent prefill from student detail, and the Integration screen
  (bridge running on abstract socket 'native-offload', 68 actions, skill
  1.7.2, Owner Trusted, workspace 0 issues) — `artifacts/ace5-ui-*-20260916.*`.
  Device left on the alpha.3 release-debug-signed build with data intact.
- **Full-suite intermittent hang — root cause found (fixed in the harness,
  not the app)**. The single 3-class instrument run used to hang
  intermittently inside one of the sandbox tests. Captured during a live
  hang (`artifacts/hang-diag/live-run2/`): the app process was fully
  silent — no proot child anywhere on the system, no `native-offload`
  worker thread, a clean fd table (no leak), no app logs for minutes, and
  SIGQUIT (`kill -3`) produced no trace because a suspended process cannot
  run its dump handler. Every thread state was indistinguishable from the
  ROM's frozen-app baseline. This rules out every in-app hypothesis
  (fd/thread/pty leaks in `ShellExecutor`/`pty_bridge.c`, offload-socket
  state, singleton ordering, pipe-buffer deadlock — the reader drains
  continuously and EOF had no holders left): the **whole process was
  frozen by the system's cached-app freezer** (stock
  `CachedAppOptimizer`/`am freeze` on ColorOS — the same logcat shows
  `Async freezing`/`Freeze process` of other cached apps, and Oplus's
  `osense.compress` was actively shrinking our backgrounded app every
  20 s). It only bites when the suite is started while the app process is
  **cached** (e.g. backgrounded after UI acceptance flows); a process
  started fresh by `am instrument` is virtually never a freezer
  candidate, which is why the sandbox class alone and warm-restarted runs
  always passed. Production paths were never affected: agent streams and
  chat presence already hold the process at adj=200 via
  `AgentForegroundService`/`SessionActivityTracker`, so real shell
  executions run freeze-immune by design — the instrumented test run was
  the only unprotected path, hence a harness fix rather than app changes.
  Fix: `scripts/run-device-suite.sh` now (1) marks the app
  `am unfreeze --sticky` for the process lifetime, (2) keeps MainActivity
  foreground during the run, and (3) watches for stalls and heals them
  with `am unfreeze` while capturing `dumpsys activity processes`
  (`isFrozen`/`isPendingFreeze`) for forensics.
- **Correction (2026-09-17): the earlier "6 consecutive 52/52" proof was
  invalid — androidx.test `-e log true` is discovery/log-only mode.** The
  runs recorded in `artifacts/oneplus-androidtest-full-20260917.txt` were
  produced by `scripts/run-device-suite.sh` while it passed `-e log true`
  to `am instrument`. In androidx.test that flag switches the runner into
  log/discovery mode: it emits per-test start/pass status events with
  near-zero durations (`Time: 0.004`–`0.068`) **without executing the test
  bodies** (verified: no marker files, no test logcat output, no side
  effects across repeated runs). Those "52/52" results are therefore not
  execution proof and must not be cited as such. The genuine real-execution
  evidence is: `artifacts/oneplus-ace5-androidtest-20260916.txt` (52/52 via
  split run, real durations), `artifacts/oneplus-androidtest-20260927.txt`
  (e.g. `Time: 8.68`), and `artifacts/oneplus-sandbox-e2e-20260927.txt`
  (`Time: 2.059`/`3.261`). The runner script has been fixed: the `-e log
  true` flag is removed, and the sticky-unfreeze is now re-issued after the
  instrumented process exists (the pre-run `am unfreeze --sticky` was a
  no-op when the process had not started yet; the process could be frozen
  during attach — re-confirmed 2026-09-17 red-handed via
  `ApplicationThreadDeferred` in `dumpsys activity processes` with total
  log silence).
- **Re-proof done (2026-09-17): genuine single-invocation 52/52.**
  `artifacts/oneplus-androidtest-full-20260917.txt` has been overwritten
  with a REAL-execution run of the fixed `scripts/run-device-suite.sh`
  (screen kept on via `svc power stayon true`, keyguard dismissed, app
  foregrounded before start): all 3 classes, 52 test-start/52 test-pass
  status pairs, `OK (52 tests)`, total `Time: 16.083` (vs. the phantom
  runs' per-test `0.004`s), **0 stall heals needed**. One environment
  caveat from the re-proof: instrumented tests cannot run against the
  minified release APK — the first attempt crashed instantly with
  `NoClassDefFoundError: kotlin.jvm.internal.Intrinsics` because R8
  shrinking strips classes the test APK links against. The successful run
  used `adb install -r` of the debug app + androidTest APKs over the
  alpha.3 release-debug-signed install (same debug key, user data
  preserved; alpha.3 was restored afterwards). Earlier real-execution
  evidence also stands: `artifacts/oneplus-ace5-androidtest-20260916.txt`
  (52/52 split run), `artifacts/oneplus-androidtest-20260927.txt`
  (`Time: 8.68`), `artifacts/oneplus-sandbox-e2e-20260927.txt`
  (`Time: 2.059`/`3.261`).

