# ANDROID_APP/DEPLOYMENT/08 — OEM Compatibility Layer

## 覆盖范围

ColorOS、OxygenOS、HyperOS、One UI、Pixel Android，以及其他 Android
OEM 定制系统——它们在 USB 调试/安装权限上的行为可能与原生 Android 不同。

## 精确识别，而不是笼统甩锅

如果 ADB 已授权、但 OEM 拒绝 USB 安装：**不得错误地告诉用户"ADB 坏了"**。
必须区分：

```
ADB Authorization          （USB 调试授权本身）
USB Installation Permission（USB 安装权限，OEM 层面单独控制）
Package Installer Restriction（系统包安装器限制）
OEM Security Restriction   （厂商安全策略限制）
```

确实需要手机端一次性设置才能继续时，明确标注
`ONE_TIME_OEM_CONFIGURATION`，说清楚具体是哪一层限制、需要用户去手机
哪个设置里打开什么，完成后自动恢复到零交互模式——不是"以后每次都要
手动装"。

## 严禁规避 OEM 安全

`00_ANDROID_DEPLOYMENT.md` 已经定的边界在这里再强调一次：不允许 root
用户设备、关闭系统关键安全保护、修改 Package Installer、注入系统服务，
仅仅为了少点一次安装确认。正确目标永远是走 Android 官方开发部署通道。
