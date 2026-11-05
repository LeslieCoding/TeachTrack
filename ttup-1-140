# ANDROID_APP/DEPLOYMENT/00 — Android Zero-Touch Deployment（总览）

*仅对装载了 `PROFILES/ANDROID_APP` 的项目生效。这套规则不得出现在
`CORE/` 里——见 `CORE/00_LAEP_CORE.md` 的 Profile 隔离原则。*

## 定义

**Android 零交互开发部署**：设备完成必要的一次性授权之后，日常开发中的
Build / Push / Install / Update / Launch / Stop / Restart / Log / Test
全部由 AI 自动执行，用户不需要重复点击手机上的"安装""打开"。

## 安全边界（必须严格区分）

- **一次性设备授权**（USB Debugging Authorization、USB 安装权限、
  Developer Options、OEM 强制的人机确认）——这是操作系统安全机制，AI
  绝不允许绕过、伪造或诱导用户关闭。设备未完成授权时，AI 必须识别状态
  并明确告知用户 `ONE_TIME_DEVICE_AUTH_REQUIRED`，只要求完成必要的一次性
  设置，不做无意义的重试。
- **授权后的日常开发**——一旦 `adb devices` 对该设备显示 `device`（不是
  `unauthorized`/`offline`），以后默认不得再要求用户手动安装 APK。

**绝对禁止**：root 用户设备、关闭系统关键安全保护、修改 Package
Installer、注入系统服务，或任何形式绕过 Android/OEM 安全确认——即使只是
为了少点一次安装确认。正确目标始终是"用官方开发部署通道做到最大自动化"，
不是"想办法绕开安全机制"。

## 默认部署模式

Android 项目默认 `deployment_mode: AUTO`，不默认 `MANUAL`。检测到已授权
Android 真机时自动进入 `PHYSICAL_DEVICE_AUTO_DEPLOY`。`project.yaml` 里的
Android 专属扩展块：

```yaml
android:
  deployment:
    mode: auto
    auto_build: true
    auto_install: true
    auto_launch: true
    auto_restart: true
    auto_logcat: true
    auto_test: true
    keep_app_data: true
    prefer_incremental: true
    fallback_to_adb: true
  target_device:
    serial: ""              # 留空 = 自动检测/沿用上次成功部署的设备
  device_priority: physical  # physical | emulator
```

这是 `PROJECT_CONFIG/SCHEMA.md` 通用 Schema 的 Profile 专属扩展——机制
（Profile 可以附加自己的配置块）是通用的，字段内容是 Android 专属的。

用户明确要求"只打包 APK，不要安装"时，设为 `auto_install: false`，尊重
用户；Release Build 不一定自动安装，Debug/Development 默认自动部署，
具体策略仍由项目决定。

## 权限

`adb devices` / `adb install` / `adb shell` / `adb logcat` /
`gradlew assembleDebug` 正常情况下都不需要管理员权限——**禁止为了
"保险"默认用管理员终端跑 Android 部署**。只有真正涉及驱动安装/USB 权限
修复/系统服务时才需要提权，且必须走
`AUTOMATION/04_PRIVILEGE_PREFLIGHT.md` 的统一决策与批处理流程，不得
自行弹 UAC。

## Zero-Touch Ready

设备只有同时满足：`ADB = Authorized` + `Device = Online` + `Install
Channel = Available` + `Target = Selected`，才标记为 `ZERO_TOUCH_READY`。
进入这个状态后的默认开发循环见 `10_ZERO_TOUCH_LOOP.md`。

## Definition of Done 扩展（本 Profile 专属）

`QUALITY/02_DEFINITION_OF_DONE.md` 定义的是所有项目通用的底线；Android
真机开发项目在此之上追加：

```
Implemented + Build Passed + Install Passed + Launch Passed
+ Required Tests Passed + No Critical Crash
```

编译成功 **不等于** 功能开发完成——Master Agent 必须知道：具备真机条件时，
一个 Android 功能至少要经过 Build + Device Deployment + Runtime
Verification 才能算数（详见 `AGENTS` 相关文件的更新）。用
`QUALITY/03_ACCEPTANCE_PROTOCOL.md` 的阶梯对照：Build 对应 A1，
Install+Launch 对应 A3，Runtime Verification 对应 A4——本模块的 Zero-Touch
Loop 已经把 A1-A4 做成自动闭环，最终验收还需要补 A5（关键交互）、A6
（核心用户旅程）、以及 Silent Degradation Guard（Crash/ANR 检测只能证明
"没崩"，不能证明 required=true 的核心能力没有被静默禁用）。

## 反过度工程

新增这套自动部署能力，**不代表简单 Android App 要被迫进入 S3/S4**。一个
普通 S1/S2 Android App 只需要 Build + Auto Install + Auto Launch +
Basic Test，不要因为部署自动化的存在就顺带引入大型项目的治理负担
（呼应 `CORE/07_DELIVERY_RULES.md` 的 Anti-Overengineering 原则）。

## 与 LeslieOS / OPERATING_SYSTEM Profile 的区别

普通 Android App 的部署是 **ADB App Deployment**（装/卸/启动一个 App）；
未来 LeslieOS 一类项目的"部署"是 **System Image Flashing / Fastboot /
Recovery / OTA**（刷整个系统镜像）——两者完全不是同一回事，本模块只管
前者，后者归 `PROFILES/OPERATING_SYSTEM/PROFILE.md` 治理，不得混淆。
