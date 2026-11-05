# ANDROID_APP/DEPLOYMENT/02 — ADB Deployment（标准可靠路径）

## 默认安装

```
adb -s <serial> install -r -t <apk>
```

`-r` = 覆盖安装并尽量保留应用数据；`-t` = 允许安装测试 APK。是否加
`-g`（自动授予 Manifest 中声明且 ADB 支持授予的运行时权限）根据项目
需要决定，不得盲目全加。

## 保留数据 vs Clean Install

日常开发默认 `KEEP_APP_DATA = true`，优先 `-r` 覆盖安装，避免每次开发都
"卸载→安装→数据全丢"。`CLEAN_INSTALL` 只在以下情况启用：数据库迁移测试、
First Launch 测试、安装异常、签名冲突、用户明确要求——**禁止每轮调试都
Clean Install**。

## Split APK / AAB

项目产物是 Multiple APK / Split APK 时，自动检测并使用
`adb install-multiple`（或对应官方工具），不得只装一个文件。产物是
`.aab` 时，不得直接 `adb install xxx.aab`——必须先按项目开发模式构建
可直接测试的 APK，必要时用官方 Bundle Tooling 生成设备适配 APK。

## 签名冲突

出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：不得立即卸载 App。先分析
package name、签名、已安装 build、当前 build。确认确实是 Debug 签名变化
导致，记录这个风险；如果确实需要卸载（会删数据），不得静默清除，要明确
说明会发生什么。

## Version Downgrade

出现 `INSTALL_FAILED_VERSION_DOWNGRADE`：先识别双方 `versionCode`，再
决定是否使用 `-d`——**不得每次默认允许 downgrade**。

## 安装验证

命令进程退出不代表安装成功。必须检测明确的 Success 标志或 package 状态，
例如 `adb shell pm path <package>` 确认 App 真正存在，再往下走。
