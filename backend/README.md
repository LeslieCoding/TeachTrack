# TeachTrack Backend (4.0)

Java 21 + Spring Boot 3 + PostgreSQL 16 + Flyway。当前覆盖 **M02 数据库与文件底座**。


## 为什么用 JDBC 而不是 JPA

本项目的查询以多表、过滤、聚合、游标为主，SQL 比实体图更直接；Flyway 是唯一 schema 来源。用 `JdbcTemplate` 避免为 25+ 张表维护 JPA 实体与懒加载陷阱，后续题库/分析查询也不受 ORM 限制。

## 目录

```text
backend/
├── src/main/java/com/leslie/teachtrack/
│   ├── auth/        Token、bootstrap、过滤器
│   ├── common/      错误信封、分页
│   ├── config/      Security、Storage、Properties
│   ├── course/      课堂记录读取
│   ├── student/     学生读取
│   ├── storage/     ObjectStorageProvider + 本地/S3 实现
│   ├── sync/        变更游标（M04 扩展推送）
│   └── audit/       审计写入
├── src/main/resources/db/migration/  Flyway V001–V010
└── src/test/java/                    存储单测 + 后端集成测试
```

## 本地启动

### 1. PostgreSQL

开发默认端口 `55432`，避免与本机其他实例冲突。

```bash
# 便携版示例（Windows）
D:/SoftWare/pgsql-16/bin/initdb.exe -D D:/SoftWare/pgdata-teachtrack -U teachtrack -A trust --encoding=UTF8
D:/SoftWare/pgsql-16/bin/pg_ctl.exe -D D:/SoftWare/pgdata-teachtrack -o "-p 55432" -l D:/SoftWare/pgdata-teachtrack/server.log start
D:/SoftWare/pgsql-16/bin/createdb.exe -p 55432 -U teachtrack teachtrack
D:/SoftWare/pgsql-16/bin/createdb.exe -p 55432 -U teachtrack teachtrack_test
```

### 2. 配置

```bash
cp .env.example .env
# 编辑 .env：至少设置 TEACHTRACK_BOOTSTRAP_TOKEN 与 TEACHTRACK_STORAGE_SECRET
```

> 密钥不得提交。`TEACHTRACK_BOOTSTRAP_TOKEN` 只在 `api_tokens` 为空时使用一次；之后请用 Token 管理接口（M03+）轮换。

### 3. 启动

```bash
JAVA_HOME=D:/SoftWare/jdk-21 ../gradlew.bat -p backend bootRun
```

健康检查：

```bash
curl -s http://127.0.0.1:8080/actuator/health
```

## 认证

所有 `/api/v1/**` 需要：

```http
Authorization: Bearer <TEACHTRACK_BOOTSTRAP_TOKEN>
```

例外：`/api/v1/assets/upload/**`、`/api/v1/assets/download/**` 使用 HMAC 签名 URL（短期有效）。

## M02 已实现 API

```text
GET  /actuator/health
GET  /api/v1/students?status=&q=&limit=&offset=
GET  /api/v1/students/{id}                 # UUID 或 legacy_id
GET  /api/v1/students/{id}/sessions
POST /api/v1/assets/upload-url
PUT  /api/v1/assets/upload/{encodedKey}?exp=&sig=
GET  /api/v1/assets/{assetId}/download-url
GET  /api/v1/assets/download/{encodedKey}?exp=&sig=
GET  /api/v1/sync/changes?cursor=&limit=
```

## 对象存储

`teachtrack.storage.provider=local`：

- 文件落在 `TEACHTRACK_STORAGE_ROOT`（默认 `./data/storage`）；
- 只能通过 HMAC 签名 URL 访问；
- 签名绑定 `action + key + 过期时间`，篡改或过期返回 403。

`teachtrack.storage.provider=s3`：

- 需要 `TEACHTRACK_S3_ENDPOINT/REGION/BUCKET/ACCESS_KEY/SECRET_KEY`；
- 使用 path-style，兼容 MinIO / R2 / OSS / COS；
- 未配置时启动失败，不会静默降级。

## 测试

```bash
# 需要 teachtrack_test 库
JAVA_HOME=D:/SoftWare/jdk-21 ../gradlew.bat -p backend test
```

集成测试覆盖：Flyway 可重复执行、健康检查公开、未授权 401、Token 访问、私有上传/签名下载/篡改拒绝。

## 备份与恢复

```bash
backend/ops/backup.sh        # pg_dump -> backups/
backend/ops/restore-test.sh  # 恢复到独立库并核对
```

详见 `ops/README.md`。

## 已知限制（M02）

- 同步只有拉取游标，推送/冲突在 M04。
- S3 适配器未做真实 MinIO 联调（无 MinIO 环境），标记 `UNVERIFIED`。
- 学生/课堂目前只读；写入与迁移在 M03。
- 题库/试卷/练习表已建，但 API 未实现（M03–M06）。

## 云端数据库

本地 `55432` 只是开发库。切云库：

1. 按 `ops/cloud-setup.md` 创建 Supabase/Neon/RDS 项目，拿连接串。
2. `ops/migrate-to-cloud.sh` 把本地数据迁到云库。
3. 用 `--spring.profiles.active=cloud` 启动，`TEACHTRACK_DB_URL` 指向云库。
4. `GET /api/v1/system/database` 确认 `mode=CLOUD_OR_REMOTE` 且 `ssl=true`。

没有云账号/连接串时，不会伪称已完成云端部署。
