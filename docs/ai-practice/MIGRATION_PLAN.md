# MIGRATION_PLAN（M01 入口）

本文件是 M01 要求的迁移计划入口。权威内容、字段映射、幂等规则、回滚方案与验收矩阵统一维护在：

→ **[`TEACHTRACK_MIGRATION_PLAN.md`](./TEACHTRACK_MIGRATION_PLAN.md)**

## 为什么只有一份正文

M01 要求的 `MIGRATION_PLAN.md` 与 Step 8 要求的 `TEACHTRACK_MIGRATION_PLAN.md` 是同一份交付物。项目规则禁止复制两份内容造成漂移，因此正文只保留一份，本文件仅作入口。

## 三条必须先满足的门禁

1. **数据源门禁**：Prompt 描述的 `TeachTrack_当前学生完整数据_含上课记录_2026-11-01.ttseed.json` 在项目、`D:\SoftWare`、`D:\Leslie` 常见位置与 git 历史中均未找到。M02 的“14 学生 / 91 课程”验收必须拿到该文件或设备备份，不能用仓库现有 29 人 seed 冒充。
2. **ID 门禁**：历史 TEXT ID 原样写入 `legacy_id`，服务端 UUID 由 UUIDv5 确定性生成；不得重新生成一套与历史记录脱钩的学生 ID。
3. **回滚门禁**：迁移前 `pg_dump` + 行数基线 + 批次 ID；没有备份不得执行破坏性迁移。

## 相关文档

- 现状：`CURRENT_ARCHITECTURE.md`
- 差距：`GAP_ANALYSIS.md`
- 目标库结构：`TEACHTRACK_DATABASE_SCHEMA.md`
- 总架构：`TEACHTRACK_AI_PRACTICE_ARCHITECTURE.md`
- 阶段计划：`TEACHTRACK_IMPLEMENTATION_ROADMAP.md`
