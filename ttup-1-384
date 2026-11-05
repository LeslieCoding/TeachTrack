# ANDROID_APP/DEPLOYMENT/07 — Deployment Failure Recovery

## 错误分类（不得所有失败都归成同一句"安装失败，请手动安装"）

```
BUILD_FAILURE            DEVICE_NOT_FOUND        DEVICE_UNAUTHORIZED
DEVICE_OFFLINE            INSTALL_PERMISSION_BLOCKED
SIGNATURE_CONFLICT        VERSION_DOWNGRADE       INSUFFICIENT_STORAGE
APK_INVALID               ABI_MISMATCH            SDK_INCOMPATIBLE
PACKAGE_CONFLICT          LAUNCH_FAILURE          RUNTIME_CRASH
TEST_FAILURE
```

每一类走各自的恢复路径，而不是统一丢给用户处理。

## 具体处理

- **INSUFFICIENT_STORAGE**：检测手机空间，分析 App 大小/Data/Cache/
  当前剩余空间，不立即让用户重置设备。
- **ABI_MISMATCH**：`adb shell getprop ro.product.cpu.abilist` 与 APK
  native library 比对，不匹配直接定位为 `ABI_MISMATCH`，不重复安装。
- **SDK_INCOMPATIBLE**：读取设备 API Level/Android 版本，与项目
  `minSdk`/`targetSdk` 比对判断兼容性。
- 其余分类见 `02_ADB_DEPLOYMENT.md`（签名冲突/版本降级）、
  `01_DEVICE_DETECTION.md`（设备相关）、`05_APP_LAUNCH.md`（启动相关）、
  `06_LOG_CAPTURE.md`（崩溃相关）。

## 禁止无限重试

同一安装错误连续失败：必须做 Root Cause Analysis（`CORE/06`），禁止
`adb install` 循环重试到天荒地老。三次同根因失败即触发
`CORE/08_AI_EXECUTION_RULES.md` §8.6 的 Stagnation Detection。

## Deployment Diagnostics

遇到部署问题自动记录进
`TEMPLATES/ANDROID_DEPLOYMENT_DIAGNOSTICS_TEMPLATE.md` 对应的项目本地
文件：Host、工具版本、设备、ADB 状态、APK、Package、安装命令、退出码、
错误、根因、恢复方式。
