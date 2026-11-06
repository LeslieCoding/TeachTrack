# Backend 备份与恢复

## 备份

```bash
cd backend/ops
TEACHTRACK_DB_URL=postgresql://teachtrack@127.0.0.1:55432/teachtrack \
PG_BIN=/d/SoftWare/pgsql-16/bin ./backup.sh
```

输出：

```text
backend/backups/postgres/teachtrack-YYYYMMDD-HHMMSS.dump
backend/backups/postgres/teachtrack-YYYYMMDD-HHMMSS.dump.sha256
```

保留策略（生产）：

- 日备 × 7
- 周备 × 4
- 月备 × 6
- 备份上传到独立 Bucket，不与数据库同机同盘

## 恢复演练

```bash
PG_BIN=/d/SoftWare/pgsql-16/bin ./restore-test.sh
```

脚本会：

1. 删除并重建 `teachtrack_restore_test`（独立库，不碰生产库）
2. `pg_restore` 最新 dump
3. 输出学生、课堂、题目、试卷、练习行数

**验收标准**：行数与备份时基线一致；否则视为备份不可用。

## Cloudflare D1 备份与恢复

```bash
# 在 backend/ 下执行：远端导出 -> 本地 SQLite 恢复验证 -> 加密 -> 上传百度网盘
bash ops/d1-backup.sh

# 下载指定备份、校验 SHA-256、解密并恢复到独立内存库核对行数
bash ops/d1-restore-test.sh teachtrack/backups/YYYYMMDD-HHMMSS-d1
```

2026-11-06 已实测 `teachtrack/backups/20261106-044450-d1`：下载、解密、恢复后 30 学生、31 课堂、1 题目、177 知识点，与 manifest 完全一致。全程不修改生产 D1。

## 定时任务

生产环境使用系统 cron / 计划任务，每天 03:17 执行 `backup.sh`，失败告警。开发环境不自动执行，避免占磁盘。
