# OpenMinis Upstream Tracking

TeachTrack vendors the Android Agent Runtime from OpenMinis at the source
level (LAEP MASTER TASK / `LAEP/STATE/DECISIONS.md` DEC-004). This file
records exactly what was imported and from where, so a future session can
diff against upstream instead of losing provenance.

- **Repository**: https://github.com/OpenMinis/OpenMinis
- **Branch**: `main`
- **Commit**: `4ef29002e88db1e20e462ec2ff46916e8a7dcb45` (2026-09-02 02:19:16 +0800)
- **Import date**: 2026-09-12/13
- **License**: GPLv3 (because it links iSH/GPLv3 and PRoot/GPLv2) — see
  `THIRD_PARTY_NOTICES/OpenMinis_LICENSE` and
  `THIRD_PARTY_NOTICES/OpenMinis_THIRD_PARTY_LICENSES.md`, both vendored
  verbatim and kept alongside this file for the life of the integration,
  per DEC-004 / spec sections 87-88.

## What was imported

- `src/android/app/src/main/java/com/openminis/**` → `app/src/main/java/com/openminis/**`
  (477 Kotlin files, unmodified except the namespace fixes below).
- `src/android/app/src/main/cpp/**` → `app/src/main/cpp/**` (pty_bridge, native
  crash handler, jieba_jni + vendored cppjieba).
- `src/android/app/src/main/jniLibs/arm64-v8a/**` → `app/src/main/jniLibs/arm64-v8a/**`
  (`libproot.so`, `libproot-loader.so`, `libproot-loader32.so` — built by
  `deps-openminis/build_proot.sh`, the two loaders are vendored/sha256-pinned
  by that script, not built from source).
- `src/android/app/src/main/assets/**` → `app/src/main/assets/**` (katex,
  jieba dictionaries, `models-dev-api.json`, `proot-aarch64` binary, the
  Alpine 3.21.3 aarch64 minirootfs tarball, `default_mount/`).
- `src/android/app/src/main/res/**` → `app/src/main/res/**`, EXCEPT:
  - all `mipmap-*/` directories (icon identity — this app has exactly one
    launcher icon, TeachTrack's own; OpenMinis's dynamic-icon-switching
    feature and its app-shortcuts were dropped, see below).
  - `values/ic_launcher_background.xml`, `values/ic_launcher_alt_colors.xml`
    (icon identity, same reason).
  - `xml/shortcuts.xml` (only referenced by the dropped app-shortcuts feature).
  - Copied `values*/colors.xml|strings.xml|themes.xml` were renamed to
    `minis_colors.xml` / `minis_strings.xml` / `minis_themes.xml` to avoid
    ever colliding with a same-named TeachTrack file at copy time (resource
    entries merge by name regardless of filename, so this is cosmetic/safety
    only, not a functional rename of any `R.` symbol).
- `src/android/app/schemas/com.openminis.app.data.db.AppDatabase/` → `app/schemas/`
  (OpenMinis's own Room database schema history; TeachTrack's own Room
  database is a separate, untouched `TeachTrackDatabase` — the two coexist
  as independent Room databases in one app, per DEC-004's Gradle-merge notes).
- `src/android/app/provider-customization.properties(.example)` → `app/`.
- `src/android/app/libs/rclone.aar` → `app/libs/rclone.aar` (built locally
  via `deps-openminis/build_proot.sh`'s sibling script, see below — not a
  checked-in upstream binary).
- `src/shared/bashism/*.json` → `shared/bashism/` (the on-demand bashism
  rule tables `copyBashismRules` stages into assets at build time).
- `deps/build_proot.sh`, `deps/talloc/`, `deps/proot/` (the vendored fork
  source, for reproducing the native cross-compile later),
  `scripts/prepare_android_sandbox.sh` → `deps-openminis/`.
- `LICENSE`, `THIRD_PARTY_LICENSES.md` → `THIRD_PARTY_NOTICES/OpenMinis_*`.

**Not imported** (out of scope for this Android integration): `src/ios/`,
`deps/ish/` (iOS-only Linux kernel), `src/shared/` beyond the bashism tables,
`scripts/*.py` locale tooling, `docs/`, `.claude/skills/debug-server/` (the
debug-only `stageDebugSkillAssets` Gradle task is kept but resolves to a
graceful no-op via its own `onlyIf { script.exists() }` guard since neither
the script nor the skill directory were vendored).

## Local modifications (why they were necessary, not stylistic)

1. **Namespace fix (R / BuildConfig)**: OpenMinis's own Gradle module used
   `namespace = "com.openminis.app"`; TeachTrack's module namespace is
   `com.leslie.teachtrack`, so there is exactly one generated `R`/`BuildConfig`
   class for the whole merged module, at TeachTrack's namespace. Every
   `import com.openminis.app.R` / `.BuildConfig`, every fully-qualified
   `com.openminis.app.R.xxx` / `.BuildConfig.xxx` reference, and every file
   that referenced `R.xxx` bare via same-package implicit resolution was
   rewritten to resolve against `com.leslie.teachtrack.R` / `.BuildConfig`.
   Pure mechanical fix; no behavior change.
2. **Dropped the dynamic-app-icon / app-shortcuts feature**
   (`AppearanceScreen.kt`'s "App Icon" `SettingsSection`, the three
   `<activity-alias>` entries + `<meta-data android:resource="@xml/shortcuts">`
   in the manifest): this app has exactly one launcher icon and identity
   (TeachTrack's), per spec sections 3/7/83 — OpenMinis's `MainActivity` is
   reached only via an explicit in-app Intent from TeachTrack's own "Agent"
   nav item (`MainActivity.kt`'s `openAgent()`), not as a second launcher
   icon. The underlying feature (switching between OpenMinis's own icon
   variants) has no meaning once there is no separate OpenMinis launcher
   icon to switch, so it was removed rather than left dangling and broken.
3. **`android:exported="false"`** on `com.openminis.app.MainActivity` (it
   was `true` upstream, since upstream launches it from the OS launcher).
   Reachable only from inside this app's own process.
4. **Manifest merge**: TeachTrack's `<application>` attributes win where
   identity/policy differs (`icon`, `label`, `allowBackup="false"`,
   `theme="@style/Theme.TeachTrack"` on TeachTrack's own activities);
   OpenMinis's functional `<application>` flags required for the sandbox to
   work are kept (`networkSecurityConfig`, `usesCleartextTraffic`,
   `extractNativeLibs="true"`, `localeConfig`, `largeHeap`,
   `requestLegacyExternalStorage`/`preserveLegacyExternalStorage`,
   `enableOnBackInvokedCallback`). `android:name` is
   `com.openminis.app.MinisApp` — TeachTrack had no existing custom
   `Application` subclass to reconcile with.
5. **Gradle**: `compileSdk` 35→36, `minSdk` 24→26 (OpenMinis's floor —
   drops Android 7.0/7.1, judged negligible for a single-teacher personal
   app whose only acceptance device runs Android 16), Kotlin 2.0.21→2.1.0,
   KSP 2.0.21-1.0.28→2.1.0-1.0.29, Compose BOM 2024.12.01→2025.09.00, added
   `org.jetbrains.kotlin.plugin.serialization`, NDK r28 (`28.0.12433566`) +
   CMake 3.22.1 + `externalNativeBuild` wired in, `packaging.jniLibs.useLegacyPackaging = true`
   (required by `extractNativeLibs=true`), JitPack + a `flatDir { dirs("app/libs") }`
   repository added to `settings.gradle.kts` (for the VAD library and the
   local `rclone.aar`, matching OpenMinis's own settings file exactly).
   AGP stayed `8.7.3` — already an exact match with TeachTrack's existing
   version, no change needed.
6. **`openAgent()`'s Intent bug** (found and fixed via real on-device
   testing, not by inspection): the first version called `startActivity`
   on `LocalContext.current.applicationContext` without
   `FLAG_ACTIVITY_NEW_TASK`, which crashes at runtime
   (`AndroidRuntimeException`) — starting an Activity from a non-Activity
   Context always requires that flag. Fixed by adding the flag.

## Verification performed against this import (see `LAEP/STATE/BACKLOG.md`)

Standalone OpenMinis baseline (before merge) and the merged TeachTrack+OpenMinis
app (after merge) were both built from source and run on real hardware —
not just compiled. See `BACKLOG.md`'s OpenMinis integration section for the
full evidence trail (screenshots, device serials, exact commands).
