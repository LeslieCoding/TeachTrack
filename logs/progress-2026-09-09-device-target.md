# TeachTrack Development Log — 2026-09-09 / Device Target Switch

## Change

The user explicitly switched all subsequent physical-device tests from OnePlus to OPPO Pad because the OnePlus is needed for other use.

## Action

- Stopped the in-progress OnePlus connected instrumentation command.
- Checked `adb devices -l` twice after the switch.
- Confirmed only OnePlus `92f73a16` is currently visible; OPPO Pad is not yet exposed by ADB.
- Updated project state, decision log, and V2 verification matrix to require OPPO Pad for new physical evidence.

## Safety

No further test, install, screenshot, or UI operation was sent to OnePlus after the user switch.

## Next

When OPPO Pad appears in `adb devices`, install `TeachTrack-debug.apk`, verify the displayed `2.0.0-alpha.1` version, run the 1.x regression suite, then begin V2 UI tests on the pad only.
