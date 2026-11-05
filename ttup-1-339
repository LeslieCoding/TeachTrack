# PROFILE: ANDROID_APP

## 适用于

普通 Android 应用（非系统级/非 ROM 定制，那属于 `OPERATING_SYSTEM`）。

## Zero-Touch Deployment（5.1.0 新增）

连接真机开发时的自动化部署——Build/Install/Launch/Log/Test 全自动闭环，
用户不需要重复点击手机上的"安装""打开"。完整规则见本目录下的
`DEPLOYMENT/`（`00_ANDROID_DEPLOYMENT.md` 起）。这是本 Profile 的一部分，
不影响其他 Profile；简单 S1/S2 Android App 只启用其中最基础的
Build+Auto Install+Auto Launch+Basic Test，不因此被迫承担大型项目治理
负担（见 `DEPLOYMENT/00_ANDROID_DEPLOYMENT.md` 的反过度工程一节）。

## 通常搭配的 Scale

`S2`（多数情况），`S3`（企业级/长期维护的 Android 产品）。

## 本 Profile 在 CORE 之上新增

- **构建/环境**：记录 Gradle 版本、Android SDK/Build Tools 版本、
  最低/目标 API Level、签名配置来源（不得硬编码 keystore 密码，见
  `SECURITY/01`）。
- **权限**：新增/使用任何 Android 权限，必须说明用途并遵循最小化原则。
- **生命周期**：Activity/Fragment/Service 生命周期相关改动需要额外考虑
  配置变更（旋转屏幕等）下的状态保留。
- **UI 一致性**：Design Token（颜色/间距/圆角/字号/动效时长）统一维护，
  遵循 Material 设计规范或项目自定义规范，禁止页面间随意不一致。
- **兼容性**：至少覆盖项目 `minSdkVersion` 到当前最新稳定版 Android 之间
  的关键差异，涉及厂商定制系统（如 ColorOS/MIUI）行为差异时明确标注，
  不假设"官方 Android 行为 = 所有设备真实行为"。
- **测试**：Instrumented Test（真机/模拟器）作为 Integration 层，覆盖
  UI 关键交互路径。

## 常见坑（写给继承此 Profile 的 Agent）

不要假设权限已经被授予；不要假设网络一定可用；不要假设应用在后台不会被
系统杀死；涉及存储访问要遵循 Scoped Storage 规则（较新 Android 版本）。
