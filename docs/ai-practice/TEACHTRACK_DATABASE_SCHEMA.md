# TeachTrack PostgreSQL 数据库设计

- 目标库：PostgreSQL 16（标准 SQL + 标准扩展）
- Schema 管理：Flyway，禁止手工改生产库
- 字符集：UTF-8，时区统一 `timestamptz`（UTC 存储）
- 主键：`uuid`；历史 ID 存 `legacy_id`
- 删除：软删除（`deleted_at`），删除写墓碑
- 并发：`version integer`，写入必须带 `base_version`

---

## 1. 约定

### 1.1 公共列

所有业务表：

```sql
id              uuid primary key,
legacy_id       text unique,                 -- 历史 ID；新记录可与 id 相同
created_at      timestamptz not null default now(),
updated_at      timestamptz not null default now(),
created_by      text,
updated_by      text,
version         integer not null default 1,
deleted_at      timestamptz
```

说明：

- `legacy_id` 对可迁移实体必填；纯新建表可空。
- `version` 每次成功更新 +1；乐观锁校验在应用层 + `WHERE version = :base`。
- `deleted_at` 非空视为已删除；查询默认过滤。
- 参与数据迁移的表额外增加 `import_batch_id uuid`（可空）：用于按批次回滚与报告，不参与业务逻辑。

### 1.2 枚举

用 `text + CHECK`，不用 PostgreSQL ENUM（便于演进，避免 `ALTER TYPE` 锁）：

```sql
student_status   : UNKNOWN, ACTIVE, PAUSED, ENDED, ARCHIVED
mastery_level    : 0..4（仅保留旧模型兼容；新模型用数值）
question_type    : SINGLE_CHOICE, MULTI_CHOICE, FILL_BLANK, EXPERIMENT,
                   CALCULATION, SHORT_ANSWER, ESSAY, OTHER
difficulty       : 1..5
review_status    : DRAFT, AI_PARSED, NEEDS_REVIEW, NEEDS_TEACHER_REVIEW, APPROVED, REJECTED
analysis_status  : UPLOADED, PARSING, AI_PARSED, NEEDS_REVIEW, APPROVED, REJECTED, PARSE_FAILED
source_type      : SELF_CREATED, TEACHER_IMPORTED, STUDENT_EXAM, AUTHORIZED_BANK, AI_GENERATED, AI_VARIANT
importance       : PRIMARY, SECONDARY, RELATED
mistake_type     : KNOWLEDGE_GAP, CONCEPT_ERROR, FORMULA_ERROR, CALCULATION_ERROR,
                   READING_ERROR, UNIT_ERROR, LOGIC_ERROR, MEMORY_ERROR,
                   TRANSFER_ERROR, CARELESS, UNKNOWN, NEEDS_TEACHER_CONFIRMATION
practice_mode    : BASIC, ERROR_FOCUS, TOPIC, COMPREHENSIVE, PRE_EXAM, RETEST
practice_status  : DRAFT, GENERATED, TEACHER_REVIEWED, APPROVED, EXPORTED, COMPLETED, CANCELLED
practice_source  : BANK, MISTAKE_VARIANT, SIMILAR, AI_ORIGINAL
asset_type       : IMAGE, DIAGRAM, GRAPH, FORMULA, PDF_PAGE, OTHER
role             : OWNER, TEACHER, AI_READ, AI_WRITE_DRAFT, ADMIN
actor_type       : USER, AI, SYSTEM
visibility       : PRIVATE
```

### 1.3 ID 生成

- 应用生成 UUIDv4（新记录）。
- 历史 ID 用 UUIDv5：

```text
NS = 6f1c2f8a-4c1e-5b7a-9d3e-7a2b1c9d0e4f
id = uuidv5(NS, "student:seed-student-001")
```

- 服务端不依赖 `gen_random_uuid()`；避免 pgcrypto 依赖，端口更稳。若使用默认值，需显式 `CREATE EXTENSION IF NOT EXISTS pgcrypto`。

---

## 2. Flyway 迁移计划

| 文件 | 内容 |
|---|---|
| `V001__extensions_and_common.sql` | 扩展、审计函数、`set_updated_at()` 触发器 |
| `V002__identity_and_students.sql` | users、roles、api_tokens、subjects、students、student_aliases、parent_contacts |
| `V003__courses_and_knowledge.sql` | course_sessions、knowledge_points |
| `V004__question_bank.sql` | questions、question_versions、options、answers、knowledge、assets |
| `V005__exam_analysis.sql` | exam_papers、exam_questions、student_answers、exam_question_analyses、exam_analysis_reports |
| `V006__mistakes_and_mastery.sql` | mistake_records、student_mastery |
| `V007__practice.sql` | practice_sets、practice_items、submissions、results |
| `V008__sync_and_audit.sql` | sync_devices、sync_changes、audit_logs、import_jobs、import_issues |
| `V009__indexes_and_search.sql` | 复合索引、trigram、全文表达式索引 |
| `V010__seed_reference.sql` | 学科与知识点基础数据（可选，幂等 upsert） |

迁移规则：

1. 每个文件只做一类变更；不在生产执行 `DROP COLUMN`（先软弃用）。
2. 加索引用 `CREATE INDEX CONCURRENTLY`（Flyway 需 `flyway.mixed=true` 或拆脚本；生产用单独运维脚本）。
3. 任何 destructive 迁移前必须有 `pg_dump` 且演练恢复。

---

## 3. 核心表 DDL

### 3.1 身份与权限

```sql
create table users (
  id uuid primary key,
  email text unique,
  display_name text not null,
  password_hash text,                    -- 单用户阶段可为空，走 OAuth
  status text not null default 'ACTIVE' check (status in ('ACTIVE','DISABLED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table user_roles (
  user_id uuid not null references users(id),
  role text not null check (role in ('OWNER','TEACHER','AI_READ','AI_WRITE_DRAFT','ADMIN')),
  primary key (user_id, role)
);

create table api_tokens (
  id uuid primary key,
  user_id uuid not null references users(id),
  name text not null,
  token_prefix text not null,            -- 前 8 位，便于识别
  token_hash bytea not null unique,      -- SHA-256
  scopes text[] not null,                -- ai.read / ai.write.draft
  rate_limit_per_min integer not null default 60,
  expires_at timestamptz,
  last_used_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);
create index api_tokens_user_idx on api_tokens(user_id);
```

### 3.2 学科、学生、别名

```sql
create table subjects (
  id uuid primary key,
  code text not null unique,             -- JHS_SCIENCE / SHS_PHYSICS / SHS_BIOLOGY
  name text not null,                    -- 初中科学 / 高中物理 / 高中生物
  stage text not null check (stage in ('JUNIOR','SENIOR','OTHER')),
  sort_order integer not null default 0,
  created_at timestamptz not null default now()
);

create table students (
  id uuid primary key,
  legacy_id text unique,
  full_name text,
  display_name text not null,
  status text not null default 'ACTIVE' check (status in ('UNKNOWN','ACTIVE','PAUSED','ENDED','ARCHIVED')),
  gender text,
  current_grade text,
  grade_at_record text,
  name_completeness text not null default 'CONFIRMED'
      check (name_completeness in ('CONFIRMED','PARTIAL','UNKNOWN')),
  school text default '',
  class_name text default '',
  subject_id uuid references subjects(id),
  notes text default '',
  start_date date,
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index students_status_idx on students(status) where deleted_at is null;
create index students_name_trgm_idx on students using gin (display_name gin_trgm_ops);
create unique index students_legacy_uk on students(legacy_id) where legacy_id is not null;

create table student_aliases (
  id uuid primary key,
  student_id uuid not null references students(id) on delete cascade,
  alias text not null,
  alias_type text not null default 'NICKNAME'
      check (alias_type in ('NICKNAME','FORMER_NAME','MISSPELLING','TEACHING_NAME','OTHER')),
  source text default '',
  created_at timestamptz not null default now(),
  unique (student_id, alias)
);
create index student_aliases_alias_idx on student_aliases(alias);

-- 结构化学生画像：迁移时不得丢弃 Prompt 中的 academicProfile /
-- learningCharacteristics / riskFlags / specialRules
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

`parent_contacts` 从 Room 迁移：

```sql
create table parent_contacts (
  id uuid primary key,
  legacy_id text unique,
  student_id uuid not null references students(id) on delete cascade,
  name text default '',
  phone text default '',
  relation text default '',
  is_primary boolean not null default false,
  notes text default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index parent_contacts_student_idx on parent_contacts(student_id);
```

> 联系方式属敏感字段：默认 API 不返回 `phone`，仅 `OWNER`/`TEACHER` 在详情页显式请求时返回。

### 3.3 课堂与知识点

```sql
create table course_sessions (
  id uuid primary key,
  legacy_id text unique,
  student_id uuid not null references students(id),
  date date not null,
  start_time text default '',
  end_time text default '',
  subject_id uuid references subjects(id),
  lesson_title text default '',
  lesson_content text default '',
  performance text default '',
  problems text default '',
  homework_summary text default '',
  next_lesson_plan text default '',
  teacher_notes text default '',
  learning_status text default '',
  source text not null default 'LOCAL',
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index course_sessions_student_date_idx on course_sessions(student_id, date desc);

create table knowledge_points (
  id uuid primary key,
  legacy_id text unique,
  parent_id uuid references knowledge_points(id),
  subject_id uuid not null references subjects(id),
  grade text default '',
  textbook_version text default '',
  chapter text default '',
  section text default '',
  name text not null,
  code text,
  description text default '',
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (subject_id, code)
);
create index knowledge_points_parent_idx on knowledge_points(parent_id);
create index knowledge_points_subject_grade_idx on knowledge_points(subject_id, grade);
create index knowledge_points_name_trgm_idx on knowledge_points using gin (name gin_trgm_ops);
```

迁移时保留原有 0–4 级进度到兼容表：

```sql
create table student_knowledge_progress_legacy (
  student_id uuid references students(id),
  knowledge_point_id uuid references knowledge_points(id),
  level smallint not null check (level between 0 and 4),
  last_updated timestamptz,
  notes text default '',
  primary key (student_id, knowledge_point_id)
);
```

新掌握度以 `student_mastery` 为准；旧级别只作为“历史上下文”展示，不参与新公式。

### 3.4 题库

```sql
create table questions (
  id uuid primary key,
  legacy_id text unique,
  subject_id uuid not null references subjects(id),
  grade text default '',
  textbook_version text default '',
  question_type text not null check (question_type in
    ('SINGLE_CHOICE','MULTI_CHOICE','FILL_BLANK','EXPERIMENT','CALCULATION','SHORT_ANSWER','ESSAY','OTHER')),
  difficulty smallint not null default 3 check (difficulty between 1 and 5),
  stem text not null,
  stem_hash text not null,               -- sha256(stem)
  normalized_hash text not null,         -- 规范化题干 hash
  source_type text not null check (source_type in
    ('SELF_CREATED','TEACHER_IMPORTED','STUDENT_EXAM','AUTHORIZED_BANK','AI_GENERATED','AI_VARIANT')),
  source_name text default '',
  copyright_status text not null default 'PERSONAL_USE'
      check (copyright_status in ('PERSONAL_USE','AUTHORIZED','PUBLIC_DOMAIN','UNKNOWN')),
  usage_scope text not null default 'PRIVATE_TEACHING'
      check (usage_scope in ('PRIVATE_TEACHING','CLASSROOM','PUBLIC')),
  review_status text not null default 'DRAFT' check (review_status in
    ('DRAFT','AI_PARSED','NEEDS_REVIEW','NEEDS_TEACHER_REVIEW','APPROVED','REJECTED')),
  quality_score numeric(4,2),
  ability_tags text[] not null default '{}',
  created_by text,
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index questions_filter_idx on questions(subject_id, grade, question_type, difficulty)
  where deleted_at is null;
create index questions_review_idx on questions(review_status) where deleted_at is null;
create index questions_stem_hash_idx on questions(stem_hash);
create index questions_normalized_hash_idx on questions(normalized_hash);
create index questions_stem_trgm_idx on questions using gin (stem gin_trgm_ops);

create table question_versions (
  id uuid primary key,
  question_id uuid not null references questions(id) on delete cascade,
  version integer not null,
  snapshot jsonb not null,
  change_note text default '',
  created_by text,
  created_at timestamptz not null default now(),
  unique (question_id, version)
);

create table question_options (
  id uuid primary key,
  question_id uuid not null references questions(id) on delete cascade,
  option_key text not null,
  content text not null,
  sort_order integer not null default 0,
  unique (question_id, option_key)
);

create table question_answers (
  question_id uuid primary key references questions(id) on delete cascade,
  standard_answer text not null,
  analysis text default '',
  scoring_points text default '',
  solution_steps text default '',
  updated_at timestamptz not null default now()
);

create table question_knowledge (
  question_id uuid not null references questions(id) on delete cascade,
  knowledge_point_id uuid not null references knowledge_points(id),
  importance text not null default 'PRIMARY'
      check (importance in ('PRIMARY','SECONDARY','RELATED')),
  primary key (question_id, knowledge_point_id)
);
create index question_knowledge_kp_idx on question_knowledge(knowledge_point_id);

create table assets (
  id uuid primary key,
  storage_provider text not null default 's3',
  storage_key text not null,
  mime_type text not null,
  size_bytes bigint not null default 0,
  sha256 text not null,
  visibility text not null default 'PRIVATE' check (visibility = 'PRIVATE'),
  width integer,
  height integer,
  created_at timestamptz not null default now(),
  unique (storage_provider, storage_key),
  unique (sha256)
);

create table question_assets (
  id uuid primary key,
  question_id uuid not null references questions(id) on delete cascade,
  asset_id uuid not null references assets(id),
  asset_type text not null check (asset_type in
    ('IMAGE','DIAGRAM','GRAPH','FORMULA','PDF_PAGE','OTHER')),
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  unique (question_id, asset_id)
);
```

### 3.5 试卷与逐题分析

```sql
create table exam_papers (
  id uuid primary key,
  legacy_id text unique,
  student_id uuid not null references students(id),
  subject_id uuid not null references subjects(id),
  exam_name text not null,
  exam_date date,
  full_score numeric(7,2),
  score numeric(7,2),
  storage_asset_id uuid references assets(id),
  source_type text not null default 'STUDENT_EXAM'
      check (source_type in ('SELF_CREATED','TEACHER_IMPORTED','STUDENT_EXAM','AUTHORIZED_BANK','AI_GENERATED','AI_VARIANT')),
  analysis_status text not null default 'UPLOADED' check (analysis_status in
    ('UPLOADED','PARSING','AI_PARSED','NEEDS_REVIEW','APPROVED','REJECTED','PARSE_FAILED')),
  parse_confidence numeric(4,3),
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index exam_papers_student_date_idx on exam_papers(student_id, exam_date desc);

create table exam_questions (
  id uuid primary key,
  exam_paper_id uuid not null references exam_papers(id) on delete cascade,
  question_id uuid references questions(id),   -- 可空：试卷原题未入题库
  question_number text not null,
  question_type text,
  score numeric(7,2) not null default 0,
  full_score numeric(7,2) not null default 0,
  sort_order integer not null default 0,
  unique (exam_paper_id, question_number)
);

create table student_answers (
  id uuid primary key,
  student_id uuid not null references students(id),
  exam_paper_id uuid not null references exam_papers(id) on delete cascade,
  exam_question_id uuid not null references exam_questions(id) on delete cascade,
  question_id uuid references questions(id),
  student_answer text default '',
  standard_answer text default '',
  score numeric(7,2) not null default 0,
  full_score numeric(7,2) not null default 0,
  is_correct boolean,
  teacher_comment text default '',
  ai_comment text default '',
  confidence numeric(4,3),
  created_at timestamptz not null default now(),
  unique (exam_paper_id, exam_question_id)
);

create table exam_question_analyses (
  id uuid primary key,
  exam_paper_id uuid not null references exam_papers(id) on delete cascade,
  exam_question_id uuid not null references exam_questions(id) on delete cascade,
  student_id uuid not null references students(id),
  ability text default '',
  knowledge_summary text default '',
  mistake_type text not null default 'UNKNOWN' check (mistake_type in
    ('KNOWLEDGE_GAP','CONCEPT_ERROR','FORMULA_ERROR','CALCULATION_ERROR','READING_ERROR',
     'UNIT_ERROR','LOGIC_ERROR','MEMORY_ERROR','TRANSFER_ERROR','CARELESS','UNKNOWN',
     'NEEDS_TEACHER_CONFIRMATION')),
  mistake_reason text default '',
  confidence numeric(4,3),
  difficulty smallint,
  taught_before boolean not null default false,
  last_taught_at timestamptz,
  wrong_before boolean not null default false,
  repeat_count integer not null default 0,
  suggested_training text default '',
  potential_score_gain numeric(7,2) not null default 0,
  review_status text not null default 'AI_PARSED'
      check (review_status in ('AI_PARSED','NEEDS_REVIEW','APPROVED','REJECTED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (exam_question_id)
);

create table exam_analysis_reports (
  id uuid primary key,
  exam_paper_id uuid not null references exam_papers(id) on delete cascade,
  summary jsonb not null default '{}',
  by_question_type jsonb not null default '[]',
  by_knowledge jsonb not null default '[]',
  by_mistake_type jsonb not null default '[]',
  ai_provider text default '',
  ai_model text default '',
  prompt_version text default '',
  generated_at timestamptz not null default now(),
  review_status text not null default 'DRAFT'
);
```

### 3.6 错题与掌握度

```sql
create table mistake_records (
  id uuid primary key,
  legacy_id text unique,                 -- 由 problem_records 迁移时生成
  student_id uuid not null references students(id),
  question_id uuid references questions(id),
  exam_paper_id uuid references exam_papers(id),
  knowledge_point_id uuid references knowledge_points(id),
  mistake_type text not null default 'UNKNOWN' check (mistake_type in
    ('KNOWLEDGE_GAP','CONCEPT_ERROR','FORMULA_ERROR','CALCULATION_ERROR','READING_ERROR',
     'UNIT_ERROR','LOGIC_ERROR','MEMORY_ERROR','TRANSFER_ERROR','CARELESS','UNKNOWN',
     'NEEDS_TEACHER_CONFIRMATION')),
  mistake_reason text default '',
  confidence numeric(4,3),
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  repeat_count integer not null default 1,
  status text not null default 'OPEN' check (status in ('OPEN','REVIEWING','RESOLVED','IGNORED')),
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index mistake_student_kp_idx on mistake_records(student_id, knowledge_point_id);
create index mistake_question_idx on mistake_records(question_id);

create table student_mastery (
  student_id uuid not null references students(id),
  knowledge_point_id uuid not null references knowledge_points(id),
  mastery_score numeric(5,2) not null default 0 check (mastery_score between 0 and 100),
  confidence numeric(4,3) not null default 0,
  practice_count integer not null default 0,
  correct_count integer not null default 0,
  wrong_count integer not null default 0,
  last_practiced_at timestamptz,
  last_wrong_at timestamptz,
  last_correct_at timestamptz,
  trend numeric(4,3) not null default 0,
  formula_version text not null default 'v1',
  version integer not null default 1,
  updated_at timestamptz not null default now(),
  primary key (student_id, knowledge_point_id)
);
create index mastery_kp_score_idx on student_mastery(knowledge_point_id, mastery_score);
```

### 3.7 练习

```sql
create table practice_sets (
  id uuid primary key,
  student_id uuid not null references students(id),
  subject_id uuid not null references subjects(id),
  mode text not null check (mode in ('BASIC','ERROR_FOCUS','TOPIC','COMPREHENSIVE','PRE_EXAM','RETEST')),
  title text not null,
  strategy jsonb not null default '{}',
  total_score numeric(7,2) not null default 0,
  estimated_minutes integer not null default 0,
  status text not null default 'DRAFT' check (status in
    ('DRAFT','GENERATED','TEACHER_REVIEWED','APPROVED','EXPORTED','COMPLETED','CANCELLED')),
  generated_by text not null default 'TEACHER',
  ai_provider text default '',
  ai_model text default '',
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index practice_sets_student_idx on practice_sets(student_id, created_at desc);

create table practice_items (
  id uuid primary key,
  practice_set_id uuid not null references practice_sets(id) on delete cascade,
  question_id uuid not null references questions(id),
  source_kind text not null check (source_kind in ('BANK','MISTAKE_VARIANT','SIMILAR','AI_ORIGINAL')),
  selection_reason text default '',
  score numeric(7,2) not null default 0,
  sort_order integer not null default 0,
  unique (practice_set_id, sort_order)
);

create table practice_submissions (
  id uuid primary key,
  practice_set_id uuid not null references practice_sets(id),
  student_id uuid not null references students(id),
  submitted_at timestamptz not null default now(),
  total_score numeric(7,2) not null default 0,
  full_score numeric(7,2) not null default 0,
  created_at timestamptz not null default now()
);

create table practice_item_results (
  id uuid primary key,
  submission_id uuid not null references practice_submissions(id) on delete cascade,
  practice_item_id uuid not null references practice_items(id),
  student_answer text default '',
  score numeric(7,2) not null default 0,
  full_score numeric(7,2) not null default 0,
  is_correct boolean,
  ai_comment text default '',
  teacher_comment text default '',
  created_at timestamptz not null default now(),
  unique (submission_id, practice_item_id)
);
```

### 3.8 同步

```sql
create table sync_devices (
  id uuid primary key,
  user_id uuid not null references users(id),
  device_name text not null,
  platform text not null default 'android',
  app_version text default '',
  last_seen_at timestamptz,
  last_cursor bigint not null default 0,
  created_at timestamptz not null default now(),
  unique (user_id, id)
);

create table sync_changes (
  seq bigserial primary key,
  entity_type text not null,
  entity_id uuid not null,
  operation text not null check (operation in ('UPSERT','DELETE')),
  version integer not null,
  device_id uuid,
  payload jsonb,                          -- 可选：轻量字段；完整数据由实体接口读取
  changed_at timestamptz not null default now()
);
create index sync_changes_entity_idx on sync_changes(entity_type, entity_id, seq desc);
create index sync_changes_seq_idx on sync_changes(seq);
```

写入约定：

- 实体写与 `sync_changes` 写必须在同一事务。
- `payload` 只放客户端渲染必需字段，避免变更日志膨胀；客户端拿到变更后可批量 `GET` 实体。
- 清理：保留 90 天；`seq` 不回退。

### 3.9 审计与导入

```sql
create table audit_logs (
  id uuid primary key,
  actor_type text not null check (actor_type in ('USER','AI','SYSTEM')),
  actor_id text,
  token_id uuid,
  provider text,
  model text,
  request_id text,
  device_id text,
  action text not null,
  entity_type text,
  entity_id uuid,
  before jsonb,
  after jsonb,
  result text not null default 'SUCCESS' check (result in ('SUCCESS','FAILURE')),
  error_code text,
  duration_ms integer,
  created_at timestamptz not null default now()
);
create index audit_entity_idx on audit_logs(entity_type, entity_id, created_at desc);
create index audit_actor_idx on audit_logs(actor_type, actor_id, created_at desc);

create table import_jobs (
  id uuid primary key,
  source_name text not null,
  source_sha256 text not null,
  source_format text not null,            -- TTSEED / BACKUP_JSON / WORKSPACE_JSONL / SEED_V1
  status text not null default 'RUNNING'
      check (status in ('RUNNING','COMPLETED','FAILED','ROLLED_BACK')),
  students_imported integer not null default 0,
  sessions_imported integer not null default 0,
  questions_imported integer not null default 0,
  issues_count integer not null default 0,
  report jsonb not null default '{}',
  started_at timestamptz not null default now(),
  finished_at timestamptz
);

create table import_issues (
  id uuid primary key,
  import_job_id uuid not null references import_jobs(id) on delete cascade,
  severity text not null check (severity in ('INFO','WARN','ERROR')),
  entity_type text,
  source_ref text,
  message text not null,
  created_at timestamptz not null default now()
);
```

批次回滚：被导入实体统一写 `import_batch_id uuid`（可加在核心表），回滚时按批次删除/恢复；更彻底的回滚用迁移前 `pg_dump` 快照。

---

## 4. Room → PostgreSQL 映射

| Room 表 | PostgreSQL 表 | 迁移规则 |
|---|---|---|
| students | students | `legacy_id = Room.id`；name→full_name/display_name；status 原样 |
| course_sessions | course_sessions | `legacy_id = id`；subject 字符串→subject_id（映射表） |
| knowledge_points | knowledge_points | 保留 parentId 关系；补 subject_id/code |
| student_knowledge_progress | student_knowledge_progress_legacy | 原样保留；同时生成 student_mastery 初始值（`confidence=0`，`mastery=level*25` 仅作初始展示，标注来源） |
| student_knowledge_events | 不进 PG（保留 Room 历史）或 `knowledge_events` 可选表 | 第一版不迁 |
| exams + student_scores | exam_papers + student_answers 不适用 | 成绩继续用 `student_scores` 兼容表（见下） |
| student_scores | student_scores | 保留旧成绩语义，题库试卷另建 `exam_papers` |
| score_items | score_items | 原样 |
| homework/communications/lesson_plans/tasks/parent_contacts | 同名表 | 原样，补公共列 |
| problem_records | mistake_records | `title/description` → `mistake_reason`；`mistake_type=UNKNOWN`；`legacy_id` 保留 |
| attachments | assets + 关联表 | uri/relativePath 只留在 Android；云端另存 storage_key |
| historical_learning_records / student_progress / unresolved | 同名表 | 原样，用于历史关联查询 |
| templates/tags/student_tags | 同名表 | 原样 |

为避免破坏现有成绩语义，`student_scores` / `score_items` 在 PG 中保留：

```sql
create table student_scores (
  id uuid primary key,
  legacy_id text unique,
  student_id uuid not null references students(id),
  exam_id uuid,
  exam_name text default '',
  exam_date date,
  subject_id uuid references subjects(id),
  score numeric(7,2) not null,
  full_score numeric(7,2) not null,
  class_rank integer,
  grade_rank integer,
  class_size integer,
  grade_size integer,
  notes text default '',
  score_label text default '',
  full_score_label text default '',
  score_accuracy text not null default 'EXACT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table score_items (
  id uuid primary key,
  legacy_id text unique,
  score_id uuid not null references student_scores(id) on delete cascade,
  category text default '',
  lost_score numeric(7,2) not null default 0,
  knowledge_point_id uuid references knowledge_points(id),
  notes text default ''
);
```

> 新试卷分析使用 `exam_papers`，不覆盖旧 `student_scores`；两者通过 `student_id + exam_name + date` 软关联，迁移报告列出无法关联项。

---

## 5. 索引与搜索

### 5.1 必须索引

| 表 | 索引 |
|---|---|
| students | status、display_name trgm、legacy_id |
| course_sessions | (student_id, date desc) |
| knowledge_points | parent_id、subject_id+grade、name trgm |
| questions | subject+grade+type+difficulty、review_status、stem_hash、normalized_hash、stem trgm |
| question_knowledge | knowledge_point_id |
| exam_papers | student_id+exam_date desc |
| student_answers | exam_paper_id |
| mistake_records | student_id+knowledge_point_id |
| student_mastery | knowledge_point_id+mastery_score |
| practice_sets | student_id+created_at desc |
| sync_changes | seq、entity |
| audit_logs | entity、actor |

不无脑建索引；上线后用 `EXPLAIN (ANALYZE, BUFFERS)` 验证真实慢查询。

### 5.2 中文搜索

- PostgreSQL 内置全文检索对中文分词不友好；第一版用 `pg_trgm` + `ILIKE`，配合规范化题干字段。
- 数据量到 10 万题并出现 >500ms 查询时，再评估 `zhparser` / OpenSearch / pgvector。
- 预留接口：`QuestionSearchService`，实现可替换。

---

## 6. 备份与恢复

### 6.1 备份

```bash
pg_dump --format=custom --no-owner --file=teachtrack-$(date +%F).dump "$DATABASE_URL"
```

- 每日 03:17（避开整点）。
- 上传到独立 Bucket：`teachtrack-backups/postgres/YYYY/MM/`。
- 保留：日 7 / 周 4 / 月 6。
- 备份文件记录 sha256 与行数摘要。

### 6.2 恢复演练（每个里程碑至少一次）

```bash
createdb teachtrack_restore_test
pg_restore --clean --no-owner -d teachtrack_restore_test teachtrack-YYYY-MM-DD.dump
psql -d teachtrack_restore_test -c "select count(*) from students;"
psql -d teachtrack_restore_test -c "select count(*) from course_sessions;"
```

验收：学生数、课堂数、题库数、练习数与备份时记录一致；抽查 3 个学生详情一致。

### 6.3 迁移前

1. 全库 `pg_dump`。
2. 记录各表行数基线。
3. 迁移在事务中执行；失败回滚。
4. 迁移后跑对账 SQL；不一致则停止并回滚。

---

## 7. 数据库角色与连接

| 角色 | 权限 |
|---|---|
| `teachtrack_app` | 业务表 SELECT/INSERT/UPDATE；无 DDL、无 DELETE（软删） |
| `teachtrack_migrator` | Flyway 专用，DDL |
| `teachtrack_readonly` | 报表/诊断只读 |
| 管理员 | 仅运维，不用于应用连接；不授予 AI |

- 生产库不开放公网；只允许 backend 所在网络访问。
- 连接池：HikariCP，最大 20，单实例；多实例时按实例数调整。
- 所有时间写 UTC；客户端本地化显示。

---

## 8. 数据保留与清理

| 数据 | 保留 |
|---|---|
| 学生/课堂/成绩 | 永久（软删除） |
| 试卷原图 | 永久，除非教师显式删除 |
| 同步变更 | 90 天 |
| 审计 | 3 年 |
| 导入报告 | 永久 |
| AI 请求原文 | 不落库；只存摘要与 requestId |

---

## 9. 验收查询（M02）

```sql
-- 学生数
select count(*) from students where deleted_at is null;

-- 历史 ID 保持
select count(*) from students where legacy_id is null;          -- 期望 0（迁移数据）

-- 课堂数
select count(*) from course_sessions where deleted_at is null;

-- 重复导入后不增加
-- 连续执行两次迁移，比较两次 count

-- 知识点树完整性
select count(*) from knowledge_points kp
where kp.parent_id is not null
  and not exists (select 1 from knowledge_points p where p.id = kp.parent_id);
-- 期望 0

-- 题库审核状态
select review_status, count(*) from questions group by 1;
-- 期望不存在 AI 直接 APPROVED 的记录（除非教师显式操作）
```

---

## 10. 实施补充（M02–M06 已落地）

Flyway 实际版本比设计稿多 4 个增量迁移：

| 文件 | 内容 | 原因 |
|---|---|---|
| `V011` | `course_sessions.date` 允许 NULL | 历史课堂日期可能未知，禁止伪造日期 |
| `V012` | `questions.updated_by` | 题目更新审计 |
| `V013` | `sync_operations` | 同步幂等键 |
| `V014` | `exam_questions.stem/standard_answer` | 解析题在入库前保留原题干与答案 |

补充约定：

- `practice_attempts` 先插入、判分后更新总分，避免结果表外键顺序问题。
- `student_mastery.trend` 为 `numeric`，写入时显式 `?::numeric`。
- PostgreSQL `numeric` 读取使用 `getBigDecimal()`，不要用 `getObject(Double.class)`。
- `sync_operations.operation_id` 唯一；重复推送返回原结果。
- `exam_questions` 可先于题库存在；教师审核时再写入 `questions` 并回填 `question_id`。
