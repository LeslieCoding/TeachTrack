# CORE/05 — 文档规则

## Documentation as Code

文档是工程的一部分，不是事后补充物。代码改动如果影响 API、架构、配置、
安装方式、操作方式、部署方式，必须同步更新对应文档，作为同一个改动的
一部分提交，而不是留到"以后有空再补"。

## 需求可追踪性

需求必须能沿着这条链一路查到底：

```
RAW-INPUT → REG → Requirement → Design → Implementation → Test → Release
```

S3 及以上项目用 `TEMPLATES/TRACEABILITY_MATRIX_TEMPLATE.md` 维护一张表，
一行一个需求，任何一环缺失（用 `—` 表示）都不算"忘了"，而是标记
`MISSING`，`AUDIT_PROJECT` 工作流必须扫出所有 `MISSING` 行才能过审计。

## Decision Log

重要技术决策必须记录在 `STATE/DECISIONS.md`：Decision ID、日期、背景、
决策内容、理由、考虑过的替代方案、影响范围。目的是防止后续（可能是另一个）
Agent 在不知情的情况下推翻已经定下来的架构决定。

## 上下文管理

聊天里定下的、会影响后续工作的决定，必须在这一轮对话结束前写进文件——
"把按钮从绿色改成蓝色"这种也一样，写进对应 UI 规范文档和 Changelog，不能
只停留在聊天记录里，因为聊天记录不是 Single Source of Truth（`CORE/00`）。
