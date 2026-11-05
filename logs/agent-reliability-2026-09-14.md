# Agent Reliability Regression — 2026-09-14

Task: `T4-AGENT-RELIABILITY-001`

## Scope

1. Keep historical students with partial recorded names searchable through
   `displayName`; do not infer a missing surname.
2. Prevent the OpenMinis `teachtrack rpc --file` bridge from reading outside
   its PRoot rootfs.
3. Ensure instrumentation tests do not clear a teacher's real Workspace SAF
   grant merely to test the disconnected branch.

## Code and tests

- `TeachTrackDao.searchStudents`: queries `name` or `displayName`.
- `TeachTrackOffloadHandler.resolveRootfsFile`: canonicalizes a guest path
  and enforces canonical containment under `rootfsDir`.
- `TeachTrackDatabaseInstrumentedTest`:
  - `displayNameOnlyHistoricalStudentIsSearchableAndResolvable`
  - `agentRpcFilePayloadCannotEscapeRootfs`
  - isolated preference context for
    `workspaceActionsGuideUserWhenNoWorkspaceIsConnected`

## Evidence

- WSL Gradle: `:app:testDebugUnitTest :app:assembleDebug` — passed
  (2026-09-14).
- WSL Gradle: `:app:assembleDebugAndroidTest` — `BUILD SUCCESSFUL in 38s`.
- OnePlus `92f73a16`: installed Debug and AndroidTest APKs in place, then
  executed `TeachTrackDatabaseInstrumentedTest` with AndroidJUnitRunner.
  Result: `OK (27 tests)` in 2.553 seconds.
- Raw runner output: `artifacts/oneplus-androidtest-20260914.txt`.
- Post-test app smoke: `MainActivity` focused, no `FATAL EXCEPTION` in the
  captured logcat window, and UI dump shows the existing dashboard with
  41 active students: `artifacts/oneplus-final-ui-20260914.xml`.

## Data safety

- Main package `com.leslie.teachtrack` was never uninstalled.
- The AndroidTest companion package was removed after the passing run; this
  does not remove or alter the main app's data.
