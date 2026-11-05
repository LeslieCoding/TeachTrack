# ANDROID_APP/DEPLOYMENT/04 — Install Strategy

## Application ID 与启动 Activity 自动识别

**禁止要求用户手填** `applicationId`（如 `com.xxx.xxx`）——优先从
Gradle、merged manifest、variant metadata、APK metadata 自动获取。同样
自动识别带 `MAIN + LAUNCHER` 的 Activity，**禁止所有项目硬编码
`.MainActivity`**（很多项目的入口 Activity 根本不叫这个名字）。

## Build Variant 识别

根据项目实际使用的 Gradle / Android Gradle Plugin / Build Variant /
Product Flavor 自动确定构建任务（例如对应 `assemble<Variant>`），**不得
硬编码所有项目都叫 `gradlew assembleDebug`**——先识别项目实际的 variant
配置，再拼出正确的任务名。

## 安装策略决策表

| 情况 | 策略 |
|---|---|
| 日常调试改动 | `-r` 覆盖安装，保留数据 |
| 数据库迁移测试 | Clean Install |
| First Launch 测试 | Clean Install |
| 上次安装异常/签名冲突 | Clean Install（先按 `02` 分析原因） |
| 用户明确要求 | 按用户要求 |
| 其余所有情况 | 默认覆盖安装，不清数据 |

策略决策和最终使用的安装命令记入
`TEMPLATES/ANDROID_DEPLOYMENT_STATE_TEMPLATE.md`。
