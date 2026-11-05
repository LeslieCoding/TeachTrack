# ANDROID_APP/DEPLOYMENT/03 — Android CLI Deployment（高速通道，能力检测后可选）

## 能力检测

如果当前环境存在兼容的现代 Android CLI，且经过能力检测确认可用，允许
优先使用官方支持的高速开发部署方式（例如 `android install` /
`android run --use-delta-install` 一类的增量安装能力）。**不得假设任何
电脑一定安装了这套 CLI**——必须先做 capability detection
（见 `10_ZERO_TOUCH_LOOP.md` 的工具能力检测清单），检测不到就跳过，不
报错、不阻塞。

## Fallback

Android CLI 不存在、不可用，或项目不兼容时，自动退回
`02_ADB_DEPLOYMENT.md` 定义的标准 ADB 路径——这是可靠基线，任何情况下都
不能因为高速通道不可用就停止部署。高速部署本身失败时同样自动回退到标准
ADB 流程，稳定性优先于速度（呼应 `10_ZERO_TOUCH_LOOP.md` 的部署性能
原则）。
