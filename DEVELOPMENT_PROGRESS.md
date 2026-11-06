# DEVELOPMENT_PROGRESS

最后更新：2026-11-06

## 当前生产状态

- 云端后端：Cloudflare Workers + D1，入口 `https://teachtrack.leslieholmes.ccwu.cc`
- D1 主区域：APAC
- 数据：30 学生、31 课堂、177 知识点、1 道题
- 本机 Java/PostgreSQL 后端与 Cloudflare Tunnel 已停止，云端仍可访问
- Android：OPPO Pad 已安装本轮最新 Debug APK，成功读取云端学生数据
- 2026-11-06 OPPO Pad 真机验收：P1 录题（重复题确认、保存草稿、刷新可见）通过；P2 文本批量导入新增 2、失败 0，同批再次导入 2/2 识别为完全重复并跳过；测试题已从正式 D1 删除
- OnePlus PLK110 使用独立包名 `com.leslie.teachtrack.deviceTest`，未覆盖原签名应用或清空其数据
- 题库开发计划：`docs/ai-practice/QUESTION_BANK_ROADMAP.md`

## 本轮本地开发（2026-11-06）

- Agent 新增 `exam.analysis.text.fill`：从 Agent 页替换/追加题库 → 试卷分析文本并自动打开该页；不自动分析或审核入库。Action Registry 现为 69 项，内置 teachtrack skill 升至 1.7.3。
- P1 录题与编辑：完整字段、选项、答案解析、知识点多选、草稿/送审、查重提醒、409 冲突刷新。
- P2 文本批量导入：题号/空行切分、题型与难度识别、知识点匹配、逐条错误/查重预览、重复跳过、含图待补标记。
- 题库扫描报告：`docs/ai-practice/QUESTION_BANK_SCAN_REPORT.md`；可复跑扫描器：`scripts/scan_question_bank.py`。

## 历史状态（2026-11-05）

M01–M07 的后端能力已实现并测试；Android 侧完成题库页与同步底座，试卷/练习页面未完成。

## 已完成并验证

| 里程碑 | 内容 | 证据 |
|---|---|---|
| M01 | 架构审计、差距、目标架构、DB、迁移、路线图 | `docs/ai-practice/`；Android 基线 35/35 |
| M02 | PostgreSQL 16、Flyway V001–V014、Token、私有对象存储、备份恢复 | 后端测试；backup/restore 演练 |
| M03 | 迁移（seed/backup/ttseed）、题库 CRUD/版本/审核/搜索/去重/附件 | seed 两次 29/30/14 不变；题库测试 |
| M04 | sync_changes、push、幂等、409 冲突、Room v8、WorkManager、客户端 JSON | 后端同步测试；Android 38/38 |
| M05 | 试卷导入、逐题分析、历史课堂关联、教师修订、审核入库 | `ExamApiTest` |
| M06 | 题库组卷、审批、提交、掌握度公式、PDF/DOCX 导出 | `PracticeApiTest`（含 PDF/DOCX 字节校验） |
| M07 | MCP JSON-RPC、11 工具、Token scope、AI 审计 | `McpApiTest`；实时 `/mcp` 冒烟 |
| 本轮题库 | Agent 填文本、P1 录题/编辑、P2 文本导入、50 题幂等导入 | Android 52/52；Cloudflare 25/25；两个 APK 构建成功 |

## 当前验证数字

- Cloudflare：TypeScript 类型检查通过；**8 个测试文件、25 tests / 0 failures**，含 50 题首次导入与整批重复导入。
- Android：**52 tests / 0 failures / 0 errors / 0 skipped**；默认包名的 `:app:testDebugUnitTest` 与 `:app:assembleDebug` 已再次通过。
- OnePlus PLK110（Android 17）：`AgentNewActionsInstrumentedTest` **17/17 通过**，其中 `exam.analysis.text.fill` 直接通过 `TeachTrackOffloadHandler` 验证；这不等同于完整 PRoot 沙箱或 UI 点击验收。
- 构建产物：`app/build/outputs/apk/debug/app-debug.apk` 和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。
- 新增 `QuestionBankComposeInstrumentedTest` 覆盖真实 PRoot 填文本/手工编辑/追加、录题/查重/409 刷新、50 题 UI 导入/重复批次拦截。Espresso 已升级为 3.7.0 以兼容 Android 17；升级后的测试在首项执行期间 ADB 断开，不能记为通过。

## 未完成 / UNVERIFIED

| 项 | 原因 |
|---|---|
| P1/P2 真机 UI 与隔离 Worker 联调 | PLK110 在 UI 测试首项执行期间断开 ADB；改在另一台电脑连接设备继续 |
| P2 DOCX/CSV、原卷版/解析版配对 | 本轮只实现文本导入；扫描器已提供可复用的 DOCX 提取函数 |
| P3 完整筛选、批量审核、软删除恢复 | 尚未实现 |
| 完整沙箱、Mock Agent、Compose UI 执行 | Agent 动作真机 17/17 已通过，其余已编译但未完成执行；50 题仅验证 App 解析器与 D1 服务端，尚无 UI 端到端通过证据 |
| 真实生产云端网络 | 本轮使用本机隔离 D1，未修改生产题库 |
| 两独立 Android 客户端同步 | 协议与客户端契约已测，Worker 未上机 |
| 真实 AI Provider 调用 | 无 API Key；`AiProvider` 默认 Mock，不伪造生成 |
| AI 原创题 / 变式题 | Provider 未配置；当前只从已审核题库组卷 |
| MinIO/S3/R2 真实联调 | 无云存储凭证；图片题先标记“含图待补” |
| 真实 `.ttseed.json` 导入 | 文件仍缺失 |
| 生产部署、监控、费用告警 | 需要云资源与授权 |

## 下一项任务：题库

详见 `docs/ai-practice/QUESTION_BANK_ROADMAP.md`。

1. 在另一台电脑连接手机或平板，完成 Agent 填文本、P1/P2 UI、完整 PRoot 沙箱与 50 题导入验收。
2. P2 DOCX/CSV：复用扫描器提取逻辑，处理原卷版/解析版配对。
3. P3：完整筛选、批量审核、批量软删除、知识点管理。
4. P4：图片/扫描件上传（需要先解决 R2 或改用其他 S3 存储）。
5. P5：真实 AI Provider、变式题、质量门。

第一批目标：初中科学 300 道已审核题，生成并完成 1 份练习。

## 检查点

- Java 后端：`JAVA_HOME=D:\SoftWare\jdk-21 ./gradlew.bat -p backend test`
- Cloudflare：`PATH=/d/SoftWare/node-22.14.0:$PATH npm.cmd --prefix backend/cloudflare test`
- Android：`JAVA_HOME=D:/SoftWare/jdk-17 ANDROID_HOME=D:/SoftWare/Android/Sdk ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
- Android SDK：`D:\SoftWare\Android\Sdk`（已写入本机 gitignored `local.properties`）
- DB：`D:\SoftWare\pgdata-teachtrack`，端口 55432

### 另一台电脑：隔离真机验收

需要 JDK 17、Node.js 22、Android SDK 36 和 platform-tools；将 `local.properties` 的 `sdk.dir` 指向该电脑的 SDK。设备已安装其他签名的 TeachTrack 时，不要卸载原应用，使用独立包名：

```powershell
./gradlew.bat -PteachTrackApplicationId=com.leslie.teachtrack.deviceTest :app:installDebug :app:installDebugAndroidTest
adb reverse tcp:18787 tcp:8787
cd backend/cloudflare
npm.cmd run migrate:local -- --persist-to ../../app/build/device-test/worker
npm.cmd run dev -- --ip 127.0.0.1 --port 8787 --persist-to ../../app/build/device-test/worker --var BOOTSTRAP_TOKEN:device-test-token
```

上述命令只初始化、使用本机隔离 D1，不要加 `--remote`。Worker 启动后另开终端执行：

```powershell
adb shell am instrument -w -r -e class com.leslie.teachtrack.QuestionBankComposeInstrumentedTest -e teachTrackTestBaseUrl http://127.0.0.1:18787 -e teachTrackTestToken device-test-token com.leslie.teachtrack.deviceTest.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.leslie.teachtrack.AgentNewActionsInstrumentedTest,com.leslie.teachtrack.SandboxEndToEndInstrumentedTest,com.leslie.teachtrack.MockAgentLoopInstrumentedTest com.leslie.teachtrack.deviceTest.test/androidx.test.runner.AndroidJUnitRunner
```

测试用例会按需创建本机测试知识点，并保存/恢复设备原有题库连接设置；测试 Token 仅适用于上述临时 Worker，不是生产凭据。

## 云端数据库（2026-11-05 补充）

- 当前运行库：**本地 PostgreSQL** `127.0.0.1:55432/teachtrack`，`/api/v1/system/database` 返回 `mode=LOCAL_DEV`。
- 已就绪：
  - `application-cloud.yml`（云 profile，连接串全部走环境变量）
  - `ops/migrate-to-cloud.sh`（dump 本地 → restore 云 → 校验表/Flyway/行数）
  - `ops/cloud-setup.md`（Supabase/Neon/RDS 连接串、SSL、安全、备份）
  - `/api/v1/system/database`（证明当前连的是本地还是远程，不返回密码）
- 已用本地第二库模拟目标验证迁移脚本：50 张表、Flyway V014、行数一致。
- **未完成**：真实云库实例。创建 Supabase/Neon 项目需要账号登录/验证码，且可能涉及计费；AI 不能擅自注册或绑定支付方式。
- 需要的最小输入：一个云 PostgreSQL 连接串（host/port/db/user/password + `sslmode=require`），或明确授权使用哪个账号。

- 云端启动器：`backend/ops/run-cloud.ps1` / `run-cloud.sh`（读取 `backend/.env`，不打印密钥）；`cloud` profile 已用本地连接串冒烟启动，health 200。

## 免费云备份已落地（2026-11-05）

- 私有仓库：`https://github.com/LeslieCoding/teachtrack-cloud`
- 已上传首份加密备份：`dumps/20261105-205304/`，manifest 显示 students=30 / courseSessions=31 / questions=1
- 加密：AES-256-CBC/PBKDF2，密钥 `backend/.cloud-backup-key`（已 gitignore，需另存）
- 脚本：`backend/ops/cloud-backup.sh`
- 性质：云存储/灾备，不是可连接的云 PostgreSQL。

## 百度网盘文件存储适配器（2026-11-05）

- 代码：`backend/src/main/java/com/leslie/teachtrack/storage/BaiduPanStorageProvider.java`
- 配置：`TEACHTRACK_STORAGE_PROVIDER=baidu-pan`、`TEACHTRACK_BYPY`、`TEACHTRACK_BAIDU_ROOT`
- 已装 `bypy 1.8.9` 到 PATH；桩测试通过（put/get/exists/delete/presign）
- 真实百度账号未授权：需要一次 `bypy info` 授权码（用户登录百度）
- 限制：百度网盘只能存文件，不能替代 PostgreSQL

## 百度网盘真实联调完成（2026-11-05）

- `bypy info`：Quota 70 GB，授权有效
- 真实上传/下载：`/apps/bypy/teachtrack/...` 内容一致
- 后端 `baidu` profile：`/api/v1/system/storage` 返回 `baidu-pan`，API 上传/下载通过
- 启动：`backend/ops/run-baidu.ps1` 或 `run-baidu.sh`
- 注意：数据库仍是本地 PostgreSQL；百度只存试卷/PDF/备份等文件

## 百度网盘承接文件与备份（2026-11-05）

- 试卷/扫描件/PDF/DOCX 导出：`baidu-pan` ObjectStorageProvider 已真实联调。
- 数据库备份：`backend/ops/baidu-backup.sh` 已真实上传加密 `pg_dump` 到 `/apps/bypy/teachtrack/backups/`。
- 数据库引擎仍为本地 PostgreSQL；百度网盘不能承载运行中的 PostgreSQL 数据目录。

## 局域网访问（2026-11-05）

- 后端监听 `8080`（Spring Boot 默认 `[::]` 双栈），本机 LAN IP `192.168.5.3`。
- Windows 防火墙规则：`TeachTrack Backend 8080 (LAN)`，仅允许 `192.168.5.0/24` 入站 TCP 8080（专用+公用配置）。
- 实测 `http://192.168.5.3:8080/actuator/health` → 200；`/api/v1/system/storage` → `baidu-pan`。
- PostgreSQL 仍只监听 `127.0.0.1:55432`，不对局域网暴露。
- 其他设备同一 Wi-Fi 可访问后端；外网访问尚未配置（需 Tailscale/Cloudflare Tunnel 或云部署）。

## Android 试卷/练习面板 + Cloudflare Tunnel（2026-11-05）

- Android 云端工作台新增三页签：题库 / 试卷分析 / 精准练习；已接线 `ExamPanel`/`PracticePanel`。
- 试卷面板：选学生/学科 → 导入试卷 → 粘贴题目文本 → 逐题分析 → 教师审核入库。
- 练习面板：选学生/学科/模式/题量 → 生成草稿 → 审核 → 导出学生版/教师版 PDF/DOCX。
- Android 测试 38/38，assembleDebug 成功；真机 UI 仍未验证（无设备）。
- Cloudflare Quick Tunnel 实测 HTTPS：health 200、API 200、MCP 11 tools；测试后停止。
- 永久 HTTPS：`backend/ops/tunnel-named.md`（需要 Cloudflare 账号+域名）。

## Named Tunnel 完成（2026-11-05）

- 固定 HTTPS：`https://teachtrack.leslieholmes.ccwu.cc`
- Cloudflare Tunnel：`teachtrack`，DNS CNAME 已加
- 启动：隐藏登录任务 `TeachTrackTunnel`（cloudflared `tunnel run teachtrack`）
- 旧 Token 已失效；新 Token 在 `backend/.cloud-token`，Android 地址已预填
- 后端仍需保持运行；Cloudflare Access 未配置

## 常驻运行方式（2026-11-05）

- `bootRun` 作为 Bash 后台任务会在 30 分钟超时，已改为 **bootJar + 隐藏计划任务**。
- 构建：`JAVA_HOME=D:\SoftWare\jdk-21 ./gradlew.bat -p backend bootJar`
- 任务 `TeachTrackBackend`：AtLogOn Hidden，`javaw.exe -jar backend/build/libs/teachtrack-backend-4.0.0-alpha.1.jar --spring.profiles.active=baidu --logging.file.name=...`
- 任务 `TeachTrackTunnel`：AtLogOn Hidden，`cloudflared --config ... tunnel run teachtrack`
- 日志：`backend/backend.log`
- 实测：本机 health 200、`https://teachtrack.leslieholmes.ccwu.cc` health 200。
- 改代码后需重新 bootJar 并重启 `TeachTrackBackend` 任务。

## Android 云端拉取与同步页（2026-11-05）

- `CloudPullService`：从 `/students` + `/students/{id}/sessions` 一次性拉取学生与课堂，UPSERT 到 Room。
- 云端工作台新增“同步”页：待同步、未解决冲突、立即同步、从云端拉取学生/课堂。
- Android 38/38，Debug APK 62 MB 构建通过；真机 UI 仍未验证。

## OPPO Pad 真机验证（2026-11-05）

- 2d64f765 安装并启动 APK；云端 HTTPS 连接、题库列表、同步拉取 30 学生/31 课堂、学生页、试卷导入均通过。
- 逐题分析 UI 按钮未在真机点击成功（键盘/坐标问题），后端分析测试已通过。
- Token 注入仅 BuildConfig.DEBUG；Release 无此入口。

## C 盘清理与 Oracle 准备（2026-11-05）

- Cloudflared 配置/证书/隧道凭据已迁到 `D:\SoftWare\cloudflared`；任务改为隐藏 WMI VBS（`run-hidden.vbs`），日志 `D:\SoftWare\cloudflared\tunnel.log`；`C:\Users\Leslie\.cloudflared` 已删除。
- `bypy` 包迁到 `D:\SoftWare\Python311\Lib\site-packages`，配置迁到 `D:\SoftWare\bypy-home\.bypy`；Baidu 上传/下载复测通过。
- Oracle Always Free 部署说明：`backend/ops/oracle/README.md`；SSH 密钥 `D:\SoftWare\oracle\ssh\teachtrack_ed25519`。
- 等待 Oracle VM 公网 IP；本机没有 Oracle 账号/VM。
