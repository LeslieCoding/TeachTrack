# ChatGPT 共享对话同步

## 共享来源

- 分享 ID：`6aef67dc-9a24-83e8-a2fe-b6603242438e`
- 标题：`课后反馈`
- 数据接口：`https://chatgpt.com/backend-api/share/<share-id>`
- 分享必须保持“公开”；链接失效或改为私密后同步会失败并写入错误状态文件

## 同步内容

`scripts/sync_chatgpt_share.py` 从助手生成的成品反馈中提取：

- 学生姓名、短称呼和日期
- 本节课内容、学生表现、下节课计划、课后作业
- 同一学生同一天只保留最终修订
- 期中试卷分析、实际分数、家长会沟通和后续安排

生成文件均在 `backend/data/imports/`，不会提交到 Git：

- `chatgpt-share-seed.json`：可直接导入 App 或后端的历史种子
- `chatgpt-share-summary.json`：解析数量、错误和来源信息

当前提取结果：13 名学生、149 份课后反馈、1 份期中家长会/试卷分析。

## 自动同步

`backend/ops/sync-chatgpt-share.ps1` 会：

1. 确保本机 TeachTrack 后端健康；未运行时启动 `TeachTrackBackend`
2. 通过代理或直连抓取分享接口
3. 生成并合并种子
4. 调用 `/api/v1/admin/migrations` 导入云端
5. 写入 `backend/data/runtime/chatgpt-share-sync.json`

`backend/ops/install-autostart.ps1` 注册每天 06:17 执行的隐藏任务 `TeachTrackChatGPTSync`。

手动执行：

```powershell
powershell.exe -ExecutionPolicy Bypass -File backend/ops/sync-chatgpt-share.ps1
```

## 同步到平板本地

云端会自动更新。平板上打开：

设置 → 历史学生数据库 Seed V1.0 → 选择 JSON 并导入

选择 `/sdcard/Download/TeachTrack-chatgpt-seed.json`。重复导入是幂等的，不会清空现有数据。

## 数据边界

- 只导入教师在对话中已经生成并确认的反馈，不把未完成的草稿或推测写成事实
- 原始聊天和分享链接不在仓库内保存
- 导入前仍应保留 PostgreSQL 和 Android Room 备份
- 试卷分析中“目标提分区间”不是已核实的失分或保证提分
