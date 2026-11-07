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

## 定时任务

生产环境使用系统 cron / 计划任务，每天 03:17 执行 `backup.sh`，失败告警。开发环境不自动执行，避免占磁盘。
