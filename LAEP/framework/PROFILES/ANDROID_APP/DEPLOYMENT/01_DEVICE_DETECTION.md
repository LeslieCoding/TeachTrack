# ANDROID_APP/DEPLOYMENT/01 — Device Detection

## 检测流程

开发开始前自动检查：

```
adb version
adb devices -l
```

识别设备状态：`device`（已授权在线） / `unauthorized`（未授权） /
`offline`（离线） / `emulator` / `physical`。

## 单设备自动选择

只存在 1 台已授权真机时，**自动选择**，不得询问"要部署到哪台设备？"。
后续所有命令锁定该设备（`adb -s <serial> ...`）。

## 多设备与设备粘性

同时存在多台设备/模拟器时，优先读取 `project.yaml` 的
`android.target_device.serial`；如果之前已经成功部署到某台设备，优先保持
**Sticky Target Device**（沿用上次成功部署的设备），不要每次重新询问。
只有首次出现多个候选且确实无法判断时，才进行一次用户选择，选择结果立刻
写回 `project.yaml`，此后不再重复询问。

## unauthorized

明确识别为 `ADB_AUTH_REQUIRED`，告诉用户手机需要完成一次 USB 调试授权，
**不得继续无意义的 Build/Install 重试**——这属于 `00_ANDROID_DEPLOYMENT.md`
定义的一次性设备授权边界，AI 无法代劳。

## offline

自动尝试低风险恢复：重新检测、必要时重启 ADB 服务、重新枚举设备。禁止
第一次 offline 就让用户重新配置整个 Android 开发环境——那是过度反应。

## USB 热插拔

开发过程中设备被拔掉：不得把项目判定为失败，状态设为
`DEVICE_DISCONNECTED`；项目继续进行静态分析、编译、单元测试；需要真机
测试的部分等待下次可用设备检测周期，或在当前会话内再次检测。设备重新
连接后自动执行 `adb devices` → 识别是否是之前那台设备 → 恢复部署，
不要求用户确认"手机插上了吗"——只要工具能自行检测，就自行检测。

状态记录见 `TEMPLATES/ANDROID_DEVICE_STATE_TEMPLATE.md`。
