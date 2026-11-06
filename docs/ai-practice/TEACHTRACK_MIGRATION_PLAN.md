# TeachTrack 数据迁移方案

- 源：现有 Room（Android）、`seed/teacher_seed_v1.json`、`TeachTrack Backup` JSON、Workspace JSONL、目标 `.ttseed.json`
- 目标：PostgreSQL（`TEACHTRACK_DATABASE_SCHEMA.md`）
- 原则：**不删数据、不换学生 ID、可回滚、可重复执行、有报告、有对账**

---

## 1. 迁移原则

| # | 原则 | 落实 |
|---|---|---|
| 1 | 历史 ID 不变 | `legacy_id` 保留原值；服务端 UUID 由 `legacy_id` 确定性生成 |
| 2 | 幂等 | 唯一约束 + upsert；重复执行不产生重复学生/课堂 |
| 3 | 先备份 | 迁移前 `pg_dump` + 行数基线 |
| 4 | 先干跑 | `--dry-run` 输出将插入/更新/跳过数量，不写库 |
| 5 | 事务化 | 单批次一个事务；失败整体回滚 |
| 6 | 可回滚 | 批次 ID + 迁移前快照；两者都能定位 |
| 7 | 有报告 | 每个批次输出 JSON/Markdown 报告与 issue 列表 |
| 8 | 不猜 | 无法确定的字段留空或 `UNKNOWN`，不编造日期、年级、成绩 |

---

## 2. 输入源与优先级

| 源 | 位置 | 用途 | 状态 |
|---|---|---|---|
| A. `.ttseed.json` | 用户提供（项目、`D:\SoftWare`、`D:\Leslie` 与 git 历史均未找到） | Prompt §2 的 14 学生 / 91 课程快照 | **缺失，M02 门禁** |
| B. 设备 Room 备份 | App「备份」导出的 `TeachTrack Backup` JSON | 真实主数据（LAEP 记录 49 学生） | 需从设备导出 |
| C. Workspace JSONL | SAF 工作区 `data/*.jsonl` | 最近一次可移植导出 | 若已建工作区则可用 |
| D. `seed/teacher_seed_v1.json` | 仓库 | 历史教学 Seed（29 学生 / 30 课程） | 可用，但**不是** Prompt 描述的快照 |

**优先级**：A > B > C > D。任何迁移报告必须写明使用的源与 sha256。

> 当前阻塞：A 不存在。M02 的“14 学生 / 91 课程”验收必须等 A 或 B；不得用 D 冒充。基础设施建设（schema、工具、备份演练）可先做。

---

## 3. 源格式差异

### 3.1 `.ttseed.json`（目标格式，待确认）

Prompt 描述的字段：

```text
students[]:
  id, displayName, alias, status, gender, grade, subject,
  parentContact, academicProfile, learningCharacteristics,
  strengths[], weaknesses[], riskFlags[], knowledgeProgress[],
  scores[], coursePlans[], communications[], specialRules[],
  courseSessionIds[], courseSessions[]
```

要求：

1. 读取前先 `schema validation`；未知字段进 `import_issues`，不静默丢弃。
2. `students[].id` 原样进 `legacy_id`；若缺失，用 `UUIDv5(NS, "student:" + displayName + ":" + subject)` 生成并标 WARN。
3. `courseSessions[]` 必须能关联到学生；不能关联的进 `unresolved_historical_records`，不猜归属。

### 3.2 `teacher_seed_v1.json`（现有）

顶层：`format=TeachTrack Historical Seed`，`students[]` 含 `scores/knowledge/strengths/weaknesses/focus/courses`；1 条 `unresolvedHistoricalRecords`。

映射：

| Seed 字段 | 目标 |
|---|---|
| `fullName` / `displayName` | `students.full_name` / `display_name` |
| `gender` / `historicalGrade` / `subject` | 对应列；`grade_at_record` 保留 |
| `status` | `students.status`（现有全 `UNKNOWN`，原样保留） |
| `knowledge[]`（`组/点`） | `knowledge_points` 两级树 |
| `weaknesses[]` | `problem_records` → `mistake_records`（`UNKNOWN`） |
| `courses[]` | `course_sessions` + `historical_learning_records` |
| `scores[]` | `student_scores`（保留 `scoreLabel/fullScoreLabel/scoreAccuracy`） |
| `communications`（若有） | `communications` |
| `progress[]`（若有） | `student_progress` |

### 3.3 `TeachTrack Backup` JSON（Room 全量）

顶层 `format=TeachTrack Backup`，数组：students、courses、knowledgePoints、knowledgeProgress、knowledgeEvents、lessonPlans、exams、scores、scoreItems、homework、communications、tasks、parentContacts、problemRecords、templates、tags、studentTags、attachments、historicalLearningRecords、studentProgress、unresolvedHistoricalRecords。

这是**最完整的现有数据源**，迁移器必须优先支持。

---

## 4. 目标映射与关键字段

### 4.1 学生

| 源 | 目标 | 规则 |
|---|---|---|
| id | `legacy_id` | 原样 |
| id | `id` | UUID 正则则同值；否则 UUIDv5 |
| name | `full_name` | 空则 NULL |
| displayName | `display_name` | 空则回落 name；都空用“未命名-<id 后 6 位>”并标 WARN |
| status | `status` | 原样；非法值 → `UNKNOWN` |
| grade | `grade_at_record` | 历史年级，不写 `current_grade` |
| currentGrade | `current_grade` | 有则写 |
| subject | `subject_id` | 通过 subjects 映射；未知学科建 `OTHER` 并记 issue |
| notes | `notes` | 原样 |
| alias[] | `student_aliases` | 逐个 upsert |
| academicProfile / learningCharacteristics / strengths / weaknesses / riskFlags / specialRules | `student_profile` JSONB（新增可选表）或 notes | **第一版不丢**：完整存 `student_profile` JSONB，便于后续结构化 |

建议新增：

```sql
create table student_profiles (
  student_id uuid primary key references students(id) on delete cascade,
  academic_profile jsonb not null default '{}',
  learning_characteristics jsonb not null default '{}',
  strengths text[] not null default '{}',
  weaknesses text[] not null default '{}',
  risk_flags text[] not null default '{}',
  special_rules text[] not null default '{}',
  updated_at timestamptz not null default now()
);
```

### 4.2 课堂

| 源 | 目标 |
|---|---|
| id | `legacy_id` + `id` |
| studentId | `student_id`（通过 legacy 映射） |
| date | `date`；无法解析 → NULL + WARN（原值进报告） |
| title/content/performance/problems/homeworkSummary/nextLessonPlan/teacherNotes | 同名列 |
| subject | `subject_id` |

`coursePlans` 来自 `lesson_plans`；`courseSessionIds` 不入库（关系由 `student_id` 表达）。

### 4.3 知识点与掌握度

- 现有 `knowledge_points.parentId` 直接映射。
- `subject` 字符串 → `subject_id`。
- 无 `code` 时生成 `code = "KP-" + legacy_id`，保证唯一。
- `student_knowledge_progress.level` 写入兼容表；`student_mastery` 初始值：
  - `mastery_score = level * 25`
  - `confidence = 0`
  - `formula_version = "legacy-seed"`
  - 标记“非练习证据，仅初始展示”；首次真实练习后按新公式覆盖。

### 4.4 成绩

- `exams` + `student_scores` 原样进 PG 的兼容表。
- 不把旧成绩强行拆成 `exam_papers/exam_questions`；只有上传了新试卷并解析后才生成逐题数据。
- 迁移报告列出“旧成绩无逐题明细”的数量。

### 4.5 附件

- Room `attachments.uri/relativePath` 是设备本地路径，云端不可直接使用。
- 迁移策略：
  1. 元数据（name/mime/size/sha256）进 `assets`（`storage_key` 暂空，标 `PENDING_UPLOAD`）。
  2. 文件本体由 M05 的“附件上传”任务从设备补传；补传后填 `storage_key`。
  3. 不上传的文件保留本地，不阻塞迁移。

### 4.6 错题

- `problem_records` → `mistake_records`：
  - `legacy_id = "problem:" + room_id`
  - `mistake_type = UNKNOWN`
  - `mistake_reason = title + "\n" + description`
  - `first_seen_at/last_seen_at` 由 `date` 解析；无日期则迁移时间并标 WARN
  - `status` 原样映射（`待复习` → `OPEN` 等）
- 不把文字薄弱点自动升级为 `CONCEPT_ERROR` 等确定错因。

---

## 5. 迁移流水线

```text
1. 读取源文件（大小/编码/sha256）
2. Schema 校验（必填字段、类型、引用完整性）
3. 预检对账（学生数、课程数、成绩数、重复 ID）
4. --dry-run：输出计划（insert/update/skip/issue）
5. 备份目标库（pg_dump）+ 记录基线
6. 开启事务
7. upsert subjects / students / aliases / profiles
8. upsert knowledge_points / legacy progress / mastery 初始值
9. upsert course_sessions / lesson_plans / historical records
10. upsert exams / student_scores / score_items
11. upsert homework / communications / parent_contacts / tasks
12. upsert problem_records → mistake_records
13. upsert attachments 元数据
14. 写入 import_jobs / import_issues
15. 对账查询（见 §7）
16. 提交事务
17. 输出报告（Markdown + JSON）
```

### 5.1 幂等实现

```text
students:          unique(legacy_id)
student_aliases:   unique(student_id, alias)
course_sessions:   unique(legacy_id)
knowledge_points:  unique(legacy_id)
student_scores:    unique(legacy_id)
problem→mistake:   unique(legacy_id)
```

子记录若没有稳定 legacy ID，用确定性规则：

```text
legacy_id = source_sha256_prefix + ":" + student_legacy_id + ":" + index
```

禁止用自增序号生成不可复现的 ID。

### 5.2 重复执行期望

| 场景 | 期望 |
|---|---|
| 同一文件导入两次 | 学生数、课程数、成绩数不变；`updated_at` 可能变化；报告显示 `updated=N` |
| 文件新增 1 名学生 | 只新增 1 条，其余更新/跳过 |
| 文件删除 1 名学生 | **默认不删除**；报告提示“源中缺失”，由教师确认后软删除 |
| 同一学生姓名改动 | 按 `legacy_id` 更新，不新建 |

---

## 6. 回滚方案

### 6.1 两级回滚

| 级别 | 方式 | 适用 |
|---|---|---|
| 批次回滚 | 按 `import_batch_id` 删除本批次新增/还原更新 | 刚导入，影响面小 |
| 快照回滚 | 恢复迁移前 `pg_dump` | 数据错乱、结构变更 |

### 6.2 批次回滚 SQL（模板）

```sql
begin;
delete from mistake_records where import_batch_id = :batch;
delete from student_scores where import_batch_id = :batch;
delete from course_sessions where import_batch_id = :batch;
delete from knowledge_points where import_batch_id = :batch;
delete from students where import_batch_id = :batch;
-- 被更新的记录用备份表/审计 before 还原
commit;
```

前提：核心表加 `import_batch_id uuid` 列（可空）。M02 schema 预留。

### 6.3 回滚验收

- 行数回到迁移前基线。
- 抽查学生详情、课堂、成绩与迁移前一致。
- `import_jobs.status = ROLLED_BACK`。

---

## 7. 对账查询

```sql
-- 总量对账
select 'students' t, count(*) from students where deleted_at is null
union all select 'courses', count(*) from course_sessions where deleted_at is null
union all select 'scores', count(*) from student_scores
union all select 'knowledge', count(*) from knowledge_points where deleted_at is null
union all select 'problems', count(*) from mistake_records;

-- 孤儿检查
select count(*) from course_sessions c
left join students s on s.id = c.student_id
where s.id is null;

select count(*) from student_scores sc
left join students s on s.id = sc.student_id
where s.id is null;

select count(*) from knowledge_points k
left join knowledge_points p on p.id = k.parent_id
where k.parent_id is not null and p.id is null;

-- 重复检查
select legacy_id, count(*) from students group by 1 having count(*) > 1;
select legacy_id, count(*) from course_sessions group by 1 having count(*) > 1;

-- 时间精度
select date_accuracy, count(*) from historical_learning_records group by 1;
```

期望：孤儿 0、重复 0；时间精度未知的记录数量与源一致。

---

## 8. 测试矩阵

| 编号 | 用例 | 输入 | 期望 |
|---|---|---|---|
| MIG-01 | 空库导入 seed v1 | `teacher_seed_v1.json` | 29 学生 / 30 课程；0 错误 |
| MIG-02 | 重复导入 | 同 MIG-01 再跑 | 条数不变；报告 updated |
| MIG-03 | Room 备份导入 | 设备备份 JSON | 与源条数一致；ID 保留 |
| MIG-04 | `.ttseed.json` 导入 | 真实快照 | 14 学生 / 91 课程（以真实文件为准） |
| MIG-05 | 缺失学生引用 | 课程引用不存在学生 | 进 unresolved，不猜归属 |
| MIG-06 | 非法日期 | `2026-13-45` | 该字段 NULL + WARN，不中断批次 |
| MIG-07 | 未知学科 | `天文` | 建 OTHER 学科 + WARN |
| MIG-08 | 重复知识点 | 同名不同 code | 允许；同 code 冲突则合并并记 issue |
| MIG-09 | 批次回滚 | 导入后回滚 | 行数回基线 |
| MIG-10 | 快照恢复 | pg_dump → drop → restore | 对账一致 |
| MIG-11 | 同名不同人 | 两个“王晨然”不同 subject | 不合并；按 legacy_id 区分 |
| MIG-12 | 姓名变更 | 同一 legacy_id 改名 | 更新，不新建 |

---

## 9. 执行 Runbook（M02）

```bash
# 0. 环境
export DATABASE_URL=postgresql://teachtrack_app@127.0.0.1:5432/teachtrack

# 1. 迁移 schema
./gradlew flywayMigrate

# 2. 干跑
./gradlew migrationRun --args="--source=... --dry-run --report=out/dry-run.md"

# 3. 备份
pg_dump --format=custom --no-owner --file=out/pre-migration.dump "$DATABASE_URL"

# 4. 正式导入
./gradlew migrationRun --args="--source=... --batch=2026-11-05-A --report=out/import-A.md"

# 5. 对账
psql "$DATABASE_URL" -f sql/reconcile.sql

# 6. 回滚（如需要）
./gradlew migrationRollback --args="--batch=2026-11-05-A"
```

工具落点：`backend/src/main/java/.../migration/`，以 Spring Boot `CommandLineRunner` + profile `migration` 运行；不把迁移逻辑塞进 Android。

---

## 10. 验收标准（M02）

1. `teacher_seed_v1.json` 连续导入两次，学生/课程/成绩条数不变。
2. 所有迁移学生 `legacy_id` 非空且唯一；历史 ID 可反查。
3. 课程、成绩、错题无孤儿。
4. 真实 `.ttseed.json`（或设备备份）导入后，学生数、课堂数与源文件一致；报告逐项列明。
5. 备份 → 删除测试库 → 恢复 → 对账一致。
6. 回滚后行数回到基线。
7. 报告包含：源 sha256、批次 ID、各表 insert/update/skip、issue 列表、耗时。

---

## 11. 明确禁止

- 用姓名作为唯一键合并学生。
- 为“凑 14 人 / 91 条”而伪造、裁剪或重复数据。
- 重复导入产生重复课堂。
- 迁移时删除 Room 或云端已有数据。
- 没有备份就执行 destructive migration。
- 把 `.ttseed.json` 的未知字段静默丢弃。
