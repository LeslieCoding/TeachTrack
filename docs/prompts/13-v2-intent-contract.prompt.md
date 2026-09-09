# TeachTrack 2.0 Prompt 13 — 意图协议与安全确认

实现严格的自然语言到结构化命令协议。

要求：

- 使用 `teachtrack.command.v1` JSON envelope。
- 每个命令包含 action、mode、parameters、missingRequired、ambiguity、explanation、sensitiveFields、requiresConfirmation。
- 未知 action、错误参数、歧义学生名、缺少必填字段必须进入 Clarification 或 Failure。
- 模型不能生成 SQL、Kotlin、反射调用或任意 URL。
- 写操作必须先 Preview；成绩、家长信息、沟通、导出、归档和批量操作必须二次确认。

验收：恶意/畸形模型输出被拒绝；同名学生不会被静默选错；确认前数据库无变化。
