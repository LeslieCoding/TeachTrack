# TEST_RESULTS

## Windows 本机服务器（2026-11-07）

```text
Java: Corretto 21.0.13
PostgreSQL: 16.10，仅监听 127.0.0.1:55432
Flyway: V001–V014
Backend: 23 tests / 0 failures / 0 errors / 0 skipped
Android: 52 tests / 0 failures / 0 errors / 0 skipped
:app:assembleDebug / :app:assembleDebugAndroidTest → BUILD SUCCESSFUL
本机与 Quick Tunnel HTTPS /actuator/health → UP
有效 Owner Token → database/storage/students/questions/subjects API 200
无效 Token → 401
PostgreSQL dump → 独立库恢复 → 行数一致
```

主库是新库：0 学生、0 课堂、0 题、4 个预置学科。旧业务数据未恢复。当前没有 Android 设备连接；新增服务器设置 Compose 用例和其他真机用例只完成编译，未执行。Quick Tunnel 地址重启会变化，不能视为固定生产入口。隐藏自启安装脚本仅通过语法检查；PowerShell 和 schtasks 注册均被系统权限拒绝，未创建任务。

## Android

```text
JAVA_HOME=D:\SoftWare\jdk-17
:app:testDebugUnitTest → 38 tests / 0 failures / 0 errors / 0 skipped
:app:assembleDebug → BUILD SUCCESSFUL
```

覆盖：原有 35 用例 + CloudSyncClient 契约 3 用例。

## 后端

```text
JAVA_HOME=D:\SoftWare\jdk-21
./gradlew.bat -p backend test
→ 21 tests / 0 failures / 0 errors / 0 skipped
```

分项：

| 测试 | 覆盖 |
|---|---|
| BackendIntegrationTest | Flyway 幂等、health、401、Token、上传/下载/篡改 |
| LocalFsStorageProviderTest | 读写删、路径穿越、签名过期、篡改 |
| MigrationServiceTest | seed 幂等、dry-run 回滚、Backup JSON、ttseed |
| QuestionApiTest | CRUD、重复 409、搜索、版本冲突 409、审核、软删除 |
| SyncApiTest | push、幂等重放、409 冲突、force、changes 游标 |
| ExamApiTest | 导入、逐题分析、历史关联、教师修订、审核入库 |
| PracticeApiTest | 组卷、审批、提交、掌握度、PDF/DOCX 字节 |
| McpApiTest | initialize、tools/list、只读工具、scope、审计、401 |

## 实机/运行冒烟

```text
GET /actuator/health → 200 {"status":"UP"}
POST /mcp tools/list → 11 tools
seed 迁移 dry-run → students=29 courses=30 scores=14
seed 正式导入两次 → students=29，scores=14 不变
PATCH /api/v1/questions/{id} → 200，版本递增
备份 → 恢复 → students=1 course_sessions=1
```

## UNVERIFIED

- Android 真机/模拟器 UI（`adb devices` 无设备）
- 两独立 Android 客户端同步
- 真实 AI Provider / AI 原创题
- MinIO/S3 真实联调
- 真实 `.ttseed.json`
- 生产部署与监控

## 云端数据库准备（2026-11-05）

```text
GET /api/v1/system/database
→ host=127.0.0.1, port=55432, database=teachtrack, ssl=false, mode=LOCAL_DEV
```

迁移脚本演练（本地第二库模拟云端目标，验证流程而非云厂商）：

```text
pg_dump 本地 teachtrack → pg_restore teachtrack_cloud_sim
表数量：50
Flyway：V014 exam question stem / V013 sync operations / V012 question updated by
行数：students 30=30, course_sessions 31=31, questions 1=1
```

结论：切换云库只差真实连接串；脚本未在真实云厂商实例上验证（UNVERIFIED）。

## cloud profile 启动验证（2026-11-05）

```text
--spring.profiles.active=cloud + 本地连接串
/actuator/health → 200
/api/v1/system/database → host=127.0.0.1, mode=LOCAL_DEV, ssl=false
```

结论：cloud profile 本身可启动；因为连接串仍是本地，模式正确显示 LOCAL_DEV。真实云库需替换 `TEACHTRACK_DB_URL` 后再验一次 `mode=CLOUD_OR_REMOTE`。

## 免费云备份实测（2026-11-05）

```text
gh repo create teachtrack-cloud --private → https://github.com/LeslieCoding/teachtrack-cloud
cloud-backup.sh → dumps/20261105-205304/
GitHub Contents API 上传成功
manifest: students=30, courseSessions=31, questions=1
```

说明：GitHub HTTPS git clone/push 在本机直连超时，改用 `gh api` Contents API 上传成功；备份为加密文件，仓库内无明文学生数据。

## 百度网盘适配器（桩验证）

```text
BaiduPanStorageProviderTest → put/get/exists/delete/presign 全部通过
```

用本地 `bypy` 桩程序模拟 upload/download/mkdir/delete，验证了命令与路径映射。真实百度账号未授权，标记 `UNVERIFIED`。

## 百度网盘适配器测试

```text
BaiduPanStorageProviderTest → 通过
bypy --version → v1.8.9（已加入 PATH）
```

测试用本地 bypy 桩程序模拟百度命令；真实百度账号未授权，真实网盘 `UNVERIFIED`。

## 百度网盘真实联调（2026-11-05）

```text
bypy info → Quota: 70.000GB, Used: 0.000B（授权已生效）
bypy upload → /apps/bypy/teachtrack/test.txt（24 B）
bypy download → 内容一致
```

后端 `baidu` profile：

```text
GET /actuator/health → 200
GET /api/v1/system/storage → {"provider":"baidu-pan"}
POST /api/v1/assets/upload-url → PUT 204
GET 签名下载 → 200，内容一致
网盘列出：/apps/bypy/teachtrack/tests/unassigned/...-baidu-profile.txt
```

结论：百度网盘文件存储真实上传/下载通过；数据库仍为本地 PostgreSQL，百度仅存文件。

## 百度网盘数据库备份真实上传（2026-11-05）

```text
baidu-backup.sh → teachtrack/backups/20261106-011645/
网盘文件：
  teachtrack.dump.enc            180,736 B
  teachtrack.dump.enc.sha256      65 B
  manifest.json                  384 B
```

备份为 AES-256-CBC/PBKDF2 加密的 `pg_dump`，不是 PostgreSQL 数据目录。真实上传成功。

## 局域网访问

```text
http://192.168.5.3:8080/actuator/health → 200
http://192.168.5.3:8080/api/v1/system/storage → {"provider":"baidu-pan"}
防火墙：仅 192.168.5.0/24 可入站 8080
PostgreSQL：127.0.0.1:55432（不对局域网开放）
```

本机经 LAN IP 验证通过；第二台设备实测需设备在场（UNVERIFIED）。

## Cloudflare Quick Tunnel（2026-11-05）

```text
cloudflared 2026.10.7
Quick Tunnel → https://<random>.trycloudflare.com
GET /actuator/health → 200（HTTPS）
GET /api/v1/system/storage (Bearer) → {"provider":"baidu-pan"}
POST /mcp tools/list → 11 tools
```

测试后已停止隧道，未保留公网入口。Quick Tunnel URL 每次重启变化且无 Cloudflare Access，仅适合测试；永久使用需 Named Tunnel + 域名。

## Cloudflare Named Tunnel（2026-11-05）

```text
域名：https://teachtrack.leslieholmes.ccwu.cc
Tunnel：teachtrack（a1a25351-0179-436a-aadc-675d9d0c7356）
GET /actuator/health → 200（HTTPS）
GET /api/v1/system/storage（新 Token）→ {"provider":"baidu-pan"}
旧 Token → 401
隐藏登录任务 TeachTrackTunnel → Running
```

Token 已轮换，明文只保存在 `backend/.cloud-token`（gitignore）。Cloudflare 服务未使用（曾尝试后删除），改用隐藏登录任务。

## 常驻任务验证（2026-11-05）

```text
TeachTrackBackend Running（javaw + bootJar + baidu profile）
TeachTrackTunnel  Running
http://127.0.0.1:8080/actuator/health → 200
https://teachtrack.leslieholmes.ccwu.cc/actuator/health → 200
```

`bootRun` 后台任务 30 分钟超时问题已由计划任务规避。

## OPPO Pad 真机验证（2026-11-05）

设备：OPPO Pad `2d64f765`（OPD2101，2560x1600）

```text
APK 安装成功（debug）
启动 → 首页正常
题库页 → 云端连接成功（https://teachtrack.leslieholmes.ccwu.cc）
题库列表加载：PATCH smoke test unique 20261105 v2
同步页 → 从云端拉取：已拉取学生 30、课堂 31
学生页 → 显示云端学生（武驰、永进、王晨然等）
试卷分析页 → 选择学生、初中科学、导入试卷成功（显示“已导入”）
```

未完成：试卷文本输入后的“逐题分析”按钮在真机键盘/坐标下未点中，UI 分析流程未完整走通；后端 ExamApiTest 已覆盖分析/审核。Token 通过 debug-only intent 注入，Release 不生效。

## C→D 迁移与 Oracle 准备（2026-11-05）

```text
cloudflared 配置/C 盘目录删除 → D:\SoftWare\cloudflared
隐藏 WMI VBS 启动隧道 → cloudflared PID 存在，HTTPS health 200
bypy 包 → D:\SoftWare\Python311\Lib\site-packages
bypy 配置 → D:\SoftWare\bypy-home\.bypy
百度网盘 API 上传/下载复测 → 200，内容一致
```

Oracle Always Free：本机无账号/VM；SSH 公钥已生成，等待 VM IP。

## Cloudflare 清理复核（2026-11-07）

- Worker `teachtrack-api`、D1 `teachtrack`、TeachTrack KV、Tunnel `teachtrack` 及其 DNS CNAME 均已删除；R2 无桶。
- TeachTrack 用量预警与 Tunnel 自动创建的 16 个 API 令牌已全部删除，控制台显示“无 API 令牌”。
- 百度网盘 D1 备份已删除；原始题库、教材和 PostgreSQL 备份未改动。
- 本地 Cloudflare 实现、D1/Tunnel 工具、配置、凭据、日志、缓存和 `cloudflared.exe` 已删除；App 不再预填旧域名。
- Android `:app:testDebugUnitTest :app:assembleDebug` 已重新通过。
- `TeachTrackTunnel` 计划任务条目已经管理员授权删除；无相关进程，端口 8788 未监听。

## 本机服务器与题库接口（2026-11-08）

- PostgreSQL 实际数据：30 学生、31 课堂、1 题；未清库
- 本机与公网 health 200，无 Token 401，有效 Owner Token 200
- 真实接口：新建题 → 上传 PNG 题图 → 列表 → 签名下载 200 → 批量通过 → 批量删除，全部通过，临时数据已清理
- 后端全量测试通过，覆盖题图、批量审核/删除、试卷分析、练习提交与导出
- DOCX 导入脚本用带内嵌图片的样例实测：解析 1 题、上传 1 图、签名下载 200，完成后删除测试题
- 自启任务 `TeachTrackBackend`、`TeachTrackTunnel` 已安装，隐藏、Ready
- Android 单元测试与 APK/androidTest 构建通过
- OnePlus 6（Android 11，窄屏）仪器测试：`TeachTrackComposeInstrumentedTest` 2/2 通过，验证 5 项底部导航和“更多”入口
- App 更新检查：仓库地址改为 `LeslieCoding/TeachTrack`，预发布版本比较测试通过
- OPPO Pad 真机：`3.0.0-alpha.3` 点击“检查更新”后显示 `3.0.0-alpha.3 → 3.0.0-alpha.4`、更新说明和下载按钮；下载进度与失败重试界面可见
- OnePlus 6 仪器测试：数据库 + PRoot 沙箱 `OK (36 tests)`，窄屏导航 `OK (2 tests)`
- OPPO Pad `alpha.4`：连接新公网地址，题库加载、学科/年级/题型/状态/难度筛选、批量选择界面均正常
- OPPO Pad 更新检查：`alpha.3 → alpha.4`、`alpha.4 → alpha.5` 均正确显示新版本；`alpha.5` 显示“已经是最新版本”
- 最终测试计数：Android JVM `61/61`，后端 `25/25`，OnePlus 6 仪器测试 `36/36`，窄屏导航 `2/2`
- OnePlus 6 `alpha.6`：从“更多”进入题库可连接本机服务器；窄屏题库标题和工具栏改为两行，不再竖排挤压
- 沙箱仪器测试曾通过后台同步写入 1 名“沙箱验收学生”；已删除该学生及同步游标，生产库恢复为 30 学生 / 31 课堂 / 1 题，随后清空 OnePlus 6 测试应用数据
- ChatGPT 共享导入：解析 996 条消息，生成 149 份课后反馈 + 1 份期中家长会/试卷分析，覆盖 13 名学生
- 云端导入后：35 名学生、180 条课程、15 条成绩、3 条沟通；OPPO Pad 本地 Room 核对同样为 35/180/15/3
- 梁淑涵期中科学：102/160，得分率 63.75%，失分 58；电学、力学与能量、化学综合题已分类记录
- 每日 06:17 同步任务 `TeachTrackChatGPTSync` 已注册，手动执行同步脚本实测成功

## 题库模块按钮级验收（2026-11-08）

隔离环境：后端 `127.0.0.1:18080`（jar 含最新 PracticeService），库 `teachtrack_uitest`，设备 OnePlus 6 `804b750b`（Android 11，窄屏）。

```text
QuestionBankComposeInstrumentedTest：两轮连续跑，均为 OK (14 tests)
覆盖：服务器设置、Agent 文本注入、录题（知识点新建/选项增删/保存并审核通过）、
      题库筛选（学科/年级/题型/审核状态/难度 + 对话框清除）、全选/取消全选、
      批量通过/拒绝/删除确认、详情通过/拒绝/关闭/编辑、题图上传、搜索、
      CSV 导入自动通过、纯文本 50 题导入与重复拦截、草稿导入来源标记、
      试卷导入/重新导入/逐题分析/审核入库、精准练习生成/审核/提交自动判分/
      学生版与教师版 PDF 与 DOCX 四项导出
Android JVM：61 tests / 0 failures
后端：25 tests / 0 failures
```

- 修复：无已审核题时，精准练习分别提示“该学科还没有已审核题目”（0 道）和“可用已审核题目 N 道”（有题但筛选不匹配），并禁用生成、提供“去题库录入 / 审核题目”入口。
- 修复：录题主按钮由“提交审核”改为“保存并审核通过”，导入默认“完整题直接通过”，含图待补题仍强制草稿。
- 修复：筛选对话框“清除”原先只清空弹窗字段、不生效也不关闭，现改为立即清除并刷新列表。
- 测试基建：新增 `filter-subject-*`、`filter-grade-*` 测试标记；相似题二次确认（“仍要保存通过/仍要存草稿”）在测试中自动确认。
- 生产验证（本机 8080 + Quick Tunnel，OPPO Pad 2d64f765）：题库加载正常；精准练习显示“该学科还没有已审核题目，先在题库录入完整题目并保存通过。”，同时出现“去题库录入 / 审核题目”和禁用的“生成练习草稿”；录题页显示“保存并审核通过”，空表单提交提示“题干不能为空”，未写入数据。
