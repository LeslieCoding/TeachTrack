# TeachTrack 目标能力差距分析（M01）

- 对照基准：`D:\SoftWare\TeachTrack` 真实代码（git `7f1a832`），不是文档描述
- 对照对象：本次《AI 精准教学与云端题库系统完整开发 Prompt》§1–§51
- 状态定义：
  - ✅ **已实现**：代码存在且可用，有测试或设备证据
  - 🟡 **部分实现**：有底座/字段/单点能力，但达不到目标语义
  - ❌ **未实现**：无代码
  - ⚠️ **冲突**：目标要求与现实现有矛盾，需要设计决策
  - 🔺 **风险**：已存在但不满足新场景的质量/安全要求

---

## 0. 总览

| 目标域 | 现状 | 结论 |
|---|---|---|
| 学生档案 / 课堂 / 成绩 / 规划 / 沟通 | ✅ 完整 | 直接复用，不重写 |
| AI 命令层（68 动作） | ✅ 完整 | 复用同一 Registry，扩展新动作 |
| 本地离线可用 | ✅ 完整 | 必须保持 |
| 题库 / 知识点树（教学级） | 🟡 知识点有树，题库无 | 新增题库域 |
| 试卷 / OCR / 逐题分析 | ❌ | 全新子系统 |
| 错题与错因分类 | 🟡 只有文字 problem_records | 新增结构化错题 |
| 掌握度模型 | 🟡 5 档枚举 | 新增数值化 mastery |
| 精准练习 / 导出 | ❌ | 全新子系统 |
| 多设备在线同步 | 🟡 文件型事件日志 | 新增服务端同步，保留文件同步 |
| 后端 / PostgreSQL / Flyway | ❌ | 新增 |
| 对象存储 | ❌ | 新增 |
| 外部 AI / MCP | 🟡 仅本机 Agent 桥 | 新增 MCP 服务 |
| 权限 / 审计 | 🟡 单用户 + 动作级审计 | 扩展角色、Token、字段级审计 |
| 备份 / 恢复演练 | 🟡 本地 JSON 备份 | 新增 PostgreSQL 备份与恢复演练 |

---

## 1. 逐节对照

### §2 已有学生数据

| 要求 | 状态 | 证据 / 差距 |
|---|---|---|
| 保留历史学生 ID | ✅ | `HistoricalSeedImporter` 确定性 ID；导入前按姓名/别名复用已有 id |
| `UPSERT_BY_ID` | ✅ | `@Insert(REPLACE)` + 确定性 ID；重复导入幂等 |
| 14 学生 / 91 CourseSession 快照 | ⚠️ | 仓库无 `.ttseed.json`；现有 seed 是 29 学生 / 30 课程；设备记录 49 学生。**必须拿到真实快照** |
| alias / academicProfile / learningCharacteristics / riskFlags / coursePlans / specialRules / courseSessionIds | ❌ | 学生表只有 notes 文本；无 alias 表、无结构化画像、无 riskFlags、无 specialRules |
| parent contact | 🟡 | `parent_contacts` 有 name/phone/relation/isPrimary/notes；无脱敏策略 |
| knowledgeProgress | 🟡 | 只有 0–4 级 + 事件；无分值/置信度 |
| scores / communications | ✅ | 表与导入均有 |

### §3 重点学生策略

| 要求 | 状态 | 说明 |
|---|---|---|
| 用历史课堂辅助诊断 | 🟡 | 有 course_sessions + knowledge_progress；无“该知识点在哪节课讲过”的关联查询 |
| 间隔复习提醒 | ❌ | 无复习调度/提醒 |
| 分层练习（基础→中档→综合） | ❌ | 无练习系统 |
| 初高中题库隔离 | ❌ | 无题库；知识点有 subject 字段但无强制隔离 |
| 知识记忆 + 题型训练双轨 | ❌ | 无练习类型/策略模型 |

### §4–§5 架构与技术栈

| 要求 | 状态 | 说明 |
|---|---|---|
| Android → HTTPS → Spring Boot → PostgreSQL | ❌ | 无后端；当前是单机 Room |
| Java 21 + Spring Boot 3.x | ❌ | 无 `backend/` |
| PostgreSQL + Flyway | ❌ | 无 |
| S3 兼容对象存储抽象 | ❌ | 无 |
| Room/SQLite 离线缓存 | ✅ | Room 已是本地权威；需增加“服务端缓存”语义 |
| Retrofit/OkHttp/WorkManager | 🟡 | OkHttp 已有（OpenMinis）；无 Retrofit；**无 WorkManager** |
| `AiProvider` 抽象 | 🟡 | 只有 `ProviderGateway`（NL→命令），无分析/生成/审核接口 |

### §6 数据库设计

| 要求 | 状态 | 说明 |
|---|---|---|
| UUID 主键 | ⚠️ | 现有全部 TEXT，含 `seed-student-001`；**不得重生成**。需 UUID + legacy_id 双 ID |
| students 字段 | 🟡 | 缺 version/created_at 类型规范/软删除 |
| student_aliases | ❌ | 无 |
| subjects | ❌ | 学科是字符串，无学科表 |
| knowledge_points 树 | 🟡 | 有 parentId，但缺 textbook_version/chapter/section/code，无唯一约束 |
| course_sessions | ✅ | 字段基本齐全；缺 learning_status/source/version |
| questions / versions / options / answers / knowledge / assets | ❌ | 全部没有 |
| 题型/难度/审核状态/质量分 | ❌ | 无 |

### §7 试卷数据

| 要求 | 状态 | 说明 |
|---|---|---|
| exam_papers | 🟡 | 有 `exams`，但没有 `student_id`、`storage_key`、`analysis_status`、`full_score/score`（分数在 student_scores） |
| exam_questions | ❌ | 无 |
| student_answers | ❌ | 无逐题答案/得分/评语 |

### §8 错题系统

| 要求 | 状态 | 说明 |
|---|---|---|
| mistake_records | 🟡 | `problem_records` 可存文字问题；无 question_id、mistake_type、confidence、repeat_count、last_seen_at |
| 错因枚举 | ❌ | 无；只有自由文本 |
| UNKNOWN / NEEDS_TEACHER_CONFIRMATION | ❌ | 无状态机；AI 无法标注“不确定” |

### §9 掌握度

| 要求 | 状态 | 说明 |
|---|---|---|
| student_mastery 数值 0–100 | 🟡 | 只有 `MasteryLevel` 0–4 |
| 综合难度/题量/最近/重复/课堂/复测/时间衰减 | ❌ | 无算法、无字段 |
| 置信度 / trend | ❌ | 无 |

### §10–§15 AI 精准练习

| 要求 | 状态 | 说明 |
|---|---|---|
| 一级功能“AI 精准练习” | ❌ | 无 UI、无领域模型 |
| 试卷上传/解析/切题/题号/分值/批改识别 | ❌ | 无 OCR、无解析器 |
| 人工确认状态机 DRAFT…REJECTED | 🟡 | 题库不存在；现有审核只有命令确认 |
| 逐题分析字段 | ❌ | 无 |
| 按题型/知识点/错因汇总 | ❌ | 无 |
| 历史课堂自动关联 | 🟡 | 数据在，查询未实现 |
| 练习生成优先级（题库→错题变式→相似题→AI原创） | ❌ | 无 |
| 练习参数/模式 | ❌ | 无 |

### §16–§17 题目校验与去重

| 要求 | 状态 | 说明 |
|---|---|---|
| 10 项质量校验 | ❌ | 无 |
| NEEDS_TEACHER_REVIEW | ❌ | 无 |
| exact/near/semantic 去重 | ❌ | 无；无 hash 字段、无 pgvector |

### §18 文件存储

| 要求 | 状态 | 说明 |
|---|---|---|
| 对象存储 + 私有 Bucket + Signed URL | ❌ | 无 |
| 目录规范 `teachtrack/students/{id}/exams` | ❌ | 无 |
| sha256 去重 | 🟡 | `attachments.sha256` 字段已存在，未用于去重 |

### §19 PostgreSQL 部署与访问边界

| 要求 | 状态 | 说明 |
|---|---|---|
| 托管 PostgreSQL / Docker 开发库 | ❌ | 无 |
| Android 不直连数据库 | ✅ | 现在根本不连数据库；新增后端后必须保持 |
| AI 不拿管理员账号 | ✅（无 AI DB 通路） | 新增 MCP 时必须用业务 API + 只读/草稿权限 |

### §20–§21 后端与 API

| 要求 | 状态 | 说明 |
|---|---|---|
| 14 个后端模块 | ❌ | 无后端 |
| REST API 全集 | ❌ | 无 |
| 软删除 | 🟡 | `attachments.deletedAt` 有；其他表无 |

### §22–§24 同步 / 冲突 / 离线

| 要求 | 状态 | 说明 |
|---|---|---|
| 服务端主数据 + 客户端缓存 | 🟡 | 客户端是主数据；服务端不存在 |
| version/created/updated/deleted | 🟡 | 仅 attachments 有 revision/deletedAt；学生等只有 updatedAt |
| sync_changes + cursor | 🟡 | 有 JSONL 事件 + 每设备 checkpoint，无游标 API |
| 409 + base_version + 三种选择 | 🟡 | 有冲突检测与选择记录，无 HTTP 协议、无应用结果 |
| Room 缓存 + WorkManager | 🟡 | Room 有；WorkManager 无 |
| 离线草稿/自动补同步 | ❌ | 无 outbox |

### §25–§28 AI Provider / MCP / 权限 / 审计

| 要求 | 状态 | 说明 |
|---|---|---|
| `AiProvider` 四方法 | ❌ | 无 |
| Provider 可替换 | 🟡 | 仅 NL 命令层可替换 |
| MCP Server + 11 个工具 | ❌ | 无 |
| 危险操作不暴露 | 🟡 | 现有 Registry 本来就没有硬删除 |
| OWNER/TEACHER/AI_READ/AI_WRITE_DRAFT/ADMIN | ❌ | 只有单用户 + OWNER_TRUSTED/STANDARD 两种访问模式 |
| audit_logs（字段级 before/after/provider/model/request_id） | 🟡 | 只有动作级元数据，100 条 SharedPreferences |
| AI 写操作审计 | 🟡 | 已记录动作；缺 provider/model/字段差异 |

### §29 数据隐私

| 要求 | 状态 | 说明 |
|---|---|---|
| 私有存储 / 不生成公开 URL | 🟡 | 本地文件私有；无云存储 |
| AI 上传去标识化 | ❌ | 无上传；现有 Provider 会把上下文 JSON 发给云端，含学生姓名 |
| Token 不硬编码 | ✅ | 现有 API Key/OAuth 走加密存储，无硬编码 |
| APK 不含数据库密码 | ✅ | 没有数据库密码 |
| 日志不打印完整 Token | ✅ | 未发现；仍需在新增层固化 |
| 生产库禁止匿名公网 | ❌ | 无生产库 |

### §30–§32 迁移 / Flyway / 备份

| 要求 | 状态 | 说明 |
|---|---|---|
| `.ttseed.json` 迁移工具 | 🟡 | 有 `HistoricalSeedImporter`，但格式不同；无 `.ttseed` 解析、无报告、无批次 |
| 幂等 | ✅ | 现有导入幂等 |
| Flyway 全部 schema | ❌ | 无 |
| pg_dump 日备 + 7/4/6 保留 + 恢复演练 | ❌ | 无 |

### §33–§36 UI / 导出

| 要求 | 状态 | 说明 |
|---|---|---|
| 题库页面 | ❌ | 无 |
| 学情分析页面 | 🟡 | 有统计页与掌握度展示；无错题趋势/复测 |
| AI 精准练习页面 | ❌ | 无 |
| 学生详情新增页签 | 🟡 | 有概览/课堂/成绩等；无试卷/错题/知识掌握/专项练习 |
| 练习生成参数页 | ❌ | 无 |
| PDF / DOCX 导出 | ❌ | 只有 CSV/JSON/ZIP |

### §37–§38 来源与版权

| 要求 | 状态 | 说明 |
|---|---|---|
| 6 种 source_type | ❌ | 无题库 |
| copyright_status / usage_scope | ❌ | 无 |

### §39 测试要求

| 要求 | 状态 | 说明 |
|---|---|---|
| Unit / Integration / DB / API / Migration / Sync 测试 | 🟡 | 有 7 个 JVM 单测 + 6 个仪器测试；无后端测试、无迁移测试、无同步双设备测试 |
| 学生/课堂/题库/同步/试卷/练习验收 | 🟡 | 学生导入有历史证据；其余无 |

### §40–§43 性能、索引、搜索、语义检索

| 要求 | 状态 | 说明 |
|---|---|---|
| 1000 学生 / 10 万题 / 1 万卷 / 100 万答题 | ❌ | 未验证 |
| 搜索 < 500ms | 🟡 | Room LIKE 搜索；无题库搜索 |
| 合理索引 | 🟡 | 现有表有基础索引；无复合/全文/trigram |
| PostgreSQL 搜索 | ❌ | 无 |
| pgvector 预留 | ❌ | 无 |

### §44–§51 阶段与交付纪律

| 要求 | 状态 | 说明 |
|---|---|---|
| M01 审计（三份文档） | ✅ | 本文件 + `CURRENT_ARCHITECTURE.md` + `TEACHTRACK_MIGRATION_PLAN.md` |
| M02–M07 | ❌ | 未开始 |
| 每阶段代码/README/Migration/测试/结果/已知问题/回滚 | ❌ | 待执行 |
| 禁止重写 / 禁止删数据 / 禁止假数据 | ✅ | 本轮只写文档，未改业务代码 |

---

## 2. 必须解决的架构冲突

### C-01 UUID 主键 vs 历史 TEXT ID

- 冲突：Prompt §6 要求 UUID、不得用姓名做主键；现状是 TEXT，且历史 Seed 使用 `seed-student-001`。
- 最低风险方案：服务端 `id uuid` + `legacy_id text unique`；由 `legacy_id` 确定性生成 `UUIDv5`，新记录用 `UUIDv4`。Android 继续使用本地 TEXT ID，API 同时返回两套 ID。
- 禁止：重新生成一套与历史记录脱钩的学生 ID。

### C-02 服务端主数据 vs 本地优先

- 冲突：Prompt §22 要求“服务端主数据 + 客户端缓存”；现状是 Room 为唯一权威。
- 最低风险方案：分两阶段。M04 前保持 Room 权威、服务端镜像；M04 起以服务端 `version` 为冲突裁决依据，但离线仍可完整读写。切换必须可回退（关闭远程数据源即可回到纯本地）。
- 禁止：一次性把 Room 降级为只读缓存。

### C-03 现有 Workspace 同步 vs 新的在线同步

- 冲突：两套同步语义并存容易双写。
- 方案：Workspace 只保留“手动导出/导入/灾备”；在线同步走 `sync_changes` 游标。二者共用 `baseRevision/revision` 概念，但**不再让 Workspace 事件参与在线冲突裁决**。

### C-04 单用户 Agent vs 多角色 Token

- 冲突：现有 Agent 只有 OWNER_TRUSTED/STANDARD 两档。
- 方案：新增 `users / roles / api_tokens`，MCP Token 只授予 `AI_READ` + `AI_WRITE_DRAFT`；Android 端仍是 OWNER。两套权限在服务端统一校验，Android 本地权限不放松。

### C-05 现有 Seed 与目标快照不一致

- 冲突：Prompt 描述的 14 学生 / 91 课程快照不在仓库；现有 seed 29/30；设备记录 49。
- 方案：M02 前必须确认唯一数据源。优先级：用户提供的 `.ttseed.json` > 设备导出备份 > 现有 seed。迁移报告必须写明来源与条数，禁止用 29 人 seed 冒充 14 人验收。

---

## 3. 风险清单

| ID | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R-01 | 真实快照缺失 | M02 验收无法做 | 先拿到 `.ttseed.json` 或设备备份；M02 前设门禁 |
| R-02 | 双 ID 映射错误 | 学生记录错配/重复 | UUIDv5 确定性 + legacy_id 唯一约束 + 迁移前后对账查询 |
| R-03 | 同步双写 | 数据漂移 | 单一写入口；Workspace 退出在线路径；冲突必须人工确认 |
| R-04 | Android 端新增依赖拖慢构建 | 现有 52 测试回归 | 新依赖最小化（OkHttp 复用、WorkManager 单加）；每阶段跑全量测试 |
| R-05 | Agent 动作继续堆进单文件 | 可维护性下降 | 新动作按 domain 拆 Registry（question/practice/exam），共用 `CopilotService` |
| R-06 | AI 生成题质量 | 教学事故 | 强制 `NEEDS_TEACHER_REVIEW`；禁止自动 APPROVED；确定性校验 + 去重 |
| R-07 | 学生隐私上传 | 合规/信任 | 去标识化、字段白名单、私有存储、审计、可关闭云端 |
| R-08 | 备份不可恢复 | 数据丢失 | 每阶段恢复演练；备份与恢复脚本同仓库；演练记录入档 |
| R-09 | 现有文档过期误导后续 AI | 错误实现 | 本文件与 `CURRENT_ARCHITECTURE.md` 作为唯一现状入口；修正 `docs/database.md` |
| R-10 | Release debug 签名 | 无法正式分发 | M07 前接入正式签名与密钥管理，不在本轮处理 |

---

## 4. M01 结论

1. 现有系统不需要重写；需要的是**在 Android 旁边新增一个云端子系统**，并在 Android 内新增题库/学情/练习域。
2. 最高优先的复用点是 Copilot Action Registry 与 Room Repository；最高优先的新建点是 PostgreSQL schema、迁移工具、对象存储抽象。
3. 在拿到真实 `.ttseed.json` 之前，M02 的“14 学生 / 91 课程”验收不可执行；其余 M02 基础设施工作可以先做。
