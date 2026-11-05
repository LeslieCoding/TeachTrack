# ANDROID_APP/DEPLOYMENT/09 — Multi-Device & Transport

## 设备优先级

`project.yaml` 设置 `android.device_priority: physical` 时，真机优先——
不能因为模拟器已经在跑就擅自部署到模拟器。真机不可用、且当前测试允许时
才用 **Emulator Fallback**，并且必须在结果里明确记录
`TESTED_ON_EMULATOR`，**不得把模拟器测试冒充成
`PHYSICAL_DEVICE_VERIFIED`**——这是 `CORE/04_TEST_RULES.md` Evidence
Protocol 在 Android 场景下的具体应用：模拟器验证和真机验证不是同一等级
的证据。

## 多设备目标

见 `01_DEVICE_DETECTION.md` 的 Sticky Target Device 与
`android.target_device.serial` 配置——本文件不重复，只强调：一旦确定过
目标设备，除非该设备不再可用，否则不重新询问。

## Wireless Debugging 作为对等传输方式

用户已完成 Android 官方 Wireless Debugging 配对后，可以把它当作和 USB
对等的 Zero-Touch Deployment 传输方式使用，**不得每次重新配对**。无论
传输方式是 USB、Wireless ADB 还是 Emulator，上层流程保持一致：

```
DEVICE → BUILD → DEPLOY → RUN → VERIFY
```

传输方式的差异只影响"怎么连上设备"这一步，不改变整套开发循环。
