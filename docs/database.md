# TeachTrack 数据库说明

当前 Room version = 1，数据库文件名为 `teachtrack.db`。每次构建会把 schema 输出到 `schemas/`，后续版本必须基于该 schema 编写显式 Migration。

已落地表：students、parent_contacts、course_sessions、knowledge_points、student_knowledge_progress、exams、student_scores、homework、communications、lesson_plans、tasks。

模型预留：ScoreItem、ProblemRecord、Template、Tag、StudentTag、Attachment；这些会在对应 Prompt 阶段加入，新增版本必须提供显式 Migration，禁止 `fallbackToDestructiveMigration`。

`StudentScore.scoreRate` 为运行时计算属性：`score / fullScore`，不会将不同满分考试直接比较。
