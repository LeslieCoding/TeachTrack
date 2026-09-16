# ANDROID_APP/DEPLOYMENT/10 — Zero-Touch Loop（默认开发循环）

## 循环本体

设备进入 `ZERO_TOUCH_READY`（见 `00_ANDROID_DEPLOYMENT.md`）后，Android
Profile 下每次代码修改的默认循环：

```
CODE CHANGE → BUILD → INSTALL/UPDATE → FORCE STOP → LAUNCH
            → VERIFY → LOG → TEST → FIX（如需要，回到 BUILD）
```

这是 `CORE/08_AI_EXECUTION_RULES.md` §8.4 通用主循环
（IMPLEMENT→BUILD→TEST→…）在 Android 真机开发场景下的具体展开，不是另一
套独立的执行哲学——`WORKFLOWS/RESUME_PROJECT.md` 执行主循环时，遇到
Android 项目且设备 `ZERO_TOUCH_READY`，就按这份展开版本走，而不是停在
"编译通过"就转头等用户装机。

最终体验：用户说"把首页按钮改大一点"，AI 自己完成
修改代码→Build→部署手机→启动→检查→截图/测试→发现异常→修复→重新部署，
而不是"生成 APK，让用户安装"。

## Build Only / Release 例外

用户明确要求"只打包不安装"，或者当前是 Release Build 且项目策略不要求
自动安装，跳过 INSTALL 及之后的步骤，尊重项目/用户的设置
（见 `00_ANDROID_DEPLOYMENT.md` 的 `auto_install` 开关）。

## 部署性能

不要求每次小改动都machine地跑最慢的完整流程——工具链和设备支持时，优先
Incremental Build / Incremental Install / Delta Install / Fast Deploy
（`03_ANDROID_CLI_DEPLOYMENT.md`）。**稳定性优先于速度**：高速部署失败
自动 Fallback 到标准部署（`02_ADB_DEPLOYMENT.md`），不得因为追求速度而
放弃可靠性。

**Full Install Checkpoint**：即使长期用增量部署，在里程碑、RC、最终测试
节点，必须至少做一次 Clean/标准 Full Deployment，防止增量状态掩盖真实
问题——对应 `AUTOMATION/01_CHECKPOINT_PROTOCOL.md` 的 Checkpoint 机制在
这里的一次具体应用。

## 跨平台主机支持

自动部署脚本/命令必须同时支持 Windows（`adb.exe`、`gradlew.bat`、
Windows 路径与空格处理、Android SDK 环境变量）、Linux（`adb`、
`./gradlew`、udev/USB 权限、可执行权限）、macOS——**不得硬编码只支持
某一个操作系统**。

## 工具能力检测

启动时检测：Java、Gradle、Android SDK、Platform Tools、ADB、
Android CLI、Build Tools、已连接设备，并记录能力清单。**工具已存在时
禁止每次自动重新下载安装 Android SDK**——优先复用现有环境。

## Single Deployment Entry

每个 Android 项目最终提供一个统一入口（内部自动完成 Detect→Build→
Install→Launch→Verify），而不是让用户记住一堆命令。迁移已有项目时的
去重原则见 `11_MIGRATION.md`。

## 状态与状态显示

设备/部署状态实例分别写入项目本地 `/LAEP/STATE/ANDROID_DEVICE_STATE.md`
和 `/LAEP/STATE/ANDROID_DEPLOYMENT_STATE.md`（模板见 `TEMPLATES/`），
避免 AI 每轮重新"学习"设备和上次部署情况。可选的一行状态展示：

```
DEVICE: CONNECTED   ADB: AUTHORIZED   DEPLOYMENT: ZERO_TOUCH_READY
BUILD: PASS   INSTALL: PASS   LAUNCH: PASS   TEST: PASS
```
