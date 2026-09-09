# TeachTrack 2.0 Prompt 12 — 云端模型 Provider Gateway

建立可切换厂商的云端模型接入层。

要求：

- 定义 ProviderGateway、ProviderConfig、ProviderRequest、ProviderResponse、ProviderError。
- UI 只依赖 provider-neutral domain interface。
- Settings 支持启用/禁用、厂商标识、endpoint、model、timeout 和连接测试。
- 真实密钥不能进入源码、Room、备份、日志、截图或错误信息。
- 首版提供 FakeProvider 和失败 Provider；真实适配器必须可插拔。
- 超时、限流、无网络、无密钥、格式错误不能影响本地功能。

验收：切换 provider 不改 Repository；无 provider 时 1.x 可完整使用；所有网络失败有可读错误。
