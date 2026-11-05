# KNOWN_ISSUES

Historical known issues are in `AI_SPEC/22_KNOWN_ISSUES.md` /
`AI_SPEC/27_KNOWN_ISSUES.md`. This file records issues discovered after
LAEP governance began (2026-09-12); resolved items are kept with their
resolution noted rather than deleted, so the audit trail survives.

- KI-001 — RESOLVED (2026-09-12): `LocalCopilotActionRegistry.allowedActions`
  included `student.overview.query`, but `execute()`'s `when` block had no
  matching branch, so a match fell through to
  `"unsupported action: ${command.action}"`. Neither the offline parser
  (`FakeCopilotProvider`) nor the cloud System Prompt ever produced this
  action, so it was dormant rather than an active bug. Fixed in B-01: added
  a real single-student overview implementation (profile, latest course,
  recent scores, pending homework, open problems, weak knowledge points);
  JVM parser test and androidTest execution test both added and passing.
- KI-002 — RESOLVED (2026-09-12): `README.md`'s top-of-file "Current
  version" read `2.0.0-alpha.1`, while the "3.0 delivery status" section
  further down the same file and `AI_SPEC/24_PROJECT_STATE.md` both
  confirmed the active line was `3.0.0-alpha.1`. Fixed in B-14: header now
  reads `3.0 · TeachTrack Copilot / 3.0.0-alpha.1` and links `V2-REQ-040`.
- KI-003 — RESOLVED (2026-09-12): OnePlus `92f73a16` instrumentation
  installs were hanging/failing (`ShellCommandUnresponsiveException` inside
  `SplitApkInstaller.installCommit`, "Failed to install split APK(s)",
  consistently across `gradlew connectedDebugAndroidTest` and plain
  `adb install`). Root cause found via logcat (`OplusInstallAccelerateManager`
  / `com.android.packageinstaller/.oplus.service.AppInstallerService`) plus a
  controlled experiment (`adb push` of the same APK completed in 0.59s at
  27.6 MB/s, ruling out USB/transfer speed; `pm install` of an
  already-on-device file still hung, isolating the problem to the on-device
  installer): **ColorOS shows its own "涉及敏感权限/暂未发现风险" scan-and-
  confirm screen (`com.oplus.appdetail`) for every install from an "Unknown
  source: computer" (i.e. ADB), and blocks until a human taps "继续安装"**.
  A fully silent `adb install -r -d`/UTP install has no one there to tap it,
  so the install sits at that screen until Android's background-service
  execution limit kills the installer's service and the session fails.
  Disabling `com.oplus.stdsp`, Doze-whitelisting, and temp-whitelisting
  `com.android.packageinstaller` did not help (the screen still requires an
  explicit tap regardless of power-management state); a device reboot did
  not help either. The only fix is to actually tap through the dialog:
  after starting the install, `uiautomator dump` and locate
  `com.oplus.appdetail:id/btn_left` ("继续安装"; bounds were `[72,1992][528,2124]`
  on this device's current resolution/scale, but re-dump per session rather
  than hard-coding coordinates) and `adb shell input tap` it; repeat for the
  androidTest APK's own confirmation screen, and for the one-time
  "«App» wants to open «test package»" instrumentation cross-launch
  permission dialog (choose "30天内允许" to avoid repeating it soon). With
  that tap-through, `gradlew :app:connectedDebugAndroidTest` completed
  cleanly and all 26 androidTest cases passed. This is a manual-gesture
  requirement of ColorOS's own security posture for non-Play-Store installs,
  not something `adb`/Gradle flags can bypass — future sessions installing
  to this device should expect and tap through it rather than re-diagnose
  from scratch or assume a transfer/timeout problem.
- KI-004 — RESOLVED (2026-09-12): `core/util/TemplateRenderer.kt`'s regex
  `Regex("\\{\\{([a-zA-Z0-9_]+)}}")` has an unescaped closing `}}`. Desktop
  JVM's regex engine (used by the pre-existing JVM test
  `TemplateRendererTest.kt`, which passed) tolerates this, but Android's ICU
  regex engine does not: any real on-device call into `TemplateRenderer`
  threw `PatternSyntaxException` wrapped in `ExceptionInInitializerError`.
  This was a **pre-existing latent crash bug**, not introduced this round —
  it would have crashed the manual "使用模板" (use template) UI feature too,
  the first time any real user tapped it on a device; it was only ever
  covered by a JVM test that could not reproduce the Android-specific regex
  behavior. Found via the new `template.render` androidTest case (the first
  code path, AI or manual, to actually exercise this class on a real
  device). Fixed by escaping the closing braces:
  `Regex("\\{\\{([a-zA-Z0-9_]+)\\}\\}")`. Both the JVM test and the new
  androidTest case pass after the fix.
