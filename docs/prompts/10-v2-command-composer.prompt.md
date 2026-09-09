# TeachTrack 2.0 Prompt 10 — 首页 Copilot 指令入口

实现 TeachTrack 2.0 首页自然语言操作入口。

要求：

- 在首页 Dashboard 增加高质感绿色主题 Command Composer。
- 支持文字输入、发送、清空、编辑草稿、取消解释。
- 显示 Idle、Interpreting、Preview、Clarification、Confirming、Executing、Success、Failure 状态。
- 不允许 UI 直接修改 Room；所有写操作进入本地 Action Registry。
- AI 不可用时，首页和 1.x 所有功能仍正常使用。
- 首版不接真实 API Key，先使用 FakeProvider 完成可重复测试。

验收：文本输入可提交到 fake provider，生成预览，不确认不能写库；错误和空状态可见且可恢复。
