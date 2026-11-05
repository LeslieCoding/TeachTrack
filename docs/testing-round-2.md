# TeachTrack 第二轮测试记录

日期：2026-09-09

设备：OPPO Pad，型号 OPD2101，ADB serial `2d64f765`。

## 已验证

| 流程 | 结果 | 证据 |
|---|---|---|
| 安装当前 Debug APK | PASS | OPPO ADB `install -r` 返回 `Success` |
| 启动首页 | PASS | `artifacts/screenshots/oppo-v2/final.png` |
| OPPO 横屏布局 | PASS | 2560×1600 截图，绿色主题、卡片和导航可见 |
| Copilot 入口打开 | PASS | `artifacts/screenshots/oppo-v2/copilot-open.png`、UI dump |
| Copilot 输入框显示 | PASS | `artifacts/screenshots/oppo-v2/copilot-open.png` |
| 学生编辑/备注保存 | PASS | `artifacts/screenshots/oppo-v2/students-edit.png`、OPPO UI dump 重新读回备注 |
| 学生归档确认 | PASS（未最终确认） | OPPO UI dump `归档学生？` / `确认归档` |
| 学生混合时间线空状态 | PASS | `artifacts/screenshots/oppo-v2/student-timeline-scroll.png` |
| Dashboard 快速开始与真实信息区 | PASS | `artifacts/screenshots/oppo-v2/dashboard-insights-final.png`、UI dump |
| SAF 备份导出 | PASS | OPPO DocumentsUI 保存 `TeachTrack-Backup.json`，返回“备份已保存” |
| SAF 备份恢复预览与二次确认 | PASS | 预览显示 `2026-09-09 06:09`、2 条核心记录；确认后显示“已合并导入 2 条记录” |
| OPPO-only instrumentation | PASS | `OK (4 tests)`，4.696s |
| 版本构建 | PASS | `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest` |

## 未通过/待继续

- 初次点击因 ColorOS 键盘改变按钮 bounds 未命中；按 UI dump bounds 重试后，识别预览、确认执行和本地待办写入均通过。
- 语音入口已申请运行时麦克风权限并接入系统 `RecognizerIntent` 边界；OPPO Pad 权限成功，但 ColorOS 识别服务返回空结果，端到端语音验收仍待系统服务条件满足。
- 备份恢复已在 OPPO 文件选择器完成一次完整端到端回归；仍需在第二轮真实教师流程中再次覆盖异常文件和取消路径。
- 2.0 全模块 Action Registry、Provider 设置和审计/撤销尚未完成。

- Copilot 当前已验证的端到端动作：`task.create`；本地适配器已覆盖其他模块写入，但尚未完成每个动作的物理验收。

## Device boundary update

The user switched the active physical test target to OnePlus serial `92f73a16`. Earlier OPPO Pad evidence remains historical and is not invalidated; all subsequent installs, tests, shell commands, and screenshots use OnePlus only.

## OnePlus active regression supplement

| Flow | Result | Evidence |
|---|---|---|
| Latest Debug APK install | PASS | OnePlus ADB `install -r` returned `Success` |
| Latest AndroidTest APK install | PASS | OnePlus ADB `install -r` returned `Success` |
| Instrumentation | PASS | `OK (4 tests)` in 47.898s |
| Dashboard greeting restored | PASS | UI dump contains `早上好，老师` and `今天关注谁？`; screenshot `artifacts/screenshots/oneplus-v2/home-greeting-fixed.png` |
| Dashboard insight layout | PASS | Greeting, 今日课程, 快速开始, 待跟进提醒 and 最近考试 render in one scroll flow |

The greeting regression was caused by Dashboard insights being emitted as an overlay sibling; the fix moved them into the dashboard content flow.

## OPPO Pad 第二轮补测追记（历史证据）

| Flow | Result | Evidence |
|---|---|---|
| Cancel backup save picker | PASS | Returned to Settings without showing backup-saved confirmation |
| Export scores CSV | PASS | DocumentsUI showed `TeachTrack-Scores.csv`; after Save, app showed `成绩 CSV 已导出` |
| CSV export screenshot | PASS | `artifacts/screenshots/oppo-v2/csv-export-result.png` |
| Invalid backup file rejection | PASS | OPPO showed `恢复失败，请确认文件格式`; `artifacts/screenshots/oppo-v2/invalid-backup-result.png` |
| Provider settings UI | PASS | OPPO showed vendor/endpoint/model/timeout fields, security boundary, and `离线 Provider 可用` |

A second full teacher workflow round remains open.

## Configurable AI provider supplement

| Check | Result | Evidence |
|---|---|---|
| Optional provider fields visible | PASS | `artifacts/screenshots/oneplus-v2/teachtrack-provider-fields.png`; UI dump contains vendor, Endpoint, Model, timeout, API Key, save and connection controls |
| API key safety boundary visible | PASS | Same screenshot/UI dump states Android Keystore encryption and excludes Room/backup/log/screenshot storage |
| No-key behavior | PASS | Provider defaults remain disabled and the app launches into offline Copilot; no credential was entered |
| Cloud success | NOT CLAIMED | Requires user-selected vendor endpoint/model/API Key; no external credential was available |
| Build/install/instrumentation after provider adapter | PASS | OnePlus Debug + AndroidTest install `Success`; instrumentation `OK (4 tests)` in 48.01s |

## OAuth login supplement

| Check | Result | Evidence |
|---|---|---|
| OAuth settings card visible | PASS | `artifacts/screenshots/oneplus-v2/oauth-settings.png` |
| PKCE configuration fields visible | PASS | `artifacts/screenshots/oneplus-v2/oauth-expanded.png`; UI dump contains Authorization Endpoint, Token Endpoint, Client ID, Scopes, Redirect URI and 开始登录 |
| Callback boundary registered | PASS | Manifest registers `teachtrack://oauth/callback`; MainActivity handles cold-start and new-intent callbacks |
| Token protection | PASS | OAuth token response is stored using Android Keystore encryption, separate from Provider API key storage |
| Real login | NOT CLAIMED | Requires a real identity provider, public client ID, endpoint configuration, and provider-side redirect allow-list |

## 约束

本轮没有向 OnePlus 设备发送安装、测试、shell 或截图命令。
