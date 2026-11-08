# DEVELOPMENT_PROGRESS

最后更新：2026-11-08

## 当前状态

- 远程基线：`main` / `c77ea16`；已整合另一台电脑的题库 UI、导航、真机验收和 ChatGPT 同步功能
- 本机服务器：Corretto Java 21.0.13 + PostgreSQL 16.10；Java API 与固定 Named Tunnel 运行中
- 数据库：`D:\SoftWare\pgdata-teachtrack`，仅监听 `127.0.0.1:55432`；Flyway V001–V017 已执行
- 题库主库：**55,617 道唯一题**（26,270 自动通过 / 29,347 待审核）；0 规范化重复、0 空题干、0 导入失败
- 当前实际课程数据：0 学生、0 课堂；题库导入报告：`docs/ai-practice/QUESTION_BANK_IMPORT_REPORT.md`
- 文件存储：`backend/data/storage`；Owner Token 与文件签名密钥位于 gitignored 的 `backend/.env`，未写入 APK
- 固定公网入口：`https://teachtrack.leslie-kaito.ccwu.cc`；健康检查 200，有效 Token 200，错误 Token 401
- 隧道配置：`D:\SoftWare\cloudflared\config.yml`，仅转发至本机 `127.0.0.1:8080`；凭据不进入项目仓库
- 隐藏自启：`TeachTrackBackend` 与 `TeachTrackTunnel` 已注册并通过重启验证；使用 `run-hidden.vbs` 启动，不显示终端窗口，不使用启动文件夹
- 接电源时原电源策略已是不自动睡眠；仍需保持电脑开机、联网并登录 Windows
- Android：`3.0.0-alpha.6`；固定地址 APK 与 androidTest APK 构建通过；另一台电脑已完成题库和窄屏导航真机验收
- 后端：长题干规范化键改为 SHA-256，规范化题干唯一索引已启用；题图、批量审核和删除接口已验证
- 备份：PostgreSQL dump 与独立恢复演练已通过；当前没有自动异地备份
- 题库开发计划：`docs/ai-practice/QUESTION_BANK_ROADMAP.md`

## 本轮开发（2026-11-08）

- 题库：补齐 App 已调用但后端缺失的题图接口；支持筛选、批量通过/拒绝/软删除
- 导入：文本导入新增标准 CSV 模式；新增 `scripts/import_docx.py`，支持 DOCX 题号切分、表格/段落文本和内嵌图片上传
- 练习：App 可逐题填写答案、提交并自动判分，显示掌握度和导出 PDF/DOCX
- 知识点：题目编辑器内可直接新建知识点并选中
- 导航：窄屏手机改为 5 个底部入口，设置/Agent/题库收进“更多”；宽屏保持 7 个入口
- 更新：检查更新改为 `LeslieCoding/TeachTrack`，修复预发布版本比较，支持下载 APK 并交给系统安装器
- 发布：新增 `docs/UPDATE_GUIDE.md`，版本号可通过 Gradle 参数覆盖
- ChatGPT 共享：新增 `scripts/sync_chatgpt_share.py` 与每日同步任务，导入 13 名学生、149 份课后反馈、1 份期中家长会/试卷分析
- 本地与云端：35 名学生、180 条课程；OPPO Pad 本地 Room 与 PostgreSQL 已核对一致

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

## 历史验证数字（2026-11-06）

- Cloudflare：TypeScript 类型检查与 25 项测试曾通过；代码和云端资源现已删除，这些结果仅代表历史状态。
- Android：**52 tests / 0 failures / 0 errors / 0 skipped**；默认包名的 `:app:testDebugUnitTest` 与 `:app:assembleDebug` 曾通过。
- OnePlus PLK110（Android 17）：`AgentNewActionsInstrumentedTest` **17/17 通过**，其中 `exam.analysis.text.fill` 直接通过 `TeachTrackOffloadHandler` 验证；这不等同于完整 PRoot 沙箱或 UI 点击验收。
- `QuestionBankComposeInstrumentedTest` 曾覆盖题库 UI 场景，但执行期间 ADB 断开，不能记为通过。

## 未完成 / UNVERIFIED

| 项 | 原因 |
|---|---|
| 固定地址 APK 真机复测 | 另一台电脑已完成题库 UI 和沙箱验收；新固定地址构建仍需复测 |
| 题库质量 | 29,347 道待审核；41,802 道含图待补；79 个扫描 PDF 未 OCR；49 道混入解析、354 道超长题干已降为待审 |
| P3 软删除恢复 | 筛选、批量审核和软删除已实现；恢复入口尚未实现 |
| 真实 AI Provider 调用 | 无 API Key；`AiProvider` 默认 Mock，不伪造生成 |
| AI 原创题 / 变式题 | Provider 未配置；当前只从已审核题库组卷 |
| MinIO/S3 真实联调 | 当前使用本机文件存储，无 S3 配置 |
| 真实 `.ttseed.json` 导入 | 文件仍缺失 |
| 异地备份、监控与告警 | 只有本机备份，尚未配置自动异地备份和监控 |

## 下一项任务：题库

详见 `docs/ai-practice/QUESTION_BANK_ROADMAP.md`。

1. 连接手机或平板，验收固定地址 APK 的服务器设置、题库 UI 和 PRoot。
2. 抽查 55,617 道题的质量，优先处理 29,347 道待审核和 49 道题干混入解析的题。
3. P3：完整筛选、批量审核、批量软删除、知识点管理。
4. P4：题图上传与完整组卷（当前 41,802 道含图待补）。
5. 对 79 个纯扫描 PDF 做 OCR，再增量导入。
6. P5：真实 AI Provider、变式题、质量门。

第一批目标 300 道已审核题已远超；下一步是从现有审核通过的题中完成 1 份练习，并继续提高数据质量。

## 检查点

- Java 后端：`JAVA_HOME=D:\SoftWare\jdk-21 ./gradlew.bat -p backend test`
- Android：`JAVA_HOME=D:/SoftWare/jdk-17 ANDROID_HOME=D:/SoftWare/Android/Sdk ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
- 本机服务：`powershell.exe -ExecutionPolicy Bypass -File backend/ops/run-local.ps1`
- 题库导入：`python scripts/import_question_bank.py --parse-only` / `--import-only --import`
- 固定公网地址：`https://teachtrack.leslie-kaito.ccwu.cc`；APK 预填参数为 `-PteachTrackServerUrl=https://teachtrack.leslie-kaito.ccwu.cc`
- Android SDK：`D:\SoftWare\Android\Sdk`（已写入本机 gitignored `local.properties`）
- DB：`D:\SoftWare\pgdata-teachtrack`，端口 55432，仅监听本机

### 另一台电脑：真机验收

安装 `app/build/outputs/apk/debug/app-debug.apk`，或在另一台电脑拉取源码后重新构建。APK 只预填地址，不包含 Owner Token；Token 由教师从本机 `backend/.env` 获取后在 App 中输入。若设备已有其他签名版本，使用独立包名：

```powershell
./gradlew.bat "-PteachTrackApplicationId=com.leslie.teachtrack.deviceTest" "-PteachTrackServerUrl=<当前HTTPS地址>" :app:installDebug :app:installDebugAndroidTest
```

`QuestionBankComposeInstrumentedTest` 的写入用例只接受 loopback 隔离后端；不要把它直接指向公网主库。完整 UI、PRoot 和 50 题导入仍待真机执行。

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

## 题库模块按钮级验收（2026-11-08）

- 题库所有界面的按钮补齐到 14 项 Compose 仪器测试，OnePlus 6 上连续两轮 `OK (14 tests)`。
- 后端 `25/25`、Android JVM `61/61` 通过；生产后端已用新 jar 重启，health 200，公网隧道 200。
- 生产库仍是 1 道草稿题；精准练习在 0 道已审核题时给出可操作提示并禁用生成。

## 题库规范化修复与分页（2026-11-08）

- 拉取 `1f4baca`（另一台电脑的题库全量导入 + 固定隧道）后回归，修复两个测试暴露的真实缺陷：
  - 规范化去重把数字全部抹平，导致「不同数值的题」被误判重复、批量导入编号题全军覆没；现改为只剥离开头题号，数字保留，后端/V018/导入脚本同步。
  - 题库列表固定 100 条无法翻页；新增 `searchPage(limit/offset/total)` 与「加载更多（已显示 X / 共 Y）」。
- Flyway 到 V018；本机生产后端已重启并应用，health 200。
- 验证：后端 27/27、Android JVM 61/61、OnePlus 6 仪器测试 15/15 连续两轮。
- 版本号 3.0.0-alpha.7（300007）；Release 已发布并带 APK，平板覆盖安装后检查更新显示已是最新。题库去重不再抹平数字，题库列表分页。
