# TeachTrack 现状架构审计（M01）

- 审计对象：`D:\SoftWare\TeachTrack`，git HEAD `7f1a832`，工作区干净
- 审计日期：2026-11-05
- 审计方式：直接读磁盘代码 + Room schema + 构建脚本 + 实际执行单元测试；不引用记忆印象
- 结论适用范围：本文件只描述**已经存在并可从代码验证**的事实；未实现的能力写进 `GAP_ANALYSIS.md`

---

## 1. 一句话结论

TeachTrack 是一个**成熟、可运行、本地优先的 Android 单机教师工作台**：Compose UI → ViewModel → Repository → Room（v7，21 张表），外加一套已经设备验证的 Copilot 命令层（68 个白名单动作）和文件型 Portable Workspace 同步底座。

它**没有**后端服务、PostgreSQL、对象存储、题库、试卷/OCR、MCP、账号与角色体系。目标架构中的云端部分是从零新增的子系统，不是对现有代码的替换。

---

## 2. 仓库结构与模块边界

```text
TeachTrack/
├── app/                       Android 应用（唯一可运行产物）
│   ├── src/main/java/com/leslie/teachtrack/   自有代码，34 个 .kt
│   └── src/main/java/com/openminis/           vendored OpenMinis Agent Runtime（约 420 个 .kt）
├── shared/bashism/            与 OpenMinis 共享的 bashism 规则（构建时复制进 assets）
├── deps-openminis/            rclone.aar 等 vendored 依赖源码/脚本
├── AI_SPEC/                   1.x / 2.x / 3.x 规格与状态记录
├── LAEP/                      TaskGates 协议、STATE 交接、GATES
├── docs/                      架构、数据库、测试、集成与 Prompt 文档
├── schemas/                   空目录；真实 Room schema 在 app/schemas/
├── seed/teacher_seed_v1.json  历史教学 Seed（29 学生 / 30 课程）
├── artifacts/                 构建产物与真机证据
└── scripts/                   构建/设备脚本
```

**关键事实**：`app/src/main/java/com/openminis/**` 是 vendored Agent Runtime，不是本项目业务代码。改造 TeachTrack 业务时应只动 `com/leslie/teachtrack/**`，避免把上游运行时改成分叉。

### 自有代码分层

| 层 | 位置 | 职责 |
|---|---|---|
| UI | `MainActivity.kt`、`FeatureScreens.kt` | 5 个底部页签（首页/学生/计划/统计/设置）+ Agent 页签；自适应布局 |
| ViewModel | `MainActivity.kt` 内 | 组合 Repository Flow 与 UI 状态 |
| Repository | `data/TeachTrackRepository.kt` | Room 读写、备份导入导出、CSV、事件记录 |
| DAO | `core/database/TeachTrackDao.kt` | 21 张表的查询/upsert |
| Domain | `core/model/Entities.kt` | Room 实体、`StudentStatus`、`MasteryLevel` |
| Workspace | `data/workspace/**` | SAF 目录、JSONL 导入导出、事件日志、冲突文件 |
| Copilot | `copilot/**` | 命令契约、Provider、白名单动作、审计、撤销 |
| Agent 桥 | `agent/**` | OpenMinis PRoot 沙箱内的 `teachtrack` CLI → 本地 Action Registry |
| 存储 | Room + SAF + `backups/` | 无对象存储；附件以 URI/相对路径保存 |

---

## 3. 构建与运行基线

| 项 | 实测值 |
|---|---|
| 构建系统 | Gradle Wrapper 8.11.1，AGP + Kotlin + KSP |
| JDK | 项目 `jvmToolchain(17)`；M01 已安装 Temurin 17（`D:\SoftWare\jdk-17`），另保留 Temurin 21（`D:\SoftWare\jdk-21`）供后端使用 |
| Android SDK | `local.properties` → `D:\SoftWare\android-sdk`，platforms 34/35/36 已安装 |
| applicationId | `com.leslie.teachtrack` |
| versionName / Code | `3.0.0-alpha.3` / `300003` |
| minSdk / target / compile | 26 / 35 / 36 |
| ABI | 仅 `arm64-v8a` |
| Room | 2.6.1，`version = 7`，`exportSchema = true`，schema 输出到 `app/schemas/` |
| 序列化 | `org.json`（业务）+ kotlinx.serialization（OpenMinis） |
| 网络 | `HttpURLConnection`（Provider）、OkHttp/SSE（OpenMinis）；**无 Retrofit、无 WorkManager** |
| 原生 | CMake + cppjieba + PRoot 资产 |
| 单元测试 | `app/src/test` 7 个文件；`app/src/androidTest` 6 个文件 |
| 既有设备证据 | `artifacts/oneplus-ace5-androidtest-20260916.txt`：`OK (52 tests)`（历史证据，设备现已不可用） |

### 构建约束

- Release 使用 debug 签名（`signingConfig = debug`），仅适合内部交付。
- `packaging.jniLibs.useLegacyPackaging = true`，PRoot 可执行文件依赖该设置，不能随手改。
- `noCompress` 包含 `tar.gz` / `proot-aarch64`，改动打包配置会破坏 Agent 沙箱。
- 依赖仓库禁用 `FAIL_ON_PROJECT_REPOS`，新增 Maven 仓库必须写进 `settings.gradle.kts`。

---

## 4. 数据层现状

### 4.1 Room 表（v7，21 张）

| 表 | 主键 | 关键字段 | 说明 |
|---|---|---|---|
| `students` | `id TEXT` | name, displayName, gender, grade, currentGrade, gradeAtRecord, subject, status, nameCompleteness | 学生主档；无 alias 表 |
| `course_sessions` | `id TEXT` | studentId, date, startTime, endTime, subject, title, content, performance, problems, homeworkSummary, nextLessonPlan | 课堂记录 |
| `knowledge_points` | `id TEXT` | parentId, name, subject, grade, orderIndex, description | 两级/多级树，但无教材版本、章节、code |
| `student_knowledge_progress` | `(studentId, knowledgePointId)` | level 0..4, lastUpdated, notes | 掌握度只有 5 档 |
| `student_knowledge_events` | `id TEXT` | studentId, knowledgePointId, level, occurredAt | 变更历史 |
| `exams` | `id TEXT` | name, date, subject, type, notes | 考试元数据 |
| `student_scores` | `id TEXT` | studentId, examId, score, fullScore, ranks, scoreLabel, scoreAccuracy | 成绩 |
| `score_items` | `id TEXT` | scoreId, category, lostScore, knowledgePointId | 失分项 |
| `homework` | `id TEXT` | studentId, courseId, name, assignedDate, status | 作业 |
| `communications` | `id TEXT` | studentId, date, contact, method, summary, parentFeedback, followUp | 家校沟通 |
| `lesson_plans` | `id TEXT` | studentId, plannedDate, chapter, knowledgePoints, focus, review, newContent | 课程规划 |
| `tasks` | `id TEXT` | title, studentId, type, dueDate, priority, status | 待办 |
| `parent_contacts` | `id TEXT` | studentId, name, phone, relation, isPrimary | 家长联系方式（敏感） |
| `problem_records` | `id TEXT` | studentId, knowledgePointId, courseId, examId, title, description, date, status | 现有“错题/问题”记录，粒度是文字，不是题目 |
| `templates` | `id TEXT` | name, type, content | 反馈模板 |
| `tags` / `student_tags` | id / 复合 | name, color / studentId+tagId | 标签 |
| `attachments` | `id TEXT` | uri, relativePath, size, sha256, revision, deletedAt | 本地附件；已具备哈希/修订/墓碑字段 |
| `historical_learning_records` | `id TEXT` | studentId, recordDate, dateAccuracy, subject, title, content, source | 历史教学资料 |
| `student_progress` | `id TEXT` | studentId, date, category, title, detail | 历史进度 |
| `unresolved_historical_records` | `id TEXT` | recordDate, title, content | 无法归属到学生的历史记录 |

Room 迁移：`MIGRATION_1_2` … `MIGRATION_6_7` 全部为显式 SQL，没有 `fallbackToDestructiveMigration`。schema 文件 1/2/3/6/7 齐备。

### 4.2 ID 策略（必须保留）

- 所有主键是 **TEXT**，不是 UUID 类型。
- 历史 Seed 使用确定性 ID：`seed-student-001`、`seed-course-<seedStudentId>-<n>`、`seed-kp-<nameUUID>`。
- 运行时新建记录使用 `UUID.randomUUID().toString()`。
- 同一个历史文件重复导入是幂等的：`HistoricalSeedImporter` 用确定性 ID + `upsert`，并在导入前按姓名/别名匹配已有学生，复用已有 `id`。

**结论**：目标系统的 UUID 要求不能通过“重新生成学生 ID”实现。必须采用“服务端 UUID + `legacy_id` 保留”的双 ID 模型，详见 `TEACHTRACK_DATABASE_SCHEMA.md` §3。

### 4.3 历史数据现状与差异（重要）

| 数据源 | 实测 | 与 Prompt 的差异 |
|---|---|---|
| `seed/teacher_seed_v1.json` | 29 学生 / 30 课程 / 1 条未归属记录；status 全为 `UNKNOWN`；字段只有 id/fullName/displayName/gender/historicalGrade/subject/status/nameCompleteness/scores/knowledge/strengths/weaknesses/focus/courses | Prompt 描述的是 14 学生 / 91 课程的 `.ttseed.json`，字段含 alias、academicProfile、learningCharacteristics、riskFlags、coursePlans、communications、specialRules、courseSessionIds 等 |
| `TeachTrack_当前学生完整数据_含上课记录_2026-11-01.ttseed.json` | **项目目录、`D:\SoftWare`、`D:\Leslie` 常见位置与 git 历史均未找到**（`find` + `git log --all --name-only` 无匹配；未全盘扫描其他磁盘） | 这是 M02 数据迁移的**输入阻塞项**，不能假装已存在，也不能用 seed 冒充 |
| 设备现状（LAEP `CURRENT_STATE.md` 记录） | 49 学生（14 ACTIVE / 35 ARCHIVED） | 与仓库 seed 又是另一组数据；说明真实主数据在设备 Room 中 |

**审计结论**：Prompt §2 的数据快照与仓库现状不一致。M01 可以继续；M02 的“导入 14 名学生 / 91 条 CourseSession”验收必须拿到真实 `.ttseed.json` 或从设备导出备份后才能执行，禁止用现有 29 人 seed 伪造验收。

### 4.4 文件存储

- 附件：`attachments.uri` + `relativePath` + `sha256` + `revision` + `deletedAt`；文件本体在用户选择的 SAF 工作区或应用目录。
- 备份：`TeachTrack Backup` JSON（全表导出）与 `.ttbackup`（ZIP + JSONL）。
- **没有**对象存储、没有签名 URL、没有私有 Bucket 概念。

---

## 5. AI 层现状

### 5.1 两条独立的 AI 通路

```text
A. 应用内 Copilot
   语音/文字 → FakeCopilotProvider 或云端 Provider
   → CopilotCommand（teachtrack.command.v1）
   → CopilotCommandValidator（白名单 + 参数长度）
   → 确认门 → LocalCopilotActionRegistry（69 动作）
   → Repository/Room → CopilotAuditStore

B. Agent（OpenMinis）
   PRoot 沙箱内 shell → `teachtrack rpc --json ...`
   → TeachTrackOffloadHandler（路径包含校验、访问模式）
   → CopilotService → 同一个 Action Registry
   → 数据动作写 Repository/Room；UI 动作写共享界面状态
```

两条通路共用同一个 Action Registry，这是现有架构最重要的复用点。`exam.analysis.text.fill` 只填题库 → 试卷分析的共享文本并打开页面，不触发试卷导入、分析或审核入库。

### 5.2 已实现的安全边界

| 边界 | 实现 |
|---|---|
| 模型不能直接访问 Room | 模型只能返回命令 JSON；执行只发生在 `LocalCopilotActionRegistry` |
| 动作白名单 | `allowedActions` 69 项；未知动作直接拒绝 |
| 写入确认 | `confirmationRequired` + `preview/execute` 两段式 |
| 敏感数据 | 家长联系方式等写入需要确认 |
| 审计 | `CopilotAuditStore`：动作、时间、状态、undoToken、sessionId；**只存元数据，不存原始参数**；最多 100 条 |
| 撤销 | `CopilotUndoTokens` 只支持任务状态回滚与学生软归档；创建类动作明确不支持硬删除 |
| API Key | AES-GCM + AndroidKeyStore 加密后存 SharedPreferences，不进 Room/日志/备份 |
| Agent 沙箱 | `resolveRootfsFile` 做 canonical path containment，禁止 `..` 越界 |
| 访问模式 | OWNER_TRUSTED 自动确认写；STANDARD 对非只读动作返回 `CONFIRMATION_REQUIRED` |

### 5.3 Provider 现状

| Provider | 状态 |
|---|---|
| Fake / Offline | 规则匹配，完全离线可用 |
| OpenAI-compatible | 已实现（`HttpURLConnection`，API Key） |
| Kimi Code OAuth | 已实现，固定 `https://api.kimi.com/coding/v1/chat/completions` |
| 其他厂商 | 未实现 |

**缺口**：Provider 只做“自然语言 → 命令”，没有 `analyzeExam` / `generateQuestion` / `reviewQuestion` 这类 AI 服务能力；也没有统一的 `AiProvider` 接口供后端复用。

### 5.4 Agent 运行时

- OpenMinis 作为 vendored 运行时嵌入，提供 PRoot/Alpine 沙箱、MCP、Provider、语音、备份等能力（约 420 个文件）。
- TeachTrack 只注册了一个 Native Offload handler：`teachtrack`。
- Agent 能看到的动作集 = `capabilities` 返回的 69 项。
- **该桥只在本机进程内可用**，不是给 ChatGPT/Kimi/Claude 的外部 MCP 服务。

---

## 6. 同步与多设备现状

### 6.1 已有能力

| 能力 | 位置 | 说明 |
|---|---|---|
| 工作区清单 | `workspace.json` | `format=TeachTrackWorkspace`，含 workspaceId/schemaVersion |
| 数据交换 | `data/*.jsonl` | 22 类实体，一行一条，保留原始 ID |
| 设备身份 | `sync/devices/<deviceId>.json` | 本地生成 UUID |
| 事件日志 | `sync/events/YYYY/MM/<deviceId>.jsonl` | 每次写操作追加 `baseRevision/revision` |
| 检查点 | `sync/checkpoints/<deviceId>.json` | 每设备最后处理事件 |
| 墓碑 | `sync/tombstones/<type>-<id>.json` | 删除标记 |
| 冲突检测 | `SyncEventScanner` | `localRevision != incomingBaseRevision` 时写冲突文件 |
| 冲突解决 | `ConflictResolutionStore` | 只记录选择：`VERSION_A` / `VERSION_B` / `MANUAL_MERGE`，不自动合并 |
| 事件写入 | `WorkspaceSyncCoordinator` | best-effort；工作区不可用时静默跳过，不阻塞本地写 |

### 6.2 明确没有的东西

- 没有服务端主数据，没有 `GET /sync/changes?cursor=`。
- 没有 HTTP 409 + base_version 的在线冲突协议。
- 没有 WorkManager、没有离线写队列、没有自动后台同步。
- 事件只覆盖部分写路径（Repository 里逐方法手工 `sync?.record(...)`），不是全表自动；直接 `dao.upsert*` 的路径不会产生事件。
- 冲突解决只落“选择记录”，**没有把选择应用回实体**。

### 6.3 结论

现有 Workspace 是“可移植数据目录 + 手工/半自动同步底座”，不是多设备在线同步系统。目标架构应保留它作为**离线导出/灾备格式**，另建服务端增量同步；不要删掉它，也不要把它当在线同步用。

---

## 7. 网络与后端现状

- 无 `backend/`、无 `pom.xml`、无 Spring、无 PostgreSQL、无 Flyway、无 Docker Compose。
- Android 不直连数据库；现有唯一网络用途是 AI Provider。
- `INTERNET` 权限已声明；OkHttp 已作为 OpenMinis 依赖存在，可直接复用，不必新增网络库。
- 无 Retrofit；新增 API 客户端可选 OkHttp + kotlinx.serialization，避免为“架构好看”引入整套新栈。

---

## 8. 测试与质量基线

| 项 | 现状 |
|---|---|
| JVM 单测 | 7 个文件：`ScoreRateTest`、`TemplateRendererTest`、`copilot/*` |
| 仪器测试 | 6 个文件，覆盖数据库、Agent 动作、PRoot 沙箱 e2e |
| 历史真机结果 | OnePlus Ace 5 `OK (52 tests)`（2026-09-16） |
| 现有构建门禁 | `./gradlew.bat assembleDebug`（README 记录） |
| 本次 M01 基线 | `:app:testDebugUnitTest` 实际执行结果见文末“实测记录” |
| 已知技术债 | `LAEP/STATE/TECH_DEBT.md`：TD-001 未接线的 typed command 契约；TD-002 同名 Composable 重载 |

### 已记录但未修的已知问题（审计中确认仍存在）

| 编号 | 问题 | 证据 |
|---|---|---|
| KI-01 | `docs/database.md` 严重过期：写的是 Room v1 / 11 张表，实际 v7 / 21 张表 | 文件内容 vs `TeachTrackDatabase.kt` |
| KI-02 | `problem_records` 是文字描述，不是可复用题目；无法支撑逐题错因、变式、复测 | `Entities.kt:49-50` |
| KI-03 | 掌握度只有 5 档枚举，没有分值、置信度、时间衰减、重复错误惩罚 | `MasteryLevel`、`student_knowledge_progress` |
| KI-04 | 事件日志依赖每个 Repository 方法手工调用，漏写即漏同步 | `TeachTrackRepository.kt` 各 `sync?.record` |
| KI-05 | 冲突解决只记录选择，不应用结果 | `ConflictResolutionStore.recordResolution` |
| KI-06 | 审计只存动作元数据，无字段级 before/after，无设备/模型信息 | `CopilotAuditStore.kt:21` |
| KI-07 | `LocalCopilotActionRegistry` 单文件 61 KB，继续加动作会变成巨型类 | 文件大小 |
| KI-08 | Release 用 debug 签名，不能作为正式分发 | `app/build.gradle.kts` |

---

## 9. 现有实现中应复用的资产

| 资产 | 复用方式 |
|---|---|
| `CopilotCommand` / 校验器 / 确认门 / 审计 | 新的题库、练习、试卷动作继续注册到同一 Registry，不另建第二套命令系统 |
| `StudentEntityResolver` / `ItemStudentResolution` | 新动作按姓名/ID 解析学生时复用 |
| `WorkspaceSyncCoordinator` + 事件格式 | 保留为离线导出/灾备；服务端同步沿用 `baseRevision/revision` 语义 |
| `attachments` 的 sha256/revision/tombstone | 对象存储元数据沿用同一字段语义 |
| `HistoricalSeedImporter` 的确定性 ID + upsert | 迁移工具复用其幂等思路，扩展 `.ttseed.json` 字段 |
| AndroidKeyStore 密钥存储 | 云端同步 Token / MCP Token 继续用同一套加密存储 |
| `TeachTrackOffloadHandler` 的严格 JSON 信封 | 未来 MCP 工具返回值沿用 `ok/operation/result/error.code` 形状 |
| `StudentDataOrganizer` | 学情聚合可先作为本地算法，后端上线后再迁移 |

---

## 10. 本次审计的边界与未知项

| 项 | 状态 |
|---|---|
| 真实设备 Room 数据 | 未读取。设备不保证连接，且读取需 debug 包 + `run-as`；不以设备数据替代仓库事实 |
| `.ttseed.json` | 磁盘/git 均无此文件，无法验证其真实字段与条数 |
| Kimi OAuth 生产可用性 | 未在本轮重新验证；不属于 M01 验收 |
| OpenMinis 上游升级策略 | 本轮不改 vendored 运行时 |
| 性能基线 | 未做 10 万题规模压测；现有数据量远低于目标 |

---

## 11. 实测记录（M01 基线）

```text
环境：JAVA_HOME=D:\SoftWare\jdk-17（Temurin 17.0.20.1）
命令 1：./gradlew.bat :app:testDebugUnitTest --console=plain
结果 1：BUILD SUCCESSFUL in 3m 39s；35 tests / 0 failures / 0 errors / 0 skipped
命令 2：./gradlew.bat :app:assembleDebug --console=plain
结果 2：BUILD SUCCESSFUL in 42s；app-debug.apk 59 MB
说明：首次执行时本机只有 JDK 21，Gradle 因 jvmToolchain(17) 无法匹配而失败；
      M01 期间安装 JDK 17 后复跑通过。业务代码与构建脚本未改。
```

> 本文件在测试完成前写定架构事实；测试结果以上述实测为准，不用推断代替。
