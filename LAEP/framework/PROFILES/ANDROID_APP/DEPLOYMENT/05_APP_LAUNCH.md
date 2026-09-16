# ANDROID_APP/DEPLOYMENT/05 — App Launch

## 自动启动

安装成功后立即自动启动应用（例如通过 `adb shell am start ...`
执行，具体 Activity 由 `04_INSTALL_STRATEGY.md` 的自动识别结果提供）。
**不得要求用户"请在手机上找到 App 并点击打开"**。

## 启动验证

执行 `am start` 之后不能直接认为测试成功，必须检测：Process 是否存在、
Activity 是否真的启动、有没有 Crash、有没有 ANR、有没有立即退出。启动
失败按 `07_DEPLOYMENT_RECOVERY.md` 的 `LAUNCH_FAILURE` 分类处理。

## Restart App

需要干净重启时：`adb shell am force-stop <package>` → 再 `launch`，
形成可靠的重启流程，用于每轮验证前确保是从干净状态启动，而不是叠加在
上一次运行的状态之上。
