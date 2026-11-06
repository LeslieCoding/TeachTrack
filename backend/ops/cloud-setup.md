# 切换到云端 PostgreSQL

当前开发库是本地 PostgreSQL（`127.0.0.1:55432`）。后端不依赖本地地址，切换到云库只需要换连接串并用 `cloud` profile 启动。

## 0. 最快路径：Supabase 免费档（约 5 分钟）

### 1. 建项目

1. 打开 `https://supabase.com`，登录/注册。
2. **New project**：
   - Organization：默认即可
   - Name：`teachtrack`
   - Database Password：设一个强密码并保存好
   - Region：选 **Southeast Asia (Singapore)** 或 Tokyo（国内访问相对稳）
3. 等 1–2 分钟初始化完成。

### 2. 复制连接串

Project → **Settings → Database → Connection string → URI**。

选 **Session pooler**（端口 `5432`），不要选 Direct connection（很多网络只有 IPv6，连不上），也不要用 Transaction pooler（`6543`）跑 Flyway 迁移。

你会得到类似：

```text
postgresql://postgres.abcdxyz:你的密码@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres
```

JDBC 写法：

```text
jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
```

用户名是 `postgres.<project-ref>`（不是单纯的 `postgres`）。

### 3. 填 `backend/.env`

```bash
cp backend/.env.example backend/.env
```

编辑：

```dotenv
TEACHTRACK_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
TEACHTRACK_DB_USER=postgres.abcdxyz
TEACHTRACK_DB_PASSWORD=你的密码

# 迁移脚本用（非 JDBC 格式）
CLOUD_DB_URL=postgresql://postgres.abcdxyz:你的密码@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
```

`.env` 已在 `.gitignore`，不要提交。

### 4. 迁移本地数据

```bash
cd backend/ops
SOURCE_DB_URL='postgresql://teachtrack@127.0.0.1:55432/teachtrack' \
CONFIRM=YES \
PG_BIN=/d/SoftWare/pgsql-16/bin \
./migrate-to-cloud.sh
```

脚本会 dump 本地库、restore 到云库、校验 50 张表、Flyway V014、学生/课堂/题库行数。云库必须是空库；`CONFIRM=YES` 是防误删门槛。

### 5. 用 cloud profile 启动

PowerShell：

```powershell
backend/ops/run-cloud.ps1
```

Git Bash：

```bash
backend/ops/run-cloud.sh
```

脚本读取 `backend/.env`，不会把密码打印到终端。

### 6. 确认真的在云上

```bash
curl -s -H "Authorization: Bearer $TEACHTRACK_BOOTSTRAP_TOKEN" \
  http://127.0.0.1:8080/api/v1/system/database
```

看到：

```json
{"host":"aws-0-ap-southeast-1.pooler.supabase.com","port":5432,"ssl":true,"mode":"CLOUD_OR_REMOTE"}
```

就说明已切到云库。

> 免费档注意：项目长时间无请求会暂停；恢复后首次连接较慢。国内网络请实测；如果 Supabase 不稳定，换 Neon 或国内云 RDS，步骤相同，只换连接串。

## 1. 云端实例选择

| 供应商 | 适用 | 注意 |
|---|---|---|
| Supabase | 初期最省事 | 用 **Session pooler**（端口 5432）；Direct connection 常为 IPv6；Transaction pooler 6543 不适合 Flyway |
| Neon | 免费档、分支数据库 | 使用 pooled connection string；闲置会挂起，首次连接慢 |
| 阿里云 RDS / 腾讯云 PostgreSQL | 国内网络稳定 | 需白名单/安全组，不建议对公网开放 |
| AWS RDS / Azure | 长期生产 | 成本更高，需 VPC/备份策略 |

**只使用标准 PostgreSQL**，不要绑定某家私有 API。应用层不需要改代码。

## 2. 创建数据库后拿三样东西

```text
TEACHTRACK_DB_URL=jdbc:postgresql://<host>:<port>/<database>?sslmode=require
TEACHTRACK_DB_USER=<user>
TEACHTRACK_DB_PASSWORD=<password>
```

> Supabase 的 JDBC 写法示例：
> `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0`
> 其中 `prepareThreshold=0` 用于连接池模式，避免 prepared statement 跨连接复用问题。

密钥只放环境变量或 Secret Manager，**不得写进仓库、APK、日志或备份**。

## 3. 迁移本地数据到云库

前提：云库是**新建/空库**，且已允许当前机器访问。

```bash
cd backend/ops
SOURCE_DB_URL='postgresql://teachtrack@127.0.0.1:55432/teachtrack' \
CLOUD_DB_URL='postgresql://user:pass@host:5432/teachtrack?sslmode=require' \
CONFIRM=YES \
PG_BIN=/d/SoftWare/pgsql-16/bin \
./migrate-to-cloud.sh
```

脚本会：

1. `pg_dump` 本地库到 `backend/backups/cloud-migration/`
2. 验证云库连通
3. `pg_restore --clean --if-exists` 到云库
4. 校验表数量与 Flyway 历史
5. 对比 `students / course_sessions / questions / exam_papers / practice_sets` 行数

`CONFIRM=YES` 是强制门槛，因为 `--clean` 会删除目标库现有对象。**不要把 CLOUD_DB_URL 指向已有生产库。**

## 4. 用 cloud profile 启动

```bash
export TEACHTRACK_DB_URL='jdbc:postgresql://host:5432/teachtrack?sslmode=require'
export TEACHTRACK_DB_USER='...'
export TEACHTRACK_DB_PASSWORD='...'
export TEACHTRACK_BOOTSTRAP_TOKEN='...'
export TEACHTRACK_STORAGE_PROVIDER=s3
export TEACHTRACK_S3_ENDPOINT='https://<account>.r2.cloudflarestorage.com'
export TEACHTRACK_S3_REGION=auto
export TEACHTRACK_S3_BUCKET='teachtrack-private'
export TEACHTRACK_S3_ACCESS_KEY='...'
export TEACHTRACK_S3_SECRET_KEY='...'
export TEACHTRACK_STORAGE_SECRET='...'

JAVA_HOME=D:/SoftWare/jdk-21 ../gradlew.bat -p backend bootRun \
  --args='--spring.profiles.active=cloud'
```

启动时 Flyway 会自动校验/补齐到最新版本；如果云库已由 `pg_restore` 带入 `flyway_schema_history`，不会重复执行。

## 5. 确认真的连到云库

```bash
curl -s -H "Authorization: Bearer $TEACHTRACK_BOOTSTRAP_TOKEN" \
  http://127.0.0.1:8080/api/v1/system/database
```

返回示例：

```json
{
  "engine": "postgresql",
  "host": "aws-0-ap-southeast-1.pooler.supabase.com",
  "port": 6543,
  "database": "postgres",
  "ssl": true,
  "mode": "CLOUD_OR_REMOTE",
  "note": "Connected to a remote PostgreSQL instance. Verify TLS and backups."
}
```

如果 `mode` 还是 `LOCAL_DEV`，说明启动时用的仍是本地连接串。

## 6. 云库安全与备份

1. 数据库**不要**开放公网匿名访问；使用供应商连接池、IP 白名单或 VPC。
2. 必须 `sslmode=require`（生产建议 `verify-full`）。
3. 云库每日 `pg_dump` 到**独立**对象存储，保留 7 日 / 4 周 / 6 月。
4. 每月至少一次恢复到隔离库并对账。
5. 应用账号只授予 `SELECT/INSERT/UPDATE`，DDL 由 Flyway 专用账号执行；不要给外部 AI 管理员账号。
6. 监控连接数、存储、慢查询、备份失败；AI Token 用量单独统计。

## 7. 当前状态

| 项 | 状态 |
|---|---|
| 本地 PostgreSQL 开发库 | 已运行（55432） |
| 云端连接串/profile/迁移脚本 | 已就绪 |
| 云库实例 | **未创建**：需要云账号/凭证或明确授权 |
| 真实云库端到端 | **UNVERIFIED**：没有实例，不能声称已完成 |

创建 Supabase/Neon 项目需要账号登录（通常还要邮箱验证/验证码），不能由 AI 擅自注册或绑定付费方式。拿到连接串后，上述脚本可以一次完成迁移与校验。

---

## 已落地的免费云备份（不是可连接数据库）

2026-11-05 已用现有 GitHub 账号创建**私有**仓库：

```text
https://github.com/LeslieCoding/teachtrack-cloud
```

内容：

- `dumps/<timestamp>/teachtrack.dump.enc`：AES-256-CBC/PBKDF2 加密的 PostgreSQL dump
- `dumps/<timestamp>/manifest.json`：只有行数，没有姓名/电话/答案
- `README.md`：恢复步骤

执行：

```bash
cd backend/ops
./cloud-backup.sh
```

密钥：

```text
backend/.cloud-backup-key
```

**必须把这个文件另存到安全位置**。密钥丢失，云备份不可恢复。该文件已 gitignore，不会进仓库。

限制：这是**云存储备份**，不是可连接的 PostgreSQL。Android/后端不能直接把 GitHub 仓库当数据库用。真正的云数据库仍需 Neon/Supabase 实例。

---

## 百度网盘作为文件存储（baidu-pan）

百度网盘只能存**文件/图片/PDF**，不能当 PostgreSQL 数据库引擎。适合把试卷图片、导出 PDF 放进去，数据库仍然用 Neon/本地。

实现：`ObjectStorageProvider` 的 `baidu-pan` 适配器，通过 `bypy` 调百度官方 PCS API。

### 一次性授权

```bash
pip install bypy
bypy info
```

终端会给出一个百度授权链接。浏览器登录百度账号 → 复制授权码 → 粘贴回终端。授权一次后 token 保存在用户目录。

文件会出现在网盘的：

```text
我的应用数据 / bypy / teachtrack
```

### 配置

```dotenv
TEACHTRACK_STORAGE_PROVIDER=baidu-pan
TEACHTRACK_BYPY=bypy
TEACHTRACK_BAIDU_ROOT=teachtrack
TEACHTRACK_BAIDU_TIMEOUT=120
```

启动后端后，上传接口会把文件写到百度网盘；下载由后端代理读回，Android 不需要百度账号。

### 限制

- 单步上传 2 GB 上限；更大文件需分片，当前适配器未实现分片。
- 每次操作启动一次 `bypy`，小文件多时比 S3 慢。
- 百度可能限速/风控；不适合作为高频题库图片 CDN。
- 文件只能放在 `/apps/bypy/` 下，这是百度开放平台的限制。
- 真实百度账号联调需要一次授权码；当前用桩程序验证了命令链路，真实网盘标记 `UNVERIFIED`。

### 本机已安装

`bypy 1.8.9` 已安装并放到 PATH：

```text
D:\SoftWare\Python311\Scripts\bypy.exe
```

如果换机器后 `bypy` 不在 PATH，设置：

```dotenv
TEACHTRACK_BYPY=C:/Users/<你>/AppData/Roaming/Python/Python311/Scripts/bypy.exe
```

### 真实授权（只需一次）

运行：

```bash
bypy info
```

它会打印百度授权链接，浏览器登录百度账号并同意后，复制页面上的 Authorization Code，粘贴回终端。

授权成功后：

```bash
bypy mkdir /apps/bypy/teachtrack
bypy list /apps/bypy/teachtrack
```

再把 `TEACHTRACK_STORAGE_PROVIDER` 改成 `baidu-pan` 并重启后端。之后：

- `POST /api/v1/assets/upload-url` 上传的文件会进百度网盘
- `GET /api/v1/assets/{id}/download-url` 由后端代理下载
- 文件在网盘 `我的应用数据 / bypy / teachtrack`

**百度网盘不能当 PostgreSQL 数据库**，只能放文件；数据库仍需本地或 Neon。

### 本机授权状态（2026-11-05）

```text
bypy info → Quota: 70.000GB, Used: 0.000B
```

已真实上传/下载验证：

```text
/apps/bypy/teachtrack/test.txt
/apps/bypy/teachtrack/tests/unassigned/...
```

启动百度网盘存储模式：

```powershell
backend/ops/run-baidu.ps1
```

```bash
backend/ops/run-baidu.sh
```

自检：

```bash
curl -H "Authorization: Bearer $TEACHTRACK_BOOTSTRAP_TOKEN" \
  http://127.0.0.1:8080/api/v1/system/storage
# {"provider":"baidu-pan"}
```

再次强调：百度网盘只存文件；`/api/v1/system/database` 仍会显示本地 PostgreSQL。

### 数据库备份也放百度网盘

```bash
cd backend/ops
./baidu-backup.sh
```

真实结果：

```text
/apps/bypy/teachtrack/backups/20261106-011645/
  teachtrack.dump.enc
  teachtrack.dump.enc.sha256
  manifest.json
```

这是 **pg_dump 逻辑备份**，不是 PostgreSQL 数据目录。恢复：

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -in teachtrack.dump.enc \
  -out teachtrack.dump -pass file:backend/.cloud-backup-key
pg_restore --clean --if-exists --no-owner -d <target-db> teachtrack.dump
```

### 为什么不能把 PostgreSQL 数据目录放百度网盘

PostgreSQL 运行中的数据目录需要：

- POSIX 文件锁与多进程并发写
- `fsync`/`fdatasync` 持久化保证
- 原子 rename、mmap、随机写
- WAL 与控制文件的严格顺序

百度网盘是对象/文件同步，不是块设备或本地文件系统；同步冲突、延迟、缓存和锁缺失会损坏 `pg_control`、WAL 和索引。**不要把 `pgdata` 指到网盘同步目录，也不要让两个进程同时写。**

### Cloudflare Tunnel（远程 HTTPS）

- 临时测试：`backend/ops/tunnel-quick.sh` 或 `.ps1`，输出 `https://<random>.trycloudflare.com`。
- 永久：`backend/ops/tunnel-named.md`，需要 Cloudflare 账号 + 域名。
- Quick Tunnel 没有访问控制，后端仍靠 Bearer Token；不要长期暴露。
