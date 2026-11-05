# ANDROID_APP/DEPLOYMENT/11 — Migration（已有 LAEP 5.0 Android 项目）

已经用 LAEP 5.0 生成的 Android 项目**不需要重新创建**。走
`WORKFLOWS/UPGRADE_PROJECT.md` 的框架版本升级流程时，Android 项目额外
执行：

```
Detect  → 项目是否已有自己的部署脚本（deploy.ps1/install.ps1/run.ps1/
           start.ps1/test-device.ps1 之类）
Add     → 补上 android: 部署配置块（00_ANDROID_DEPLOYMENT.md 的 Schema）
Generate→ 生成本模块要求的自动化能力（Device Detection/Install/Launch/
           Logcat，按 01-10 号文件落实）
Verify  → 跑一遍 §79 的 Case A-F 验证
Mark    → 标记该项目 LAEP 5.1 Compatible
```

## 已有部署脚本怎么处理

先检查现有脚本是否可靠：可靠的（确实按项目实际情况正确工作、没有明显
坑）**复用/升级**，不可靠的**替换**。目标是收敛成
`10_ZERO_TOUCH_LOOP.md` 定义的 **Single Deployment Entry**，避免一堆
互相冲突、职责重叠的部署脚本共存（`deploy.ps1` 和 `install.ps1` 都在
装 APK，谁跟谁听谁的说不清楚）——这是要清理掉的技术债，不是要保留的
"多一层保险"。
