# TeachTrack 回归测试第三轮（2026-09-11）

## 环境

- Android 真机：OnePlus `92f73a16`（PKG110）
- OPPO Pad：`2d64f765` 当前未连接，本轮未操作
- Java：JDK 17

## 执行项目

- `git diff --check`
- `testDebugUnitTest`
- `assembleDebug`
- `assembleDebugAndroidTest`
- `assembleRelease`
- Debug APK 安装
- AndroidTest APK 安装
- 完整 instrumentation suite

## 结果

- 构建：PASS
- Debug / AndroidTest 安装：PASS
- instrumentation：PASS，`OK (11 tests)`
- 覆盖：Dashboard 限量流、Copilot 本地写入、备份往返、损坏备份回滚、Workspace 健康检查、revision 冲突、冲突扫描幂等、Tombstone 删除、性能数据量回归。

## 交付物

- `artifacts/deliverables/TeachTrack-3.0.0-alpha.1-debug.apk`
- `artifacts/deliverables/TeachTrack-3.0.0-alpha.1-release-unsigned.apk`
- `artifacts/deliverables/SHA256SUMS.txt`

## 未完成 / 外部条件

- OPPO Pad 新一轮安装和截图需设备重新连接。
- Release 签名需用户签名材料。
- 真实云端 Provider/OAuth 需用户账户、客户端注册和凭据。
