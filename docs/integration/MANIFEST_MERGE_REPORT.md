# Manifest Merge Report (spec §80)

Date: 2026-09-27. Scope: `app/src/main/AndroidManifest.xml` — the merged
manifest of the single `:app` module that combines TeachTrack (host app) and
the vendored OpenMinis Agent runtime (`com.openminis.*`, see
`OPENMINIS_UPSTREAM.md`). Sources compared: TeachTrack's original manifest
(one launcher activity, RECORD_AUDIO + INTERNET, OAuth callback) and the
upstream OpenMinis manifest as documented in `OPENMINIS_UPSTREAM.md` §1-6.

## Identity / `<application>` attributes

| Attribute | Winner | Reason |
|---|---|---|
| `android:name` | `com.openminis.app.MinisApp` (OpenMinis) | TeachTrack never had an `Application` subclass; MinisApp owns repositories, offload server, crash guards. |
| `icon` / `roundIcon` / `label` | TeachTrack (`@mipmap/ic_launcher`, "师迹 · TeachTrack") | One app identity — spec §3/§7. |
| `allowBackup="false"` | TeachTrack | Host-app data policy. |
| `theme` | `@style/Theme.TeachTrack` at app level; `@style/Theme.Minis` per-activity on OpenMinis activities | Host identity wins globally; Agent screens keep their own theme where upstream requires it. |
| `networkSecurityConfig`, `usesCleartextTraffic="true"` | OpenMinis | Sandbox localhost/PRoot traffic requires cleartext to local endpoints. |
| `extractNativeLibs="true"` + `packaging.jniLibs.useLegacyPackaging` | OpenMinis | PRoot loads `.so` files from disk paths, not the APK zip. |
| `largeHeap`, `requestLegacyExternalStorage`, `preserveLegacyExternalStorage`, `localeConfig`, `enableOnBackInvokedCallback` | OpenMinis | Agent runtime requirements (model buffers, legacy storage for sandbox file bridging, per-app language). |
| `dataExtractionRules="@xml/data_extraction_rules"` | merged (present in app res) | Backup opt-out rules for Android 12+ device transfer. |

## Permissions

Merged as a union with attribution comments in the manifest:

- **TeachTrack's own**: `RECORD_AUDIO`, `INTERNET`.
- **OpenMinis Agent runtime**: `REQUEST_INSTALL_PACKAGES`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `SYSTEM_ALERT_WINDOW`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`,
  `POST_PROMOTED_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE`, calendar/contacts/location
  pairs, media reads (`READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`,
  `ACCESS_MEDIA_LOCATION`), legacy storage (`READ/WRITE_EXTERNAL_STORAGE` with
  `maxSdkVersion`, `MANAGE_EXTERNAL_STORAGE`), `CAMERA`, `SET_ALARM`.
- `<uses-feature android.hardware.camera.any required="false">` — keeps the
  app installable on camera-less devices; camera is an optional chat input.
- Conflict resolution: no permission conflicts existed (TeachTrack's two are a
  strict subset of what OpenMinis also needs — `INTERNET`/`RECORD_AUDIO`).

## `<queries>` (package visibility)

Union of both: TeachTrack's `VIEW https` browser query, plus OpenMinis's
speech-recognition, CustomTabs, alarm/timer and `SENDTO mailto` queries.

## Activities

| Component | Disposition |
|---|---|
| `com.leslie.teachtrack.MainActivity` | Sole `MAIN`/`LAUNCHER` entry. Also holds the `teachtrack://oauth/callback` deep link (TeachTrack's own OAuth). `exported="true"` (launcher requirement). |
| `com.leslie.teachtrack.ExternalWebActivity` | `exported="false"`, in-app web fallback. |
| `com.openminis.app.MainActivity` | **Conflict resolved**: upstream had `MAIN/LAUNCHER` + `exported="true"` + dynamic-icon activity-aliases + app-shortcuts. Merged: launcher intent-filter **dropped**, aliases and shortcuts **dropped**, `exported="false"`, `singleTask` kept, `minis://` deep-link filter kept (still functional for in-app/OS-originated VIEWs that resolve to the same package), `configChanges`/`windowSoftInputMode`/`resizeableActivity` kept. Reached via explicit in-app Intent from TeachTrack (`openAgent()`), and its full UI is additionally embedded in the Agent tab (`AgentHost`, P1). |
| `ShareReceiverActivity` | `exported="true"` with the full upstream SEND/SEND_MULTIPLE/VIEW mime filters — system share-sheet entry into the Agent. Required for parity. |
| `WebAppActivity` | kept upstream (`singleInstance`, own taskAffinity, custom action filter) — pinned web-app windows. |
| `OAuthRedirectActivity` | kept upstream (`exported="true"`, six localhost callback ports for Agent model-provider OAuth). `Theme.NoDisplay`, `singleTask`. |

## Services / receivers / providers

| Component | Disposition |
|---|---|
| `AgentForegroundService` | kept; `foregroundServiceType="mediaPlayback"` + matching permission (this is the keep-alive service `SessionActivityTracker` drives; also hooked from the embedded `AgentHost`). |
| `AlarmReceiver` (BOOT_COMPLETED) | kept `exported="true"` — reschedules alarms after boot. |
| `ScheduledNotificationReceiver` | kept `exported="false"`. |
| `MinisNotificationListenerService` | kept `exported="true"` with the required `BIND_NOTIFICATION_LISTENER_SERVICE` permission filter. |
| `androidx.core.content.FileProvider` | single merged instance, authority `${applicationId}.fileprovider`, `@xml/file_provider_paths`. No conflict: TeachTrack previously had none. |

## Dropped (deliberate, with reasons)

1. **OpenMinis launcher icon / activity-alias trio / `xml/shortcuts`
   meta-data** — one launcher identity (spec §3/§7/§83); the dynamic app-icon
   feature is meaningless without a second launcher entry. See
   `OPENMINIS_UPSTREAM.md` §2.
2. **`exported="true"` on `com.openminis.app.MainActivity`** — now in-app only
   (`OPENMINIS_UPSTREAM.md` §3). Note: `minis://` deep links from *other* apps
   therefore no longer resolve; in-process deep links (widget/notification/
   quick-action paths run inside this app) are unaffected.
3. Nothing else: every permission, service, receiver, provider and query the
   standalone OpenMinis manifest declared is present.

## Verification

- Merged manifest inspected at
  `.build-isolated-androidtest*/app/intermediates/merged_manifest/...` during
  prior on-device runs (27/27 OnePlus instrumentation,
  `artifacts/oneplus-androidtest-20260914.txt`) and this phase's
  `:app:assembleDebug` (BUILD SUCCESSFUL, manifest merger reported no
  conflicts).
- Runtime smoke evidence for the merged components lives in
  `LAEP/STATE/BACKLOG.md` (OpenMinis integration section).
