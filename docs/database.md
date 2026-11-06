# TeachTrack 数据库说明

当前 Room version = **7**，数据库文件名为 `teachtrack.db`。schema 由 KSP 输出到 `app/schemas/`；任何结构变化必须提供显式 `Migration`，禁止 `fallbackToDestructiveMigration`。

## 已落地表（21 张）

`students`、`parent_contacts`、`course_sessions`、`knowledge_points`、`student_knowledge_progress`、`student_knowledge_events`、`exams`、`student_scores`、`score_items`、`homework`、`communications`、`lesson_plans`、`tasks`、`problem_records`、`templates`、`tags`、`student_tags`、`attachments`、`historical_learning_records`、`student_progress`、`unresolved_historical_records`。

## 迁移历史

| 版本 | 内容 |
|---|---|
| 1→2 | score_items、problem_records、templates、tags、student_tags、attachments |
| 2→3 | student_knowledge_events |
| 3→4 | students 增加 displayName/currentGrade/gradeAtRecord/nameCompleteness；historical_learning_records、student_progress |
| 4→5 | student_scores 增加 scoreLabel/fullScoreLabel/scoreAccuracy |
| 5→6 | unresolved_historical_records |
| 6→7 | attachments 增加 relativePath/size/sha256/updatedAt/revision/deletedAt |

## 关键约定

- 主键均为 `TEXT`：历史 Seed 使用 `seed-*` 确定性 ID，运行时新建使用 UUID 字符串。不得重新生成历史学生 ID。
- `StudentScore.scoreRate` 为运行时计算属性：`score / fullScore`，不会将不同满分考试直接比较。
- `problem_records` 是文字级问题记录，不是题库中的结构化题目；题库设计见 `docs/ai-practice/TEACHTRACK_DATABASE_SCHEMA.md`。
- 新增云端题库/练习相关表时，只增不删，且通过旁表记录同步状态，避免改动现有 21 张表。

## 后续（4.0）

PostgreSQL 目标 schema 与 Room → PostgreSQL 映射见：

- `docs/ai-practice/TEACHTRACK_DATABASE_SCHEMA.md`
- `docs/ai-practice/TEACHTRACK_MIGRATION_PLAN.md`
