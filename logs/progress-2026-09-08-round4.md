# TeachTrack 开发记录：2026-09-08 / Round 4

## 任务

- 执行范围：Prompt 02 数据层、Prompt 05 知识点历史、Prompt 08 备份恢复、Prompt 09 质量验证。
- 真机范围：仅 OnePlus PKG110，serial `92f73a16`；按用户要求不操作 OPPO Pad。

## 本轮开发

1. 备份恢复流程改为：选择文件 → 校验 `TeachTrack Backup` 格式 → 显示导出时间和各类记录数量 → 二次确认 → 按 ID 合并。
2. Room 增加 `StudentKnowledgeEventEntity`，记录学生每次知识点掌握状态变化；新增 schema v2→v3 migration，避免升级清库。
3. 备份 JSON 增加知识点历史事件导入/导出，保证长期成长档案可以完整迁移。
4. 创建并同步 `AI_SPEC` 状态文件，记录当前阶段、设备切换、风险和回滚点。

## 验证命令与结果

- `testDebugUnitTest assembleDebug`：PASS。
- `testDebugUnitTest assembleDebug assembleRelease`：PASS，`BUILD SUCCESSFUL`。
- 通过 ADB push + `pm install -r -d` 安装最新 Debug APK 到 OnePlus：PASS。
- OnePlus 启动首页截图：`artifacts/screenshots/oneplus-v3-dashboard.png`，PASS。
- 快速开始“新学生”→添加学生弹窗：PASS；字段和保存按钮可见。
- 保存测试学生后进入 Student Workspace：PASS；概览、快速续课、成绩趋势、知识点掌握、快速记录、时间线均可见。
- 安装过程遇到 ColorOS 安全安装引导，已由 ADB 自动点击“继续安装”完成，无需用户操作。
- `AndroidRuntime:E`、`Room:E` 过滤日志未发现崩溃或迁移错误。

## 遗留

- UI 自动化和 Room instrumentation 测试尚未补齐；当前以 JVM unit test、构建、ADB 真机证据覆盖。
- 部分旧源码字符串编码污染仍需清理。
- 正式 release 签名材料未提供，当前 release APK 是 unsigned。
