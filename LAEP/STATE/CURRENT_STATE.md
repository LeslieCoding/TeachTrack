# TeachTrack — LAEP CURRENT_STATE

## What this file is

This is the single "catch up fast" entry point for any AI (or human) picking
up this project cold. Read this file, then `STATE/BACKLOG.md` (has every
task/bug with full technical detail, most recent entries last), then
`STATE/DECISIONS.md` DEC-004 (why the architecture is what it is) before
touching code. Earlier history (pre-2026-09-12) lives in `AI_SPEC/` and is
summarized, not repeated, below.

**Last updated: 2026-09-15, by the AI that did the work below.**
The user asked for this file to be written so the work can be handed off to
a different AI/session to continue. Everything below is real, verified
state — not a plan, not an intention.

**2026-09-15 addendum:** the full-matrix Agent action test/fix/verify loop
described below reached completion (all 49 actions live-tested this
session, every found bug fixed, tier 3 architecture trim done, tier 4
assessed and closed with no action needed — see `STATE/BACKLOG.md`'s most
recent entries). Most recent work after that: `T-AGENT-TAB-REDESIGN-001` —
the Agent tab (`MainActivity.kt` tab index 5) no longer splits its height
with a duplicated Dashboard pane; it now shows the Agent chat at full tab
height, per direct user feedback that the split wasted screen space
restating Home. See `BACKLOG.md`'s top entry for full detail.

**2026-09-16 addendum:** user supplied a real bug-hunt report
(`T-STUDENT-SCOPE-FILTER-001` in `BACKLOG.md`, top entry) produced by
probing the live Agent against real production data. Root cause across 4
of its 6 named bugs was one pattern repeated in different call sites:
several business queries scanned ALL students regardless of status
instead of scoping to `status = 'ACTIVE'`, so ended/archived students kept
resurfacing in AI follow-up findings, auto-created tasks, and Dashboard
cards. Fixed at every call site found (`TeachTrackRepository
.copilotOrganizationData()`, `Dashboard()`/`DashboardInsights()` in
`MainActivity.kt`, `activeStudentCountOnce()`/`observePendingTaskCount()`/
`pendingTaskCountOnce()` in `TeachTrackDao.kt`). Also found and fixed a
genuine regression from the 2026-09-15 Agent-tab redesign above: `ChatScreen`
embedded in `AgentSplitPanel` was double-reserving keyboard height (once
from `MainActivity`'s own bottom-nav `Scaffold`'s default `safeDrawing`
inset consumption, once more from `ChatScreen`'s own manual
`.imePadding()`), rendering as a large blank gap between the composer and
the real keyboard — fixed with a new `handlesOwnImePadding` parameter.
All fixes SQL- and/or live-UI-verified against OnePlus `92f73a16`'s real
production data (49 students: 14 ACTIVE / 35 ARCHIVED, exactly matching
the report's own cited figures) — see `BACKLOG.md` for full evidence.
**Environment note for future sessions:** this session hit a real,
reproducible WSL-bridge issue — a `wsl -d Ubuntu-24.04 -- ./gradlew ...`
invocation piped through this harness's Bash tool silently dies (the
wsl.exe client disconnects, reporting a misleading exit code, while the
actual build is still running or gets orphaned inside the VM) whenever
Gradle goes quiet for a while during a long, output-sparse task —
consistently `kspDebugKotlin`. Two independent fixes both worked: (a)
redirect output to a file on the Windows-visible `/mnt/d` path instead of
piping live stdout through the tool, and/or (b) add `--info` to keep the
output stream continuously active. Prefer (b) plus checking for
`BUILD SUCCESSFUL`/`BUILD FAILED` in the resulting log before trusting any
build's exit code in this environment — an exit code of 0 alone was
observed to be unreliable here.

## The one-paragraph version

TeachTrack (a personal-use Android app for a private tutor to track
students/courses/scores/etc.) now has the **complete OpenMinis Agent
Runtime merged into it at the source level** — same APK, same process, not
a wrapper. A custom `teachtrack` CLI tool lets the Agent read/write
TeachTrack's real business data by going through the app's own
UseCase/Repository layer (never touching Room/DAO directly). This session
did three things: (1) found and fixed 5 real bugs in that bridge by
actually exercising it through the Agent on a real device and cross-checking
the real SQLite database after each call, (2) deleted 57 files of OpenMinis
functionality the user doesn't use (backup/rclone sync, debug tooling,
Shizuku, accessibility CLI, scheduled tasks, skills marketplace browser) to
simplify the codebase per explicit user request, while keeping the chat
core / Provider config / Skill system / sandbox execution engine fully
intact, and (3) is mid-way through systematically testing every remaining
action in the capability matrix, fixing what's broken as it's found.

## Architecture (current, source-verified)

- **Host app**: `com.leslie.teachtrack` — Kotlin + Compose + Material3 +
  Room (schema v7) + MVVM/Repository, single `:app` module.
- **Embedded Agent Runtime**: `com.openminis.app` — the real OpenMinis
  source (GPLv3, imported from `github.com/OpenMinis/OpenMinis` commit
  `4ef2900`, see `OPENMINIS_UPSTREAM.md` for the full import manifest and
  every local modification with reasoning). Runs in the same process,
  merged `AndroidManifest.xml`, `MinisApp` as the single `Application`
  class.
- **The Bridge** (the only new "glue" code, everything else is either
  TeachTrack's pre-existing code or OpenMinis's pre-existing code):
  - `app/src/main/java/com/leslie/teachtrack/agent/TeachTrackOffloadHandler.kt`
    — implements OpenMinis's `NativeOffloadHandler`, registered as
    `"teachtrack"` in `MinisApp.kt` right before
    `NativeOffloadServer.start(...)`. Parses `teachtrack rpc --json '...'`
    / `--file <path>` calls from the Agent's Linux sandbox (PRoot/Alpine),
    builds a `CopilotCommand`, executes it through the *same*
    `CopilotService`/`LocalCopilotActionRegistry` the app's own UI uses
    (OWNER_TRUSTED mode — always `confirmed=true`, since TeachTrack's
    49-action allow-list has no true hard-destructive action), returns
    strict JSON (`{"ok":true,"operation":...,"result":{...}}` /
    `{"ok":false,"error":{"code":...,"message":...}}`).
  - `app/src/main/java/com/leslie/teachtrack/agent/TeachTrackAppContext.kt`
    — singleton holder for the shared `TeachTrackRepository` /
    `LocalCopilotActionRegistry` instances (so the Bridge and the app's own
    UI never build two competing Room databases).
  - `app/src/main/java/com/leslie/teachtrack/agent/AgentSplitPanel.kt` — the
    in-app split-view UI (TeachTrack's own Dashboard on top, a live
    `ChatScreen` from OpenMinis below) reached via the bottom nav's "Agent"
    tab. Handles a real layout bug where the keyboard, sized against the
    *full* window, would crush the compact panel — fixed by collapsing the
    Dashboard to 0 height while the IME is visible (`MainActivity.kt`,
    search `split-view-ime-collapse`).
  - The bundled `teachtrack` **Skill** (`SKILL.md`, content lives as a
    Kotlin string constant `TEACHTRACK_SKILL_CONTENT` in
    `app/src/main/java/com/openminis/app/data/repository/SkillRepository.kt`,
    version currently **1.5.0**, bump this constant's version string
    whenever the content changes or the on-device copy won't update) —
    this is how the Agent discovers what `teachtrack` is and how to call
    it. Read this file's current content before assuming what parameters
    an action needs; several bugs this session were exactly "the Agent
    guessed a parameter name because the Skill didn't document it."

## What got fixed this session (chronological, all real, all
DB-cross-checked unless noted)

Full technical detail for every item below — root cause, exact code diff,
exact verification steps and their raw output — is in `STATE/BACKLOG.md`
under the "OpenMinis Agent Runtime source-level integration (DEC-004)"
section. This is only an index so you know what to search for.

1. **`ANTHROPIC_OAUTH_IDENTIFIER_PROMPT` crash** — 4 unguarded call sites in
   `ChatViewModel.kt` crashed the whole app the first time a message went
   through an Anthropic **OAuth** (not API-key) provider. Fixed with a
   try/catch fallback; does not fabricate the actual private identifier
   value (can't — it's Anthropic's own secret).
2. **`student.update` silently dropped `status`** — the `old.copy(...)`
   call never included `status`, so passing it did nothing while still
   returning a fake success message. A live batch "set 29 UNKNOWN students
   to ACTIVE" reported 38 successes; the real DB was completely unchanged.
   Fixed by adding a `status` parameter (`在读`/`暂停`/`结束`, never
   `已归档` — that's `student.archive`'s job, which has its own audit
   record).
3. **`student.search` had no `status` filter** — accepted but ignored one,
   causing the Agent's own re-verification step to report a wrong final
   count. Added the same status filter as (2).
4. **`knowledge.update`/`knowledge.query` two bugs**: (a) looked up the
   knowledge point by passing `knowledgePointId` straight into a
   *name*-matching DB query, so a UUID (exactly what `knowledge.create`
   hands back) never matched anything; (b) `knowledge.query` scoped to a
   student returned zero actual data, just a navigation-effect message
   that's a no-op over the Bridge (no Compose state to drive there). Fixed
   both — added `knowledgeById()`, and `knowledge.query` now returns real
   progress data as text + `count`.
5. **`communication.create` / `task.status.update` — not code bugs, Skill
   documentation gaps**: both actions need an explicit parameter
   (`summary`, `taskId`) with no usable fallback once called from
   `teachtrack rpc` (their `rawText`-fallback trick only works for
   in-app conversational calls, which have a "sentence" to fall back to —
   the Bridge has none). Documented explicitly in the Skill content.
6. **`task.create` / `problem.create` never returned the new record's
   id** — meant `task.status.update`/`problem.status.update` had nothing
   reliable to reference. Added `data.taskId` / `data.problemId` to both
   creates (mirrors the pattern `knowledge.create` already used).

## Agent Runtime simplification (DEC-004 addendum, user-requested)

User's own words after I raised the conflict with the project's "don't gut
OpenMinis's native capability" constraint and the user overrode it: "码放着
占空间没必要... 你自己决定怎么做，一直开发下去" (paraphrased: it's just
dead weight, use your judgment, keep going without asking).

**Deleted** (57 files total, ~13 more touched only for cross-reference
cleanup, zero business-logic changes): dead `OnboardingScreen.kt`; the
entire `debug/` package (DEBUG-only tooling); `backup/` + rclone remote
sync (kept `BackupFormat.kt` — `ProviderRepository.kt`, a do-not-touch
file, depends on it); the skills-marketplace browser page (not the
skill-loading mechanism); Shizuku + Accessibility CLI integration
(including a `MainActivity.kt` dialog block that referenced it); the
scheduled-task system.

**Never touched, confirmed intact**: `sandbox/` (PRoot execution engine —
this is what `shell_execute` and the `teachtrack` CLI both run on top of),
`tools/`, `browser/` protocol layer + `ui/browser/BrowserSheet.kt` (used
live by the `browser_use` tool inside chat), `ui/terminal/`, `terminal/`,
`service/`, `provider/`, `auth/`, `ProviderRepository.kt`, `SkillRepository.kt`,
the `teachtrack` registration line in `MinisApp.kt`, `crash/`, `config/`,
`mcp/`, `speech/`, `webapp/`, `share/`.

**Not done, deliberately deferred** (see the research report referenced in
`BACKLOG.md` for the full module-by-module breakdown): risk-tier 3
(trimming the "Open Browser"/"Rootfs Management" buttons from
`SessionListScreen`'s toolbar — low risk, just hasn't been done) and
risk-tier 4 (MCP/voice/webapp/share — these are genuinely wired into
`ChatScreen.kt`/`ChatViewModel.kt` itself, not independent modules, so
removing them means editing the chat core directly; the safer path
discussed was passing `null`/feature-flagging rather than physically
deleting files, not yet started).

Verified after combining with the bug fixes above: `gradle
:app:assembleDebug` clean, fresh install with real data intact (41
students + everything created during testing), no crash, full multi-step
Agent conversations complete normally.

## Action-by-action test status (LocalCopilotActionRegistry, 49 actions)

**Tested and confirmed working via real Agent conversation + DB pull**:
`student.create/update/search/archive/overview.query`,
`student.data.organize.preview`, `course.create`, `knowledge.create/update/query`,
`communication.create`, `homework.create`, `problem.create`, `exam.create`,
`task.create/status.update`, `parent_contact.create/query`, `tag.create/assign/query`,
`template.create/list/render`, `analytics.query`, `dashboard.query`.

**Tested, appears to work but not independently cross-checked**:
`navigation.open` — most recent test returned
`{"ok":true,"operation":"navigation.open","message":"已打开模块：首页","result":{"tab":"0"}}`
which reads as a real success. **This directly contradicts an earlier,
unverified assumption recorded in `BACKLOG.md` that `navigation.open`
"doesn't wire back into TeachTrack's own UI state" over the Bridge** — that
earlier note was written before this action was actually exercised through
the Bridge and may simply have been wrong. Whoever picks this up next:
re-test `navigation.open` deliberately (send it, then screenshot the
device to confirm the visible tab actually changed) and correct
`BACKLOG.md`'s older claim either way — don't just trust either note over
the other.

**Not yet tested at all**: `problem.status.update` (code fix applied
alongside `task.status.update`'s, same shape, but not independently
re-run — flagged in `BACKLOG.md` as a quick spot-check to do),
`template.update`, `template.delete`, `attachment.list/query/delete`,
`backup.export/list/restore`, `csv.export.scores`,
`workspace.status.query/export/check/sync.scan/conflict.list/conflict.resolve`,
`score.create` (attempted once early in the session but interrupted by the
OAuth crash bug before completion — never cleanly re-run since),
`score.query`, `course.resume`, `course.timeline`,
`student.followup.tasks.create`, `knowledge.weakness.query`.

## Known, not-yet-fixed issues (real, found this session, low priority)

- **Data quality, pre-existing, not a regression**: seed student
  `seed-student-029` (displayName "永进") has `name=null` — every
  name-based lookup fails to find them since it matches against `name`,
  not `displayName`. Also "汪可" has two same-named seed records
  (`seed-student-007`/`seed-student-022`) — may be two real students who
  share a name, or a duplicate-import artifact; worth a manual look.
- **`OWNER_TRUSTED` has no Settings UI toggle** — it's simply how the
  Bridge always calls `execute()` today (`confirmed=true`, no way to turn
  it off from the app).
- Section 89 Delivery Proof checklist / Section 90
  `TEACHTRACK_AGENT_COVERAGE_REPORT.md` from the original mega-spec —
  never formally generated as a document, though most of the underlying
  capability work behind it is now done and evidenced in `BACKLOG.md`.
- Contextual "Ask Agent" entry points from Student Workspace (spec
  sections 84-85) — not started.
- One open question from the user, never resolved: an early request to
  delete a "kpi 模块" — the exact module meant was never clarified and the
  user never followed up. Don't guess at this; ask if it resurfaces.

## Environment / tooling notes (must-know — saves real time)

- **Build environment is WSL2 Ubuntu-24.04**, not native Windows — there is
  no independent Linux toolchain otherwise. Full SDK/NDK/JDK 17/Gradle
  8.11.1/Go toolchain already installed there this session (see
  `project.yaml`'s `environment_manifest` for exact versions/paths).
- **The canonical rebuild command**, reusable as-is:
  ```
  MSYS_NO_PATHCONV=1 wsl -d Ubuntu-24.04 -- bash -c "sed -i 's/\r$//' <script> && bash <script>"
  ```
  A ready-made script exists in this session's scratchpad
  (`fix_keystore_rebuild.sh`) that sets `JAVA_HOME`/`ANDROID_HOME`/NDK/PATH,
  copies the Windows-side `debug.keystore` into WSL (**do not let WSL
  generate its own** — the signature mismatch causes
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, which is NOT a reason to
  uninstall/lose real student data), and runs
  `gradle :app:assembleDebug --no-daemon --stacktrace`, logging to
  `/root/build-teachtrack.log` inside WSL. Always run with
  `run_in_background: true` (takes 30s–3min) and tail that log afterward
  for `BUILD SUCCESSFUL`/`BUILD FAILED` — **never trust the outer script's
  own stdout, it only prints the keystore fingerprint**, the real result is
  in that log file.
  - **Never run a second build while one from a subagent/fork is still
    mid-flight** — they share the same working tree and WSL Gradle daemon;
    a concurrent build will show unrelated `Unresolved reference` errors
    from the *other* build's half-finished edits. Wait for one to fully
    finish first.
- **`adb.exe` location**: `D:/Leslie/Tools/platform-tools/adb.exe` (not on
  PATH in either Bash or PowerShell tool environments — always invoke by
  full path). Device connects fine; `adb devices` confirms `device` state.
- **To pull and inspect the real on-device database** (the only reliable
  way to verify a write actually happened, since the Agent's own chat
  reply can't always be trusted at face value — see bug #2 above):
  ```
  adb exec-out run-as com.leslie.teachtrack cat databases/teachtrack.db > local.db
  adb exec-out run-as com.leslie.teachtrack cat databases/teachtrack.db-wal > local.db-wal
  adb exec-out run-as com.leslie.teachtrack cat databases/teachtrack.db-shm > local.db-shm
  ```
  All three files are required together (WAL mode) — pulling just the
  `.db` file shows stale data. Then, in WSL (`sqlite3` installed this
  session via `apt-get install sqlite3`):
  `sqlite3 local.db 'PRAGMA wal_checkpoint;' && sqlite3 local.db "SELECT ..."`.
- **`adb shell input text` cannot type Chinese characters** (throws a
  `NullPointerException` inside the Android input shell command) and
  chokes on shell-special characters (`(`, `)`, `;`, unescaped quotes) —
  test instructions sent to the Agent this whole session were written in
  English with `%s` for spaces and no parens/semicolons for exactly this
  reason. To clear a partially-wrong text field, `input keyevent 67`
  (backspace) in a loop works; there's no reliable "select all" from
  `adb shell`.
- **Screenshot coordinates need a 1.19× scale factor**: device is
  1080×2376 real pixels, screenshots display at 909×2000 in this tool's
  viewer — multiply any coordinate read off the *displayed* image by 1.19
  before passing to `adb shell input tap`. Forgetting this was the source
  of several wasted taps this session.
- A `CronCreate` recurring job (`2644f59e`, every 25 min, session-only,
  auto-expires in 7 days) is currently active, re-prompting whoever is
  running this session to keep going with this exact test-fix-verify
  loop. If a fresh AI picks this up in a **new** session, that cron job
  does not carry over — recreate one if the same "don't stop, keep
  testing" cadence is still wanted, or just proceed manually.

## How to continue (the actual next steps, in priority order)

1. Re-verify `navigation.open` deliberately and correct whichever
   `BACKLOG.md` note turns out to be wrong (see above).
2. Spot-check `problem.status.update` (code fix applied, never
   independently re-run).
3. Work through the untested action list above the same way every prior
   one was tested: real Agent conversation on-device → check the raw
   `teachtrack rpc` JSON result → pull the real SQLite DB → fix any real
   bug found → rebuild → reinstall → re-verify → write it up in
   `BACKLOG.md` with the same level of evidence (exact JSON, exact SQL
   query, exact result row).
4. Once the action list is exhausted, decide whether to continue the
   deferred risk-tier 3/4 simplification work, or move on to the
   mega-spec's still-open items (Settings toggle for OWNER_TRUSTED,
   Student Workspace "Ask Agent" entry points, the formal coverage
   report document).
5. The user's standing instruction for this whole workstream: don't stop
   to ask before continuing routine testing/bug-fixing/verification — only
   stop for a genuine STOP condition (needs the user's own account auth,
   an API key that can't be generated, physical-device interactive auth,
   or an external private resource with no substitute). See the
   `teachtrack-agent-autonomous-testing` memory (if this AI has access to
   the same memory store) or just take this paragraph as the standing
   authorization.
