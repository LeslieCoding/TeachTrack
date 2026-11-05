# TeachTrack 两轮测试计划

## 第一轮：模块功能

逐项验证学生、课程、快速续课、时间线、考试成绩、知识点掌握历史、作业、沟通、计划、Dashboard、日历、统计、备份恢复和 CSV 导出；每项检查保存、重启后的持久化、空状态、错误状态与取消行为。

## 第二轮：真实教师流程

添加学生 → 记录课程 → 标记知识点 → 记录问题 → 布置作业 → 家长沟通 → 设置下节课 → 快速续课 → 录入成绩 → 查看得分率与时间线 → 导出备份。

## 设备边界

当前及后续真机回归目标为 OnePlus 手机，型号 PKG110，serial `92f73a16`。OPPO Pad `2d64f765` 保留为历史证据，不再操作，直到用户再次切换目标。每轮保存截图与结果到 `artifacts/screenshots` 和本目录。

## 自动化证据

- JVM：`testDebugUnitTest`，覆盖得分率归一化、模板渲染、掌握等级、Copilot 命令验证。
- Android instrumentation：`connectedDebugAndroidTest`；如果多台设备同时在线，必须显式指定 OnePlus serial `92f73a16`，禁止向 OPPO Pad 发送命令。
- OnePlus 手工检查：安装、启动、横屏/竖屏、截图视觉检查、快速开始、Copilot 预览、学生工作台和持久化。
