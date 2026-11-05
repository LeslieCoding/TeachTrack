# TeachTrack 测试记录：第 4 轮（2026-09-11）

## 设备

- 当前设备：OnePlus `92f73a16` / `PKG110`
- OPPO Pad `2d64f765`：本轮未连接，因此没有新增 OPPO 证据。

## 构建与自动化测试

- `testDebugUnitTest`：通过。
- `assembleDebugAndroidTest`：通过。
- OnePlus instrumentation：`OK (13 tests)`。
- 覆盖 Dashboard 限量查询、备份往返、同步 revision/conflict/tombstone、Copilot 本地写入、Workspace 健康检查、损坏备份回滚、性能数据量和 CSV 转义。
- `assembleRelease`：通过。

## OAuth / WebView 真机验证

- 手机没有可处理 HTTPS 的外部浏览器 Activity，系统解析结果为 `No activity found`。
- 应用内 `ExternalWebActivity` 成功启动并显示 Kimi 官方页面。
- UIAutomator 读取到 Kimi 登录页面标题、手机号登录、验证码输入、协议勾选和登录按钮。
- Kimi 设备码成功生成并进入轮询状态。
- 未执行真实账号登录，因此不宣称 OAuth Token 已取得。

## 交付物

- `artifacts/deliverables/TeachTrack-3.0.0-alpha.1-debug.apk`
- `artifacts/deliverables/TeachTrack-3.0.0-alpha.1-release-unsigned.apk`
- 哈希：`artifacts/deliverables/SHA256SUMS.txt`
- 首页截图：`artifacts/screenshots/oneplus-v2/final-dashboard-2026-09-11.png`

## 未完成/外部条件

- OPPO Pad 新一轮回归需要设备重新连接。
- Release 签名需要签名密钥。
- 云端 Provider 的真实调用需要服务商账号、客户端注册或 API 凭据。
