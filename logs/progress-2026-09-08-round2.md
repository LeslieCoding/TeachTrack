# TeachTrack 持续开发记录（第二轮）

## 本轮完成

1. 将计划、统计、设置从占位页替换为真实 Compose 页面。
2. 计划页可新增待办，数据写入 Room tasks。
3. 统计页读取在读学生、课程记录、成绩记录、待处理事项真实数量。
4. 学生工作台增加知识点掌握、作业、家校沟通真实录入入口，并显示摘要。
5. 实现完整 JSON 备份导出，覆盖学生、课程、考试、成绩、知识点、掌握、作业、沟通、计划、待办、家长联系人。
6. 实现 JSON 恢复合并，按 ID 写入，不清空当前数据库。
7. 实现学生成绩 CSV 导出，得分率按 score/fullScore 生成。
8. Debug 与 Release 构建均成功；最新 Debug 安装至 OPPO Pad serial 2d64f765。

## OPPO Pad 验证

- 计划页切换成功，空状态与新待办入口可见。
- 学生列表与 Student Workspace 可见，知识点/作业/沟通入口可见。
- 设置页三个数据操作入口可见。
- 设备：OPD2101，1600×2560 竖屏 UI 坐标，测试期间未操作 PKG110。

## 当前遗留

- Room schema export 仍有编译警告，需要接入 schemaLocation 与正式 migration 文件。
- 需要补充单元、DAO、Repository、ViewModel 测试。
- 需要完成真实导出文件内容回读验证、恢复前数量确认 Dialog、CSV 在表格软件中的兼容性验证。
- Release 当前为 unsigned，正式签名需要用户提供 keystore 或明确签名策略。
