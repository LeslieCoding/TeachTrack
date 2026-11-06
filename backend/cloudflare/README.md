# TeachTrack Cloudflare 后端

现有 Java/Spring Boot 后端的并行实现，运行在 Cloudflare Workers，数据存储在 Cloudflare D1（SQLite 语义）。Android API 路径和 JSON 字段保持兼容；原 PostgreSQL 后端与数据均保留。

## 当前部署

- 入口：`https://teachtrack.leslieholmes.ccwu.cc`
- Worker：`teachtrack-api`
- D1：`teachtrack`，主区域 APAC
- 真实数据：30 学生、31 课堂、177 知识点、181 条掌握度记录
- 本机后端和 Tunnel 停止后，云端与 OPPO Pad 均可读取数据

## 已实现

- 学生列表/详情/课堂记录
- 学科与知识点
- 题库 CRUD、版本历史、乐观锁、软删除、审核状态、附件关联、去重
- 试卷导入、逐题分析、历史课堂关联、教师修订、审核入题库
- 练习生成、教师审核、提交判分、掌握度重算
- 增量同步拉取、幂等推送、版本冲突与三种冲突处理选项
- MCP 11 个业务工具，AI 审计；无删库/批量删除/直接 SQL 工具
- KV（免费 1GB）题图存储、题目图片上传/列表、短期签名 URL；R2 存在时自动优先 R2
- App 题目详情显示题图，教师可直接从相册/拍照上传题图
- 含图题缺少图片时禁止 APPROVED，组卷时自动排除
- PDF/DOCX 学生版与教师版导出，嵌入开源中文字体和题目配图
- JSON 迁移接口，支持 SEED_V1、BACKUP_JSON、TTSEED、dry-run 与幂等导入

## 本地开发

```bash
cd backend/cloudflare
npm install
python tools/build-schema.py
npx wrangler d1 migrations apply teachtrack --local
npm test
npm run typecheck
npx wrangler dev --local --port 8789
```

`.dev.vars` 只放本地测试值，已被 `.gitignore` 排除。生产认证使用 D1 中的 `api_tokens` SHA-256 哈希，不需要明文 Token 写入配置。

## 从 PostgreSQL 迁移真实数据

```bash
python tools/pg-to-d1.py
npx wrangler d1 execute teachtrack --local --file=data/d1-import.sql
npx wrangler d1 execute teachtrack --remote --file=data/d1-import.sql
```

迁移按外键依赖排序，知识点树按父子顺序写入；`insert or ignore` 保证重复执行不产生重复记录。`data/` 含真实学生数据，已排除在 Git 之外；迁移完成后可删除并由脚本重新生成。

## 部署

```bash
npx wrangler login
npx wrangler d1 migrations apply teachtrack --remote
npx wrangler deploy
```

Worker 路由 `teachtrack.leslieholmes.ccwu.cc/*` 优先于现有 Tunnel 处理请求。保留 Tunnel DNS 记录，便于回退。

## 备份与恢复

```bash
cd backend
bash ops/d1-backup.sh
bash ops/d1-restore-test.sh teachtrack/backups/YYYYMMDD-HHMMSS-d1
```

备份会先做一次本地 SQLite 恢复验证，再加密上传到百度网盘；恢复脚本会重新下载、校验、解密并核对行数。2026-11-06 已完成一次全链路实测。

## 验证

```bash
npm test           # 23 tests
npm run typecheck
npx wrangler deploy --dry-run
curl https://teachtrack.leslieholmes.ccwu.cc/actuator/health
```

已实测：本地 D1 真实数据导入 783 条写入；远端 APAC 查询；公网学生/课堂/题库/同步/MCP；停止本机后端与 Tunnel 后仍返回 30 学生；OPPO Pad 学生页成功加载。

## 尚未完成

- 题图当前使用 KV：免费 1GB、每天 1000 次写入、单文件 25MiB；不适合全集 5044 文档的图片一次性上传，也不适合大文件长期归档。
- KV 是最终一致性存储，刚上传后其他节点可能短暂读不到；已通过的含图题如果图片被删，导出会明确报错而不是生成残卷。
- 完整 PDF/Word 原始讲义建议归档百度网盘；R2 仍是大量题图与导出文件的长期方案，需要开通付款方式。
- 原 `POST /api/v1/admin/migrations` 改为在 `data` 字段上传 JSON；Workers 不读取用户电脑路径。PostgreSQL 真实迁移也可由 `tools/pg-to-d1.py` 完成。
- D1 的写入吞吐不适合高并发批量写入；TeachTrack 当前为单教师低并发场景。
- 题干模糊搜索使用 `LIKE`，数据量大后需增加全文检索表；语义向量检索尚未实现。

## 回退

保留原 PostgreSQL 数据库和 Java jar。移除 `wrangler.jsonc` 中的 Worker 路由并重新部署后，现有 Tunnel DNS 可再次指向本机；再启动 `TeachTrackBackend` 和 `TeachTrackTunnel` 计划任务即可恢复原链路。
