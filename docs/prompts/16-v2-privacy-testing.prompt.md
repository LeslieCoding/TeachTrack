# TeachTrack 2.0 Prompt 16 — 隐私、测试与发布

完成 2.0 Copilot 的安全质量门禁。

测试层级：

- Unit：命令解析、schema 校验、歧义判断、敏感字段分类、脱敏。
- Fake provider：成功、超时、空响应、恶意输出、未知 action、限流。
- Repository/Room：确认前后数据变化、undo、审计排除秘密。
- Compose：输入、语音权限、预览、确认、取消、错误、离线状态。
- OPPO Pad：真实 UI 两轮测试；只在用户明确提供真实 provider 配置后测试网络适配器。

发布门禁：未提供签名密钥不生成 signed release；未完成隐私确认和 provider 失败回退不标记 Done；所有 1.x 回归测试必须通过。
