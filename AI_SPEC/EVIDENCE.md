# TeachTrack Evidence Index

## Build evidence

- `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest`: PASS; latest artifacts are under `artifacts/apk/`.
- LAEP audit: PASS, no broken links or missing files.

## OPPO Pad evidence (`2d64f765`)

- `artifacts/screenshots/oppo-v2/final.png`: current dashboard after installation.
- `artifacts/screenshots/oppo-v2/copilot-open.png`: Copilot input and microphone affordance.
- `artifacts/screenshots/oppo-v2/after-preview-tap.png`: parsed `task.create` preview and confirmation.
- `artifacts/screenshots/oppo-v2/copilot-confirmed-fixed.png`: confirmed local task write; Dashboard pending count is 1.
- `artifacts/screenshots/oppo-v2/copilot-persisted.png`: pending count remains 1 after force-stop/relaunch.
- Manual `am instrument -w -r com.leslie.teachtrack.test/androidx.test.runner.AndroidJUnitRunner`: `OK (3 tests)`.

## Limits

The evidence does not prove real cloud-provider behavior, voice transcription, release signing, complete Action Registry semantics, or two complete 00-09 physical regression rounds. Those remain explicit open items.
