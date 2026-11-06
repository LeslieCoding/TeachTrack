# BLOCKERS

真实阻塞项、原因、可替代方案、待授权事项。

## B-01 `.ttseed.json` 缺失（影响 M03 数据验收）

- 现象：`TeachTrack_当前学生完整数据_含上课记录_2026-11-01.ttseed.json` 在项目目录、`D:\SoftWare`、`D:\Leslie` 常见位置和 git 历史均未找到。
- 影响：无法验证 14 学生 / 13 ACTIVE / 1 PAUSED / 91 条课堂记录。
- 已做：M01 如实报告；不使用仓库 29 人 seed 或设备 49 人记录冒充。
- 替代：迁移工具先支持 `teacher_seed_v1.json`、Room `TeachTrack Backup` JSON、Workspace JSONL；真实文件到位后只做适配与验收。
- 需要：用户提供 `.ttseed.json`，或授权从设备导出备份。

## B-02 无 Docker Desktop（影响开发环境形态）

- 现象：`docker` 命令不存在。
- 影响：不能用 Docker Compose 起 PostgreSQL/MinIO。
- 替代：使用 PostgreSQL 16 Windows 便携版（`D:\SoftWare\pgsql-16`，端口 55432）；MinIO 缺失时先用本地私有存储实现，S3 适配器代码保留并标记 `UNVERIFIED`。
- 需要：无（不阻塞 M02）；生产部署前再确认容器环境。

## B-03 无 MinIO / S3 凭证（影响对象存储联调）

- 现象：无对象存储账号与密钥；MinIO 下载端点未确认可达。
- 影响：S3 适配器无法做真实端到端验证。
- 替代：实现 `LocalFsStorageProvider`（私有目录 + HMAC 签名 URL），满足 M02 本地验收；S3 适配器用单元测试 + 配置说明，标记 `UNVERIFIED`。
- 需要：后续提供 R2/OSS/COS/MinIO 端点与密钥（不得写入仓库）。

## B-04 无真实 AI Provider Key（影响 M05/M06 真实调用）

- 现象：未配置云端模型密钥。
- 影响：AI 分析/生成只能走 Mock 与本地适配器。
- 替代：`AiProvider` 接口 + Mock 实现 + 本地 OpenAI 兼容端点支持；真实调用标记 `UNVERIFIED`。
- 需要：后续提供密钥（环境变量）。

## B-05 Android 真机不可用（影响仪器测试）

- 现象：一加 Ace 5 已硬砖；OPPO Pad 是否连接未确认。
- 影响：新增 Android 功能只能做 JVM 测试与构建验证，真机 UI 验收延后。
- 替代：`assembleDebug` + JVM 单测 + 现有仪器测试代码审查；不伪称真机通过。
- 需要：可用设备或模拟器。

## B-06 Android 试卷分析页 / 精准练习页未完成

- 现象：后端 API 与测试已完成，Android 仅有题库页与同步底座。
- 影响：教师目前需通过 REST/MCP 使用试卷分析与练习生成，不能在 App 内完成全部流程。
- 替代：API 可用；题型/参数已固定，补 UI 不需要改后端。
- 需要：继续开发 Android 页面（无需外部凭证）。

## B-07 AI 原创题未启用

- 现象：`AiProvider` 默认 `MockAiProvider`，生成返回空；无真实 Key。
- 影响：题库不足时返回 `INSUFFICIENT_QUESTIONS`，不会伪造题目。
- 替代：已审核题库组卷可完整使用。
- 需要：OpenAI/Kimi/Claude/本地模型端点与 Key（只放环境变量）。

## B-08 真实云 PostgreSQL 实例未创建

- 现象：当前 `teachtrack` 库在 `127.0.0.1:55432`，是本地开发库；`/api/v1/system/database` 明确返回 `LOCAL_DEV`。
- 影响：不能满足“云端数据库”的生产要求。
- 已做：`application-cloud.yml`、`ops/migrate-to-cloud.sh`、`ops/cloud-setup.md`、数据库模式自检接口；迁移脚本已用本地第二库模拟目标验证。
- 阻塞原因：Supabase/Neon/RDS 等项目创建需要账号登录（邮箱验证/验证码），部分涉及计费；没有现成凭证，AI 不得擅自注册或绑定支付方式。
- 最小所需输入：云 PostgreSQL 连接串，或明确授权使用的云账号。

## B-09 云备份已建，云数据库仍未建

- 已做：私有 GitHub 仓库 `LeslieCoding/teachtrack-cloud`，加密备份已上传并可校验。
- 未做：可连接的云 PostgreSQL 实例；Supabase/Neon 需要账号登录。
- 区别：GitHub 仓库是云存储/灾备，不是数据库引擎，不能替代 `TEACHTRACK_DB_URL`。

## B-10 百度网盘只差一次授权

- 代码已实现 `baidu-pan` 适配器，`bypy` 已安装并验证命令可用。
- 真实上传/下载需要一次 `bypy info` 授权码：浏览器登录百度账号并同意授权。
- 百度网盘不是数据库引擎，不能把 `TEACHTRACK_DB_URL` 指向它。

## B-10 已解决：百度网盘授权与真实联调

- 授权已完成：`bypy info` 返回 Quota 70 GB。
- 真实上传/下载通过；后端 `baidu` profile 可用。
- 仍不支持：百度网盘不能作为 PostgreSQL 数据库引擎。
