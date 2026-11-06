# TeachTrack 实施路线图（M01–M07）

- 起点：`3.0.0-alpha.3`（versionCode 300003），现有 68 个 Copilot 动作与 Room v7 全部保留
- 目标：`4.0.0`，在现有 App 上增加云端题库、学情诊断、AI 精准练习、跨设备同步、MCP
- 节奏：每个里程碑独立可交付、可回滚；未通过验收不进入下一阶段

---

## 0. 里程碑总览

| 里程碑 | 主题 | 主要产物 | 依赖 | 状态 |
|---|---|---|---|---|
| M01 | 架构审计 | 现状/差距/迁移/架构/DB/路线图文档 | 无 | ✅ 本轮完成 |
| M02 | 云端数据底座 | backend 骨架、Flyway、PostgreSQL、对象存储、迁移工具、备份演练 | M01 + 真实数据源 | 待开始 |
| M03 | 题库中心 | 题目 CRUD、知识点、附件、审核、搜索、Android 题库页 | M02 | 待开始 |
| M04 | 跨设备同步 | sync_changes、cursor、409 冲突、WorkManager、双设备验证 | M03 | 待开始 |
| M05 | AI 试卷分析 | 上传、OCR、切题、逐题分析、历史关联、教师校对 | M04 | 待开始 |
| M06 | AI 精准练习 | 选题、变式、原创、质量门、PDF/DOCX、复测 | M05 | 待开始 |
| M07 | MCP / 外部 AI | MCP Server、Token、scope、限流、审计 | M06 | 待开始 |

---

## M01 — 架构审计（已完成）

### 交付物

```text
docs/ai-practice/CURRENT_ARCHITECTURE.md
docs/ai-practice/GAP_ANALYSIS.md
docs/ai-practice/TEACHTRACK_MIGRATION_PLAN.md
docs/ai-practice/MIGRATION_PLAN.md
docs/ai-practice/TEACHTRACK_AI_PRACTICE_ARCHITECTURE.md
docs/ai-practice/TEACHTRACK_DATABASE_SCHEMA.md
docs/ai-practice/TEACHTRACK_IMPLEMENTATION_ROADMAP.md
```

### 验收

- [x] 逐文件审计现有代码，不靠印象
- [x] 明确学生数据模型、ID 策略、Room v7 结构
- [x] 明确 Copilot / Agent / Provider / Workspace 现状
- [x] 明确“有/部分/没有/冲突/风险”
- [x] 给出可执行的迁移与 schema 设计
- [x] 不改业务代码、不动数据库
- [x] 基线构建/测试实跑（结果见交付汇报）

### 已知阻塞

- 真实 `.ttseed.json` 缺失 → 只影响 M02 的数据验收，不影响基础设施建设。

---

## M02 — 云端数据底座

### 目标

建立一个能安全存学生、课堂、题库 schema、能备份能恢复的后端，但**不要求 Android 立刻接入**。

### 任务

| # | 任务 | 落点 |
|---|---|---|
| 2.1 | 新建独立后端构建 | `backend/build.gradle.kts`、`settings.gradle.kts`（不加入 Android `include`） |
| 2.2 | Spring Boot 骨架 + 分模块包 | `backend/src/main/java/com/leslie/teachtrack/**` |
| 2.3 | Flyway V001–V010 | `backend/src/main/resources/db/migration/` |
| 2.4 | 通用错误信封 / 分页 / 审计切面 | `common/` |
| 2.5 | 身份与 Token（最小版） | `auth/` |
| 2.6 | 学生/课堂/知识点/成绩只读 + 写入 API | `student/`、`course/`、`knowledge/` |
| 2.7 | `ObjectStorageProvider` + MinIO/S3 实现 | `storage/` |
| 2.8 | 迁移工具（seed v1 / Backup JSON / `.ttseed` 适配） | `migration/` |
| 2.9 | 备份与恢复脚本 + 演练 | `backend/ops/backup.sh`、`restore-test.sh` |
| 2.10 | docker compose（postgres + minio + backend） | `backend/docker-compose.yml` |
| 2.11 | 后端测试：Repository/迁移/API/备份 | `backend/src/test/**` |

### 验收（硬门禁）

1. `docker compose up` 后 `/actuator/health` 为 `UP`。
2. `flywayMigrate` 从空库一次成功；重复执行无变更。
3. 导入 `teacher_seed_v1.json`：29 学生 / 30 课程；重复导入条数不变。
4. 导入真实 `.ttseed.json` 或设备备份：条数与源一致（**需要真实文件**）。
5. `pg_dump` → 删除测试库 → `pg_restore` → 对账一致。
6. 对象存储：上传 → 私有读 → 10 分钟签名 URL 可下载；公开 URL 不可访问。
7. 所有写操作产生 `audit_logs`。
8. 关闭后端，Android 现有功能不受影响（本阶段 Android 未接入）。

### 交付物

```text
backend/ 代码
backend/README.md（启动、迁移、备份、恢复）
Flyway 脚本
迁移报告样例
测试结果
已知问题
回滚方案（drop schema / restore dump）
```

### 风险

- 真实数据源缺失：先把工具做到能处理三种格式，验收数据后补。
- Supabase 网络：开发用本地 Docker，生产再切换托管库。

---

## M03 — 题库中心

### 目标

题库可用：录题、改题、审核、搜索、插图；Android 有真实页面。

### 任务

| # | 任务 |
|---|---|
| 3.1 | 题目 CRUD + 软删除 + 版本快照 |
| 3.2 | 选项/答案/解析/评分点 |
| 3.3 | 知识点多对多 + 主/次/关联 |
| 3.4 | 题目图片上传（预签名直传 + sha256 去重） |
| 3.5 | 审核状态机（DRAFT → … → APPROVED） |
| 3.6 | 搜索：学科/年级/知识点/题型/难度/来源/审核状态 |
| 3.7 | 去重：exact + normalized hash + trigram 候选 |
| 3.8 | Android：题库列表/详情/筛选/审核（只读 + 轻编辑） |
| 3.9 | Copilot 新动作：`question.search/create/update/review` |
| 3.10 | 测试：CRUD、版本、软删、搜索、去重 |

### 验收

1. 新建题 → 出现在列表 → 修改 → 旧版本可查 → 软删除后默认不可见。
2. 同一题干重复导入被 exact hash 拦住，提示已有题目。
3. 搜索 6 个维度组合正确；1 万题种子数据下 P95 < 500ms（本地）。
4. AI 生成的题默认 `NEEDS_TEACHER_REVIEW`，不可直接 `APPROVED`。
5. Android 关闭远程时题库入口提示“未连接”，现有功能不崩。

---

## M04 — 跨设备同步

### 目标

手机/平板看到同一份学生、题库、试卷、练习、错题、掌握度；离线可写，回连自动同步，冲突不丢数据。

### 任务

| # | 任务 |
|---|---|
| 4.1 | `sync_changes` 写入与实体写同事务 |
| 4.2 | `GET /sync/changes?cursor=`、`POST /sync/push` |
| 4.3 | 409 冲突 + 三种选择 API |
| 4.4 | Android `sync_outbox` / `sync_state` / `sync_conflict` 表（Room v8） |
| 4.5 | WorkManager 后台同步 + 退避 + 前台大文件 |
| 4.6 | 冲突 UI + 解决动作 |
| 4.7 | 远程数据源开关（`remote.enabled`） |
| 4.8 | 双设备模拟测试（两台模拟器/一台真机 + 一台模拟器） |
| 4.9 | 同步审计与指标 |

### 验收

1. 设备 A 新建学生 → 设备 B 拉取可见。
2. 设备 B 离线改学生 → 回连推送成功。
3. A/B 同时改同一学生 → 一端 409，选择 KEEP_LOCAL / KEEP_REMOTE / MANUAL_MERGE 后结果符合预期。
4. 断网 100 次写入 → 回连后全部按序同步，无丢失。
5. 关闭 `remote.syncEnabled` → 回到纯本地，无崩溃、无数据删除。
6. 两台设备数据对账一致。

---

## M05 — AI 试卷分析

### 目标

上传一张试卷，得到逐题分析、错因、知识点、历史课堂关联，并可教师校对后入库。

### 任务

| # | 任务 |
|---|---|
| 5.1 | 上传 URL + 直传对象存储 |
| 5.2 | 异步解析任务表 + worker |
| 5.3 | OCR Provider 抽象 + 至少一个实现 |
| 5.4 | 题目切分 / 题号 / 分值 / 学生答案 / 批改痕迹 |
| 5.5 | 知识点/题型/难度候选 |
| 5.6 | `exam_questions` / `student_answers` 草稿 |
| 5.7 | 逐题分析 + 汇总报告 |
| 5.8 | 历史课堂关联查询 |
| 5.9 | Android 上传、进度、逐题校对、确认入库 |
| 5.10 | 低置信度标黄与人工确认门 |
| 5.11 | 测试：上传/解析/修订/确认/失败重试 |

### 验收

1. 真实试卷图片 → 解析出题号与分值；低置信度题标黄。
2. 教师可逐题改题干/答案/得分；确认后写入题库与错题。
3. 未确认的解析不会出现在正式题库。
4. 逐题分析包含 Prompt §12 的全部字段。
5. 对蒋欣阳/王晨然等真实学生，能显示“该知识点曾于某日讲解”（有数据时）。
6. 解析失败有明确错误码与重试入口。

---

## M06 — AI 精准练习

### 目标

按学生薄弱点生成可审核、可导出、可复测的练习。

### 任务

| # | 任务 |
|---|---|
| 6.1 | 练习生成策略（题库 → 错题变式 → 相似题 → AI 原创） |
| 6.2 | `AiProvider.generateQuestions` + Prompt 版本化 |
| 6.3 | 10 项质量门 + 去重 |
| 6.4 | 练习集/题目/审核状态 |
| 6.5 | 学生版/教师版导出 PDF |
| 6.6 | DOCX 导出 |
| 6.7 | 学生作答录入 / 复测 |
| 6.8 | 掌握度重算（公式 v1） |
| 6.9 | Android 生成页、预览、审核、历史 |
| 6.10 | 测试：题库足够/不足/AI 生成/审核失败/重新生成 |

### 验收

1. 题库充足时优先用题库，不调用 AI 原创。
2. 题库不足时生成变式/原创，全部为草稿。
3. 质量门失败的题不能进入 `APPROVED`。
4. 同一请求重复生成不会产生重复题（去重）。
5. 导出 PDF/DOCX 含学生版与教师版；中文字体正常。
6. 复测后 `student_mastery` 更新，`confidence` 随题量上升。
7. 掌握度不是简单正确率（用构造数据验证难度/时间/重复错误的影响）。

---

## M07 — MCP / 外部 AI

### 目标

ChatGPT / Kimi / Claude 可通过 Token 访问 TeachTrack 的只读与草稿能力，全部可审计。

### 任务

| # | 任务 |
|---|---|
| 7.1 | MCP Server（JSON-RPC / Streamable HTTP） |
| 7.2 | 11 个工具实现 |
| 7.3 | Token 创建/吊销/scope/过期 |
| 7.4 | 限流与并发保护 |
| 7.5 | 字段级脱敏 |
| 7.6 | 全量审计（provider/model/requestId/token） |
| 7.7 | 危险工具不注册的测试 |
| 7.8 | 外部 AI 端到端演练（至少一个客户端） |
| 7.9 | 运维文档与 Token 轮换 |

### 验收

1. 有效 Token 可调用 11 个工具；无 Token/过期/越权返回 401/403。
2. `delete_*`、`bulk_delete`、`database_reset` 不在工具列表，调用返回 method not found。
3. AI 创建练习只产生 `DRAFT`。
4. 每次调用有审计记录，含 token_id、provider、model、request_id。
5. 返回学生信息默认不含电话/家长姓名。
6. 限流生效（超出返回 429）。

---

## 1. 通用交付规范（每个里程碑）

```text
代码
README（怎么跑、怎么测、怎么回滚）
Migration（如有）
测试（Unit / Integration / DB / API / Migration / Sync 按需）
测试结果（命令 + 输出摘要）
已知问题
回滚方案
```

### 每个里程碑的 Definition of Done

- [ ] 新功能真实可用（不是 UI 占位）
- [ ] 旧功能回归通过（至少现有 7 个 JVM 测试 + 6 个仪器测试文件不退化）
- [ ] 关闭新功能开关后回到现状
- [ ] 数据迁移可重复执行
- [ ] 有审计
- [ ] 有错误码与失败路径
- [ ] 文档更新（含 `CURRENT_ARCHITECTURE.md`）
- [ ] 临时文件清理

---

## 2. 版本与分支策略

| 阶段 | versionName | versionCode |
|---|---|---|
| 当前 | 3.0.0-alpha.3 | 300003 |
| M02 完成 | 4.0.0-alpha.1 | 400001 |
| M03 | 4.0.0-alpha.2 | 400002 |
| M04 | 4.0.0-beta.1 | 400100 |
| M05 | 4.0.0-beta.2 | 400200 |
| M06 | 4.0.0-rc.1 | 400300 |
| M07 | 4.0.0 | 400400 |

规则：每个可交付改动递增 versionCode；正式发版 commit + tag + push + 远端校验（按项目既有习惯）。

---

## 3. 依赖与前置条件

| 依赖 | 需要时间点 | 状态 |
|---|---|---|
| JDK 17（Android 构建） | 现在 | ✅ 已装 `D:\SoftWare\jdk-17` |
| JDK 21（后端） | M02 | ✅ 已有 `D:\SoftWare\jdk-21` |
| Docker Desktop / PostgreSQL 16 | M02 | 待确认 |
| 对象存储（MinIO 本地 / R2 生产） | M02 | 待配置 |
| 真实 `.ttseed.json` 或设备备份 | M02 数据验收 | ❌ 缺失 |
| AI Provider Key | M05 | 待配置（不得入库） |
| 正式签名密钥 | M07 前 | 待准备 |

---

## 4. 主要风险与应对

| 风险 | 触发点 | 应对 |
|---|---|---|
| 真实数据源缺失 | M02 | 先建工具；数据验收单独门禁 |
| Android 回归 | M03/M04 | 每个 PR 跑现有测试；远程开关默认关闭 |
| 同步冲突复杂 | M04 | 先做 409 + 人工选择，不做自动合并 |
| OCR 质量不达预期 | M05 | 人工校对为必经步骤；低置信度标黄 |
| AI 生成题错误 | M06 | 草稿 + 质量门 + 教师审核；禁止自动通过 |
| MCP 安全 | M07 | 最小工具集、Token scope、限流、审计、脱敏 |
| 单文件膨胀 | M03+ | 按域拆 Registry |
| 备份不可恢复 | 全程 | 每个里程碑一次恢复演练 |

---

## 5. 下一步（M02 启动清单）

1. 确认数据源：取得 `.ttseed.json` 或从设备导出 `TeachTrack Backup`。
2. 确认 Docker/PostgreSQL 是否可用；不可用则先装 `D:\SoftWare` 下。
3. 新建 `backend/` 独立 Gradle 构建，不进入 Android `settings.gradle.kts` 的 `include`。
4. 写 Flyway V001–V010；跑空库迁移。
5. 写迁移工具；先用 `teacher_seed_v1.json` 打通幂等。
6. 写备份/恢复脚本并完成一次演练。
7. 输出 M02 验收报告。

---

## 6. 明确不做（防止范围膨胀）

- 不在 M02 改 Android 业务代码。
- 不在 M03 做 AI 生成题。
- 不在 M04 做自动冲突合并。
- 不在 M05 做题库自动入库。
- 不在 M06 做正式发布签名。
- 不在第一版引入 Elasticsearch、Kafka、Redis、微服务拆分。
