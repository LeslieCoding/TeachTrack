# TeachTrack 4.0 · AI 精准练习系统架构

- 文档状态：设计基线（M01 交付），实施从 M02 开始
- 适用代码：`D:\SoftWare\TeachTrack`（Android 现有代码）+ 新增 `backend/`
- 设计原则：**保留现有 TeachTrack，不推倒重写；本地优先不变；所有云端能力可关闭**

---

## 1. 目标与边界

### 1.1 目标

在现有教师工作台上增加一条完整闭环：

```text
课堂记录 → 作业/试卷 → 逐题分析 → 错题与错因 → 薄弱知识点
→ 题库选题 / AI 变式 / AI 原创 → 教师审核 → 练习导出
→ 学生作答 → 复测 → 掌握度更新 → 下一节课建议
```

### 1.2 不变的边界

| 边界 | 规则 |
|---|---|
| 学生数据 | 不删除、不重建、不改历史 ID |
| 课堂/成绩/规划/沟通 | 现有表与页面继续工作，不做破坏性迁移 |
| 离线可用 | 无网络、无账号、无后端时，现有功能 100% 可用 |
| 模型权限 | 模型永远不能直接访问 Room/PostgreSQL，只能走命令或业务 API |
| 删除 | 默认软删除；不向 AI 暴露硬删除 |
| 文件 | 大文件只进对象存储，不进数据库 |
| AI 输出 | 默认“草稿”，必须教师审核后才能进入已审核题库 |

### 1.3 非目标（本阶段不做）

- 多教师 SaaS、学校租户、计费。
- 实时协同编辑。
- 自研 OCR 模型训练。
- 第一版就上 Elasticsearch / 向量数据库集群。
- 把 Android 改成纯瘦客户端。

---

## 2. 总体拓扑

```text
┌──────────────────────────── Android（现有 + 扩展） ────────────────────────────┐
│ Compose UI                                                                     │
│   ├── 现有 5 页签 + Agent                                                    │
│   └── 新增：题库 / 学情分析 / AI 精准练习 / 学生详情新页签                     │
│ ViewModel                                                                      │
│ Repository（本地权威 + 远程缓存）                                              │
│   ├── Room v7 → v8+（只增表/字段，不破坏现有）                                 │
│   ├── Workspace（SAF JSONL，保留为导出/灾备）                                  │
│   ├── RemoteDataSource（新，可关闭）                                           │
│   └── SyncOutbox + WorkManager（M04）                                          │
│ Copilot Action Registry（68 → 100+，按域拆分）                                 │
└───────────────────────────────┬───────────────────────────────────────────────┘
                                │ HTTPS + Bearer / OAuth
┌───────────────────────────────▼───────────────────────────────────────────────┐
│ backend/  Java 21 + Spring Boot 3.x                                            │
│  auth │ student │ course │ knowledge │ question │ exam │ mistake │ mastery     │
│  practice │ ai │ storage │ sync │ audit │ mcp                                  │
│                                                                               │
│  REST /api/v1/**        同步 /api/v1/sync/**        MCP /mcp/**               │
└──────────────┬───────────────────────────────┬────────────────────────────────┘
               │                               │
     ┌─────────▼─────────┐          ┌──────────▼──────────┐
     │ PostgreSQL 16     │          │ S3 兼容对象存储      │
     │ 业务 + 题库 + 同步 │          │ 试卷/图片/导出       │
     │ + 审计 + 游标      │          │ 私有 Bucket          │
     └───────────────────┘          └─────────────────────┘
               │
     ┌─────────▼─────────────────────────────────────────┐
     │ AI Service Layer                                  │
     │ AiProvider: OpenAI / Kimi / Claude / Local        │
     │ OcrProvider: 云端 OCR / 本地 OCR                  │
     └───────────────────────────────────────────────────┘
```

### 2.1 部署形态

| 环境 | 组成 |
|---|---|
| 开发 | `docker compose`：postgres:16 + minio + backend（热重载） |
| 生产（第一阶段） | 托管 PostgreSQL（Supabase 或等价）+ 单台容器/VM 跑 backend + R2/OSS/COS |
| 存储 | 生产用私有 Bucket；开发用 MinIO；接口统一 `ObjectStorageProvider` |
| 备份 | 独立 Bucket，不与业务 Bucket 同账号同区域单点 |

**可移植性约束**：不得依赖 Supabase 私有 API；只使用标准 PostgreSQL + S3 API。数据库连接串、存储端点、AI Key 全部走环境变量/密钥管理。

---

## 3. 后端模块划分

```text
backend/
├── build.gradle.kts             独立 Gradle 构建（不参与 Android 构建）
├── src/main/java/com/leslie/teachtrack/
│   ├── TeachTrackApplication.java
│   ├── common/                  错误信封、分页、时钟、ID、审计切面
│   ├── auth/                    用户、角色、API Token、JWT
│   ├── student/                 学生、别名、画像
│   ├── course/                  课堂记录
│   ├── knowledge/               学科、知识点树
│   ├── question/                题目、选项、答案、版本、附件、去重
│   ├── exam/                    试卷、题号、学生答案、逐题分析
│   ├── mistake/                 错题、错因
│   ├── mastery/                 掌握度计算
│   ├── practice/                练习生成、审核、提交、导出
│   ├── ai/                      AiProvider、OcrProvider、Prompt、质量门
│   ├── storage/                 ObjectStorageProvider、签名 URL
│   ├── sync/                    变更日志、游标、冲突
│   ├── audit/                   字段级审计
│   └── mcp/                     MCP 工具、Token scope、限流
└── src/main/resources/
    ├── application.yml
    └── db/migration/V001__…sql   Flyway
```

分层规则：`Controller → Service → Repository → Domain`；DTO 不直接暴露实体；禁止一个 Service 包办整个域；跨域只通过 Service 接口调用。

### 3.1 Android 侧新增模块（不新建平行 App）

```text
com.leslie.teachtrack/
├── question/       题库列表、详情、筛选、审核
├── exam/           上传、解析结果校对、逐题分析
├── mastery/        掌握度与趋势
├── practice/       生成参数、预览、审核、导出、历史
├── remote/         API 客户端、鉴权、DTO
├── sync/           outbox、cursor、冲突 UI
└── copilot/        现有 Registry 按域拆分（question/practice/exam Registry）
```

现有 `MainActivity` / `FeatureScreens` 只增加入口与导航，不重写。

---

## 4. 数据所有权与同步模型

### 4.1 所有权

| 数据 | 权威源 | 说明 |
|---|---|---|
| 学生/课堂/成绩/沟通/规划 | 服务端（M04 起）；M02–M03 为 Android→服务端单向镜像 | 离线仍可写，回连后同步 |
| 题库/试卷/练习/掌握度 | 服务端 | Android 只缓存 |
| 附件/试卷原图 | 对象存储 | 数据库只存元数据与 storage_key |
| 审计 | 服务端 + Android 本地副本 | 服务端为准 |

### 4.2 同步协议

```http
GET  /api/v1/sync/changes?cursor=12345&limit=500
→ { "changes": [ { "seq": 12346, "entityType": "question", "entityId": "...",
                   "operation": "UPSERT|DELETE", "version": 7, "payload": {...} } ],
    "nextCursor": 12845, "hasMore": false }

POST /api/v1/sync/push
{ "deviceId": "...", "baseCursor": 12845,
  "changes": [ { "entityType": "student", "entityId": "...", "baseVersion": 3,
                 "operation": "UPSERT", "payload": {...} } ] }
→ 200 { "applied": [...], "conflicts": [...] }
→ 409 { "conflicts": [ { "entityType": "...", "entityId": "...",
                        "baseVersion": 3, "currentVersion": 5,
                        "remote": {...},
                        "choices": ["KEEP_LOCAL","KEEP_REMOTE","MANUAL_MERGE"] } ] }
```

规则：

1. 每个可变实体有 `version`，每次成功写入 +1。
2. `sync_changes` 只追加，`seq` 全局递增；客户端保存 `lastCursor`。
3. 推送时校验 `baseVersion`；不一致不覆盖，返回 409。
4. `KEEP_LOCAL` = 以当前 `currentVersion` 为 base 重试一次并记录审计；`KEEP_REMOTE` = 丢弃本地变更；`MANUAL_MERGE` = 用户提交合并后的完整实体。
5. 删除写墓碑（`operation=DELETE`），保留 90 天后由清理任务物理删除。
6. 单次 push 限 200 条 / 5 MB；大文件走预签名直传，不进入同步通道。

### 4.3 离线队列（Android）

- 新增 Room 表 `sync_outbox`：`id, entityType, entityId, operation, baseVersion, payload, createdAt, retryCount, lastError`。
- 新增 Room 表 `sync_state`：`entityType, entityId, serverVersion, lastSyncedAt, dirty`。
- WorkManager：网络可用时指数退避（30s → 1m → 5m → 30m，最多 6 次）；大文件用 `setForeground`。
- 冲突不自动解决；进入“待处理冲突”列表，阻止该实体后续推送直到解决。

**为什么不在现有 21 张表上逐表加 `serverVersion`**：迁移面太大且容易破坏现有测试。用旁表记录同步状态，实体表保持原样，风险最低。

---

## 5. ID 策略（关键决策）

### 5.1 规则

| 场景 | 服务端 `id` | `legacy_id` |
|---|---|---|
| 历史 Seed `seed-student-001` | `UUIDv5(NS, "student:seed-student-001")` | `seed-student-001` |
| 现有 Room UUID 字符串 | 同值 | 同值 |
| 新建记录 | `UUIDv4` | 同值 |
| 知识点 `seed-kp-*` | `UUIDv5(NS, "knowledge:seed-kp-*")` | 原值 |

- 命名空间 `NS = 6f1c2f8a-4c1e-5b7a-9d3e-7a2b1c9d0e4f`（实现时写为常量，变更需迁移）。
- 所有业务表：`id uuid primary key`，`legacy_id text unique not null`。
- API 入参可传 `id` 或 `legacy_id`；返回两者。
- Android 不需要立即改用 UUID；同步层保存服务端映射即可。

### 5.2 禁止

- 用姓名当主键或唯一键。
- 重新生成与历史记录无关的学生 ID。
- 在迁移中丢弃 `legacy_id`。

---

## 6. 试卷处理与 AI 分析流水线

### 6.1 上传与解析

```text
客户端申请上传 URL
→ 直传对象存储（私有）
→ 创建 exam_paper(status=UPLOADED)
→ 异步任务：
   1. 预处理（旋转/去噪/切边）
   2. OCR（整页文本 + 版面块）
   3. 题目切分（题号正则 + 版面）
   4. 字段抽取（题号/分值/题干/选项/学生答案/批改痕迹）
   5. 知识点/题型/难度候选
   6. 生成 exam_questions + student_answers 草稿
→ status=AI_PARSED
→ 教师校对（可逐题改）
→ status=NEEDS_REVIEW → APPROVED / REJECTED
```

状态机：`UPLOADED → PARSING → AI_PARSED → NEEDS_REVIEW → APPROVED | REJECTED`；失败进 `PARSE_FAILED`，可重试。

**硬规则**：OCR/LLM 结果不得直接进入正式题库；必须经过教师确认。解析置信度低于阈值（默认 0.75）的题必须单独标黄。

### 6.2 逐题分析输出

每道题至少写入：

```text
question_number, question_type, knowledge_points[], ability_tags[],
score, full_score, score_rate, student_answer, standard_answer,
mistake_type, mistake_reason, confidence, difficulty,
taught_before, last_taught_at, wrong_before, repeat_count,
suggested_training, potential_score_gain, review_status
```

`taught_before` 只表示“课堂记录里出现过”，**不推导为已掌握**。掌握度只看练习/复测证据。

### 6.3 历史课堂关联

查询顺序：

1. `exam_questions.knowledge_points` → `knowledge_points`
2. `course_sessions` 全文/知识点提及（先 LIKE + trigram，后续可加 embedding）
3. `student_mastery` 最近练习
4. `mistake_records` 历史错误

返回结构包含：讲解日期、复习次数、历史错误次数、最近正确/错误日期、当前掌握度与置信度。

---

## 7. 题库与质量门

### 7.1 题目来源

`SELF_CREATED / TEACHER_IMPORTED / STUDENT_EXAM / AUTHORIZED_BANK / AI_GENERATED / AI_VARIANT`

### 7.2 审核状态

`DRAFT / AI_PARSED / NEEDS_REVIEW / NEEDS_TEACHER_REVIEW / APPROVED / REJECTED`

- AI 生成题默认 `NEEDS_TEACHER_REVIEW`。
- 只有教师操作可以把状态改成 `APPROVED`。
- 任何修改生成新 `question_versions` 记录，不覆盖历史。

### 7.3 质量门（10 项）

| # | 检查 | 实现 |
|---|---|---|
| 1 | 题干完整 | 非空、最小长度、含问句/填空标记 |
| 2 | 条件充分 | LLM 复核 + 规则（单位、变量定义） |
| 3 | 答案唯一 | 选择题选项唯一；计算题数值复算 |
| 4 | 计算正确 | 对可解析数值题做独立计算校验 |
| 5 | 单位正确 | 单位白名单 + 量纲检查 |
| 6 | 知识点匹配 | 与目标知识点集合交集非空 |
| 7 | 难度匹配 | 与请求难度差 ≤1 |
| 8 | 解析正确 | 解析覆盖关键步骤；与答案一致 |
| 9 | 重复检测 | exact hash / normalized hash / trigram / 向量（后期） |
| 10 | 图像一致 | 有图题必须有人工/多模态复核标记 |

任一项失败 → `NEEDS_TEACHER_REVIEW` 或 `REJECTED`，并记录失败原因。

### 7.4 去重三级

1. **exact**：`sha256(stem)` 完全相同。
2. **near**：规范化题干（去空格/标点/数字替换为占位符）后 hash。
3. **semantic**：`pg_trgm` 相似度 ≥ 0.75 作为候选；M06 后期接入 `pgvector`（预留 `question_embeddings` 表与接口，不在第一版启用）。

---

## 8. 掌握度模型

禁止 `mastery = 正确率`。采用带收缩的加权模型：

```text
对知识点 K 的每次作答 i：
  d_i = 难度 1..5
  w_difficulty = 0.6 + 0.2 * (d_i - 1)          // 0.6 .. 1.4
  age_days = now - answered_at
  w_recency = 0.5 ^ (age_days / 45)             // 45 天半衰
  w_i = w_difficulty * w_recency

  raw = 100 * Σ(w_i * correct_i) / Σ(w_i)
  n_eff = Σ(w_i)
  shrunk = (n_eff / (n_eff + 2)) * raw + (2 / (n_eff + 2)) * 50
  repeat_penalty = min(20, 4 * recent_repeat_wrong_count)
  mastery = clamp(shrunk - repeat_penalty, 0, 100)

confidence = min(1.0, n_eff / 5)
trend = 最近 3 次 mastery 的线性斜率（-1..1）
```

- 课堂“讲过”只提高 `confidence` 的上下文权重，**不加分**。
- 复测（同知识点、间隔 ≥1 天）单独计权，权重 ×1.2。
- 公式版本写入 `formula_version`，便于回算。
- 每次重算保留 `student_mastery_history`（可选，M06 加）。

---

## 9. AI Provider 抽象

```java
public interface AiProvider {
    ExamAnalysis analyzeExam(ExamAnalysisRequest request);
    List<GeneratedQuestion> generateQuestions(QuestionGenerationRequest request);
    QuestionReview reviewQuestion(QuestionReviewRequest request);
    String generateExplanation(ExplanationRequest request);
}
```

实现：

| 实现 | 用途 |
|---|---|
| `OpenAiProvider` | OpenAI / 兼容端点 |
| `KimiProvider` | Moonshot / Kimi Code |
| `ClaudeProvider` | Anthropic |
| `LocalProvider` | Ollama / 本地 OpenAI 兼容 |
| `MockProvider` | 测试专用，禁止在生产启用 |

规则：

- 业务层只注入 `AiProvider`，不出现厂商 SDK 类型。
- 所有请求带 `requestId`、`provider`、`model`、`promptVersion`，写入审计。
- 超时、重试、熔断统一在 `ai` 模块；单次请求默认 60s，解析任务异步。
- Prompt 模板版本化存放（`resources/prompts/*.md`），不散落在 Java 字符串里。
- 发送前做字段白名单与去标识化：默认只发学生 ID、题干、答案；姓名/电话/家长信息默认不发。

### 9.1 OCR 抽象

```java
public interface OcrProvider {
    OcrResult recognize(StorageObject object, OcrOptions options);
}
```

云端 OCR 与本地 OCR 可切换；识别结果与置信度原样保存，便于复核与回溯。

---

## 10. MCP / 外部 AI

### 10.1 传输

- MCP Server 挂在 backend 内，路径 `/mcp`，使用 Streamable HTTP + JSON-RPC 2.0。
- 鉴权：`Authorization: Bearer <token>`；Token 只存 SHA-256 哈希，前缀明文用于识别。
- 每个 Token 绑定 `scopes`、`expiresAt`、`rateLimit`。

### 10.2 工具与权限

| 工具 | scope | 说明 |
|---|---|---|
| `search_students` | `ai.read` | 只返回 ID/显示名/年级，不含联系方式 |
| `get_student_profile` | `ai.read` | 去标识化学情摘要 |
| `get_student_sessions` | `ai.read` | 分页 |
| `get_student_mastery` | `ai.read` | 含置信度 |
| `get_student_mistakes` | `ai.read` | 分页 |
| `search_questions` | `ai.read` | 过滤 + 分页 |
| `get_question` | `ai.read` | 不含版权受限原文以外的额外字段 |
| `analyze_exam` | `ai.write.draft` | 只创建分析草稿 |
| `generate_practice` | `ai.write.draft` | 只创建练习草稿 |
| `save_practice_draft` | `ai.write.draft` | 幂等 upsert 草稿 |
| `get_practice` | `ai.read` | |

**不存在的工具**：`delete_student`、`delete_question`、`bulk_delete`、`database_reset`。不是“隐藏”，而是根本不注册。

### 10.3 审计

每次调用记录：`actor_type=AI`、`token_id`、`provider`、`model`、`request_id`、`tool`、`entity`、`before/after`（草稿级）、耗时、结果码。

---

## 11. 对象存储

### 11.1 接口

```java
public interface ObjectStorageProvider {
    PresignedUpload presignUpload(String key, String contentType, Duration ttl);
    PresignedDownload presignDownload(String key, Duration ttl);
    StorageObject put(String key, InputStream data, String contentType);
    void delete(String key);
    boolean exists(String key);
}
```

实现：`S3CompatibleStorageProvider`（R2 / MinIO / OSS / COS 走 S3 兼容端点），必要时再写厂商适配。业务层不出现 R2 SDK。

### 11.2 Key 规范

```text
teachtrack/
├── students/{studentId}/exams/{examPaperId}/{assetId}.{ext}
├── students/{studentId}/homework/{homeworkId}/{assetId}.{ext}
├── students/{studentId}/practice/{practiceId}/{assetId}.{ext}
├── questions/{questionId}/images/{assetId}.{ext}
└── exports/{practiceId}/{variant}.pdf|docx
```

- Bucket 私有；默认签名 URL 10 分钟。
- 上传前算 sha256，同 sha256 可复用已有对象（去重）。
- 数据库只存 `storage_provider/storage_key/mime/size/sha256`。
- 删除先软删除元数据，对象由清理任务延迟删除。

---

## 12. 权限与安全

### 12.1 角色

| 角色 | 权限 |
|---|---|
| `OWNER` | 全部业务权限（教师本人） |
| `TEACHER` | 业务读写，不能管理 Token/用户 |
| `AI_READ` | 只读工具 |
| `AI_WRITE_DRAFT` | 只读 + 创建草稿 |
| `ADMIN` | 运维，不默认授予 AI |

### 12.2 边界

1. Android 不持有数据库凭证；只持有短期 Bearer Token。
2. MCP Token 与用户登录 Token 分离；MCP Token 不能调用用户管理接口。
3. 外部 AI 永远不拿 PostgreSQL 连接。
4. 日志脱敏：Token、电话、地址、家长姓名默认不打印。
5. 上传前去标识化；姓名与试卷内容分离传输时用 student ID。
6. 所有写操作经 Service 校验权限与实体归属。
7. 限流：MCP 每 Token 60 req/min；上传 URL 每分钟 20 次。

---

## 13. 客户端改造策略

### 13.1 阶段化

| 阶段 | Android 变化 | 兼容保证 |
|---|---|---|
| M02 | 只加“连接设置/健康检查”，Room 不变 | 关掉远程即回到现状 |
| M03 | 新增题库缓存表与页面 | 现有页面不动 |
| M04 | 新增 outbox/sync_state + WorkManager | 本地写仍成功；同步失败只记错误 |
| M05 | 新增试卷上传/校对 | 无后端时入口提示不可用 |
| M06 | 新增练习生成/导出 | 导出可本地完成 |
| M07 | 新增 Token 管理页 | 不影响现有 Copilot |

### 13.2 Room 迁移原则

- 只新增表/列，不重命名、不删除现有列。
- 每个版本显式 `Migration`，保持 `exportSchema=true`。
- 新增表建议：
  - `question_cache`、`question_filter_cache`
  - `exam_paper_cache`、`exam_analysis_cache`
  - `practice_cache`、`practice_item_cache`
  - `sync_outbox`、`sync_state`、`sync_conflict`
  - `mastery_cache`
- 不在 Room 中存题干图片二进制；只存 storage key 与本地缓存路径。

### 13.3 功能开关

`RemoteFeatures`（DataStore/SharedPreferences）：

```text
remote.enabled
remote.baseUrl
remote.syncEnabled
remote.examAnalysisEnabled
remote.practiceEnabled
```

任一关闭 → 走现有本地路径；这是回滚的第一道闸门。

---

## 14. 可观测性与运维

| 项 | 方案 |
|---|---|
| 结构化日志 | JSON，含 requestId、entity、duration；脱敏 |
| 健康检查 | `/actuator/health`（DB + storage + AI 配置） |
| 指标 | 请求量/错误率/延迟、解析任务队列、AI 调用量与失败率 |
| 异步任务 | 数据库任务表 + 轮询 worker（第一版不引入 Kafka） |
| 迁移 | Flyway，启动时校验，禁止手工改生产库 |
| 备份 | `pg_dump` 每日；对象存储跨桶复制；恢复脚本同仓库 |
| 告警 | 备份失败、解析连续失败、AI 错误率 > 20% |

---

## 15. 关键技术决策（ADR 摘要）

| ID | 决策 | 理由 |
|---|---|---|
| ADR-01 | 保留 Android 本地优先，不做纯瘦客户端 | 用户明确要求离线可用；现有代码已验证 |
| ADR-02 | 服务端 UUID + legacy_id 双 ID | 同时满足 UUID 要求与历史 ID 不可变 |
| ADR-03 | 同步用 `sync_changes` 游标，不用文件同步 | 可审计、可重放、可做 409 冲突 |
| ADR-04 | 现有 Workspace 保留为导出/灾备 | 不破坏已验证能力，降低迁移风险 |
| ADR-05 | 第一版不上 Elasticsearch / pgvector | 数据量未到；先用 trigram + 复合索引 |
| ADR-06 | AI 生成题一律先草稿 | 防止错误题目直接进教学 |
| ADR-07 | 对象存储抽象为 S3 兼容接口 | 兼容 R2/MinIO/OSS/COS，避免厂商锁定 |
| ADR-08 | MCP 只暴露业务工具，不暴露 SQL | 安全边界；所有写操作可审计 |
| ADR-09 | 新动作按域拆 Registry，共用 CopilotService | 避免 61 KB 单文件继续膨胀 |
| ADR-10 | 新功能全部可关闭 | 出问题能 1 分钟内回退到现状 |

---

## 16. 失败模式与回退

| 失败 | 行为 |
|---|---|
| 后端不可达 | Android 继续本地读写；同步进入重试队列；UI 显示“未同步”而非报错阻塞 |
| 对象存储不可达 | 上传失败可重试；已上传元数据标记 `PENDING` |
| AI Provider 失败 | 返回明确错误码；不写入半成品；可切换 Provider 重试 |
| OCR 置信度低 | 题目标黄，强制人工校对 |
| 同步冲突 | 409 + 冲突列表；不静默覆盖 |
| 迁移失败 | 事务回滚 + 批次标记；从备份恢复 |
| 版本不兼容 | API 版本前缀 `/api/v1`；客户端检查 `minAppVersion` |

---

## 17. 验收基线（架构层）

M02 起每个阶段必须同时满足：

1. `./gradlew :app:testDebugUnitTest` 与现有仪器测试不回退。
2. 后端 `./gradlew test` 全绿（新建后）。
3. 关闭 `remote.enabled` 后，原有 68 个 Copilot 动作全部可用。
4. 迁移重复执行两次，学生/课堂条数不增加。
5. 备份 → 删除测试库 → 恢复 → 对账一致。
6. 所有新增写操作有审计记录。
7. 所有新增 AI 输出默认非 `APPROVED`。
