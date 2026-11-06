# IMPLEMENTATION_CHECKLIST

状态：TODO / IN_PROGRESS / BLOCKED / VERIFIED / UNVERIFIED

## M01 现有工程审计 — VERIFIED

- [x] 仓库/依赖/LAEP/Room/Copilot/同步审计
- [x] CURRENT_ARCHITECTURE / GAP_ANALYSIS / MIGRATION_PLAN
- [x] 目标架构 / DB schema / roadmap
- [x] 基线：Android 35/35；assembleDebug
- [x] 未改业务代码、未动数据库
- [x] 报告 `.ttseed.json` 缺失

## M02 数据库与文件底座 — VERIFIED（S3 UNVERIFIED）

- [x] PostgreSQL 16.9（55432）
- [x] backend Gradle + Spring Boot
- [x] Flyway V001–V014；重复执行 0 变更
- [x] `/actuator/health` UP
- [x] Token 认证 + 401 + scope
- [x] ObjectStorageProvider + 本地私有存储 + 签名 URL
- [x] 私有上传/下载/篡改 403
- [ ] UNVERIFIED S3/MinIO 真实联调
- [x] audit_logs
- [x] 后端测试
- [x] `.env.example` + `application.yml`
- [x] 备份/恢复演练（1 学生/1 课堂对账）

## M03 历史迁移与题库中心 — 后端 VERIFIED / Android 部分

- [ ] BLOCKED 真实 `.ttseed.json`
- [x] 迁移：SEED_V1 / BACKUP_JSON / TTSEED
- [x] 幂等 + 对账 + import_jobs/issues
- [x] 题库 CRUD / 版本 / 软删除
- [x] 知识点 / 审核 / 附件关联
- [x] 去重：exact + normalized + trigram
- [x] 题库 API 测试
- [x] Android 题库页（列表/搜索/详情/审核）编译
- [ ] UNVERIFIED Android 题库页真机点击

## M04 跨设备同步 — 后端/客户端 VERIFIED，双端 UNVERIFIED

- [x] `GET /sync/changes` 游标
- [x] `POST /sync/operations` 幂等键
- [x] 409 冲突 + KEEP_LOCAL/MANUAL_MERGE 语义
- [x] Room v8：sync_outbox / sync_state / sync_conflicts
- [x] WorkManager 调度
- [x] CloudSyncClient 契约测试
- [ ] UNVERIFIED 两独立客户端同时同步（无设备）

## M05 试卷解析与逐题诊断 — 后端 VERIFIED / UI 未完成

- [x] 试卷导入（assetId 可选）
- [x] 结构化题目 / 文本切题
- [x] 逐题得分 / 知识点匹配
- [x] taught_before / last_taught_at / wrong_before / repeat_count
- [x] 错因 UNKNOWN / NEEDS_TEACHER_CONFIRMATION（不猜测）
- [x] 报告：总分、题型、知识点、错因
- [x] 教师修订 + 审核入库
- [ ] TODO Android 试卷分析页
- [ ] UNVERIFIED 真实 OCR/多模态 Provider

## M06 AI 精准练习与导出 — 后端 VERIFIED（AI 原创 UNVERIFIED）

- [x] 已审核题库选题
- [x] 练习集 / 题目 / 审批
- [x] 提交与自动判分（选择/填空）
- [x] 掌握度公式 v1（难度/时间/重复错误/收缩）
- [x] PDF 学生版/教师版
- [x] DOCX 学生版/教师版
- [x] 导出字节测试（%PDF / PK）
- [ ] UNVERIFIED AI 原创/变式（Mock 不生成；缺 Key）
- [ ] TODO Android 精准练习页

## M07 MCP / 外部 AI — 本地 VERIFIED，生产 UNVERIFIED

- [x] MCP JSON-RPC `/mcp`
- [x] 11 个工具
- [x] Token scope（ai.read / ai.write.draft）
- [x] 危险工具不注册
- [x] AI 审计（actor_type=AI、token、requestId）
- [x] 未授权 401、只读 Token 写操作拒绝
- [ ] UNVERIFIED 真实 ChatGPT/Kimi/Claude 客户端接入
- [ ] TODO 生产部署、监控、费用告警
