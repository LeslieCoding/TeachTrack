# TeachTrack Test Record: Round 5 (2026-09-14)

## Fresh build evidence

- `:app:assembleRelease --no-daemon --console=plain`: **BUILD SUCCESSFUL** in 5m56s.
- `:app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`: **BUILD SUCCESSFUL** in 4m31s.
- Parsed JVM XML results: **28 tests, 0 failures, 0 errors**.

## Device evidence retained for this round

- Target: OnePlus `92f73a16` / PKG110.
- Latest database instrumentation evidence: `TeachTrackDatabaseInstrumentedTest`, **OK (27 tests)**.
- Post-test main-app smoke: the installed `com.leslie.teachtrack` package opens to the real dashboard; no fatal exception was captured; real app data was preserved.

## Explicit limitations

- The AndroidTest companion APK is currently blocked by the ColorOS package-risk screen. Therefore the Compose/full instrumentation test was **not re-run** in this round.
- OPPO Pad is not currently connected. No new tablet orientation or visual regression is claimed.
- The Release APK is debug-signed by the current Gradle release configuration. It is a build-verification/delivery candidate, not a production-signed artifact.
- Real external provider/OAuth testing needs the owner's account authorization or API credentials, which were not requested or stored.
- Three new workspace end-to-end instrumentation tests (two-device conflict + resolution, duplicate-event idempotence, and Room A -> backup -> fresh Room B restoration) compile successfully but are **not device-executed**: WSL cannot replace a Windows-locked generated AndroidTest APK, and the connected ColorOS device separately blocks companion test-package installation.

## Isolation-build retry

- An opt-in isolated Gradle output script, `scripts/isolated-build.init.gradle`, was added so a Windows-locked default `app/build` directory cannot prevent test packaging.
- The isolated command completed successfully: `:app:assembleDebug :app:assembleDebugAndroidTest` in 5m13s, producing fresh Debug and AndroidTest APKs outside the locked default output tree.
- The fresh main Debug APK installed silently over the existing OnePlus app. AndroidTest installation then reached the ColorOS system risk page; UIAutomator evidence shows it only exposes `退出安装` and no continue/allow action. Automation dismissed that page and restored `com.leslie.teachtrack/.MainActivity` successfully. Therefore expanded instrumentation remains **BUILT / NOT_DEVICE_EXECUTED**, not falsely reported as passed.

## Raw evidence

- `artifacts/oneplus-androidtest-20260914.txt`
- `artifacts/oneplus-final-ui-20260914.xml`
- WSL Gradle daemon logs under `/root/.gradle/daemon/8.11.1/` for the two fresh builds.
