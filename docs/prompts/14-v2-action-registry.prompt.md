# TeachTrack 2.0 Prompt 14 — 全模块 Action Registry

建立覆盖 TeachTrack 全部业务模块的本地 Action Registry。

首批 action ID：

- `student.create`, `student.update`, `student.search`, `student.archive`
- `course.create`, `course.resume`, `course.timeline`
- `exam.create`, `score.create`, `score.query`
- `knowledge.progress.update`, `knowledge.query`
- `homework.create`, `problem.create`, `problem.status.update`
- `communication.create`, `lesson_plan.create`, `task.create`
- `template.create`, `tag.create`, `attachment.list`
- `dashboard.query`, `analytics.query`, `navigation.open`

每个 action 必须声明参数 schema、校验器、Repository 调用、敏感字段、确认等级、undo 策略、审计标签和测试。

验收：Registry 覆盖表可自动检查；模型只能调用 allow-list；每个写 action 有 Repository 集成测试。
