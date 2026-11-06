# M02–M07 实施状态与验收记录

- 日期：2026-11-05
- 代码：`D:\SoftWare\TeachTrack`
- 后端：`backend/`（Java 21 + Spring Boot 3.4.1 + JDBC + Flyway）
- 数据库：PostgreSQL 16.9，开发端口 55432
- Android：`3.0.0-alpha.3` 基础上增量，Room v8

---

## 1. 交付总览

| 里程碑 | 后端 | Android | 自动化证据 | 状态 |
|---|---|---|---|---|
| M01 | — | — | Android 35/35、assembleDebug | ✅ |
| M02 | PostgreSQL/Flyway/Token/对象存储/审计/备份 | 未接入 | 后端测试、备份恢复演练 | ✅（S3 未联调） |
| M03 | 迁移 + 题库 CRUD/搜索/审核/去重/附件 | 题库页（未真机） | 迁移/题库测试 | ✅ 后端，UI 部分 |
| M04 | 游标 + push + 幂等 + 409 | Room v8 + WorkManager + 客户端 | 同步测试 + 客户端契约 | ✅ 协议，双端未上机 |
| M05 | 导入 + 解析 + 逐题分析 + 历史关联 + 审核 | 未实现 | ExamApiTest | ✅ 后端，UI 缺 |
| M06 | 组卷 + 审批 + 提交 + 掌握度 + PDF/DOCX | 未实现 | PracticeApiTest | ✅ 后端（AI 原创未配置） |
| M07 | MCP + 11 工具 + scope + AI 审计 | — | McpApiTest + 实跑 | ✅ 本地，生产未部署 |

测试合计：后端 **21/21**，Android **38/38**。

---

## 2. 后端结构与关键端点

```text
backend/src/main/java/com/leslie/teachtrack/
├── auth/        bootstrap token、Bearer 过滤器、scopes
├── common/      ApiException、错误信封、分页、UUIDv5/SHA-256
├── config/      Security、Storage、Properties
├── student/     学生列表/详情/课堂
├── course/      课堂读取
├── knowledge/   学科、知识点树
├── question/    题库 CRUD、版本、审核、搜索、去重、附件
├── exam/        试卷导入、解析、逐题分析、修订、审核入库
├── practice/    组卷、提交、掌握度、PDF/DOCX 导出
├── ai/          AiProvider + MockAiProvider
├── storage/     ObjectStorageProvider、本地/S3、签名 URL
├── sync/        游标、push、幂等、409
├── audit/       用户/AI 审计
├── migration/   seed v1 / backup JSON / ttseed
└── mcp/         JSON-RPC、工具、scope
```

主要端点：

```text
GET  /actuator/health
GET  /api/v1/students
GET  /api/v1/students/{id}
GET  /api/v1/students/{id}/sessions
GET  /api/v1/students/{id}/mistakes      (通过题库/错题数据)
GET  /api/v1/students/{id}/mastery
GET  /api/v1/students/{id}/exams
GET  /api/v1/questions
POST /api/v1/questions
GET  /api/v1/questions/{id}
PATCH /api/v1/questions/{id}
DELETE /api/v1/questions/{id}
GET  /api/v1/questions/duplicates?stem=
POST /api/v1/questions/{id}/review
POST /api/v1/questions/{id}/assets
POST /api/v1/exams/import
POST /api/v1/exams/{id}/analyze
GET  /api/v1/exams/{id}/analysis
PATCH /api/v1/exams/{id}/questions/{examQuestionId}
POST /api/v1/exams/{id}/approve
POST /api/v1/practices/generate
GET  /api/v1/practices/{id}
POST /api/v1/practices/{id}/approve
POST /api/v1/practices/{id}/submit
GET  /api/v1/practices/{id}/export
GET  /api/v1/sync/changes
POST /api/v1/sync/operations
POST /api/v1/admin/migrations
POST /mcp
POST /api/v1/assets/upload-url
PUT  /api/v1/assets/upload/{encodedKey}
GET  /api/v1/assets/{id}/download-url
GET  /api/v1/assets/download/{encodedKey}
```

---

## 3. 已实测的关键行为

1. **迁移幂等**：seed v1 连续两次，29 学生 / 30 课程 / 14 成绩不变；dry-run 全回滚。
2. **题库乐观锁**：旧 `baseVersion` 返回 409 `VERSION_CONFLICT`。
3. **同步幂等**：同一 `operationId` 重放不产生第二次写入。
4. **同步冲突**：旧版本推送返回 409 结构，含 `KEEP_LOCAL`/`KEEP_REMOTE`/`MANUAL_MERGE`。
5. **试卷分析**：2 题（1 对 1 错）→ `AI_PARSED`；错题 `NEEDS_TEACHER_CONFIRMATION` 或 `UNKNOWN`；`taught_before` 命中“液体压强”。
6. **审核入库**：试卷题目在教师 approve 后写入题库并回填 `question_id`。
7. **掌握度**：1 对 1 错得到 0–100 的分值、置信度 > 0，不是简单正确率。
8. **导出**：PDF 以 `%PDF` 开头，DOCX 以 `PK` 开头；对象存储在私有目录，通过签名 URL 下载。
9. **MCP**：11 个工具；无 `delete_*`/`database_reset`；只读 Token 调用写工具被拒绝；AI 审计落库。

---

## 4. 未完成 / 未验证

| 项 | 状态 | 说明 |
|---|---|---|
| Android 试卷分析页 | TODO | 后端 API 已就绪 |
| Android 精准练习页 | TODO | 后端 API 已就绪 |
| Android 题库页真机点击 | UNVERIFIED | `adb devices` 无设备 |
| 两独立 Android 客户端同步 | UNVERIFIED | 协议与客户端解析已测 |
| 真实 AI Provider | UNVERIFIED | 无 Key；默认 Mock 不生成 |
| AI 原创/变式题 | UNVERIFIED | 需 Provider + 质量门 |
| S3/MinIO 联调 | UNVERIFIED | 本地存储已测 |
| 真实 `.ttseed.json` | BLOCKED | 文件缺失 |
| 生产部署/监控/费用告警 | TODO | 需云资源与授权 |

---

## 5. 回滚

| 层 | 回滚 |
|---|---|
| Android | 关闭远程功能（不配置 Cloud Token）→ 纯本地；回退 Room v8 代码与 `sync_*` 表（保留数据不影响旧版读取） |
| 后端 | 停止进程；Flyway 不自动回滚；恢复 M02 前 `pg_dump` |
| 数据 | `import_jobs` 批次回滚；或恢复快照 |
| 对象存储 | 删除 `exports/` 与对应 `assets` 行；私有 Bucket 不影响其他数据 |
| MCP | 关闭 `/mcp` 路由或吊销 Token；不影响 REST |

---

## 6. 已知问题

1. `AiProvider` 只有 Mock；真实生成未接入。
2. `QuestionService.create` 使用 `REQUIRES_NEW` 供试卷审核调用；创建类操作的事务边界需在后续统一评审。
3. `student_mastery.trend` 仍是数值文本，趋势算法简单。
4. 迁移报告中的 `knowledgePoints` 已改为唯一计数；`courses` 计数包含测试数据时需按 `legacy_id` 过滤。
5. `docs/ai-practice` 初版设计中的 M02 迁移已按最新版提示词后移到 M03。
