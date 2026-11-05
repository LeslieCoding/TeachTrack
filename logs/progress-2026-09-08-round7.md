# TeachTrack 开发记录：2026-09-08 / Round 7

## 本轮实现

- 成绩录入新增“失分类型”和“失分分值”字段；填写后创建 `ScoreItem` 并关联本次 `StudentScore`。
- ScoreItem 已加入 DAO、Repository、备份导入/导出及恢复预览统计。
- Dashboard 的“今日课程”改为按 `CourseSession.date == 当前日期` 查询，空状态不再把在读学生冒充为今日课程。
- Dashboard 待处理数量改为读取 Room 的 pending task count。

## 验证

- `testDebugUnitTest assembleDebug assembleRelease`：PASS。
- 最新 Debug 安装到 OnePlus `92f73a16`：Success。
- 一加 UI dump 明确显示 Dashboard 空状态文案“在学生页记录今天课程后，这里会显示真实课程”：PASS。
- 一加成绩弹窗 UI dump 显示“失分类型（选填）”“失分分值（选填）”：PASS。
- `AndroidRuntime:E` / `Room:E`：无错误。

## 遗留

- ScoreItem 当前支持单条快速失分项，后续可扩展为多条编辑列表。
- ProblemRecord 独立实体仍保留为结构化扩展，课程问题已可直接记录到 `CourseSession.problems`。
- 自动化 instrumentation / Compose UI 测试仍需补充。
