# TeachTrack Development Log — 2026-09-08 / Round 20

## Objective

Continue physical-device regression testing after the OnePlus phone reconnected. The OPPO Pad remains untouched.

## Install verification

- Target: OnePlus PKG110 / `92f73a16`
- ADB connection: PASS
- Debug APK install: PASS after automatically handling the ColorOS unknown-source confirmation screen
- During this reinstall cycle, the prior manual test record `RoundOne` was not present after launch. The cause is not yet proven; it may be a device install-data reset or a changed application data state.
- This observation is recorded as a data-preservation risk rather than being marked as a pass.

## Fresh regression workflow

1. Launched the app and confirmed the Student page had a functional empty state, search field, status filters, sorting control, and add-student action.
2. Created `RegressionStudent` with the add-student dialog.
3. Confirmed the new student workspace opened and exposed `开始新课程` and `录入成绩` actions.
4. Saved a course record through the quick-resume dialog.
5. Saved `RegressionExam`, score `91`, full score `100`.
6. Student workspace UI dump showed `91%` and `91.0/100.0`.
7. Force-stopped and relaunched the app.
8. Dashboard showed `RegressionStudent`, a course record, and one in-study student after process death.

## Evidence

- `artifacts/screenshots/oneplus-regression-score.png`
- `artifacts/screenshots/oneplus-regression-relaunch.png`
- `artifacts/oneplus-regression-score-saved.xml`
- `artifacts/oneplus-regression-relaunch.xml`

## Result

Fresh data capture, course save, score save, score-rate calculation, process restart, and dashboard reload all passed. The only unresolved observation is preservation of older data across this particular reinstall event; no existing user data was deliberately deleted by the test commands.
