# TeachTrack 验收标准

## 功能验收

- AC-001 新增学生、保存、重启后仍存在。
- AC-002 从学生页开始课程，能带入上一节课的 Next Lesson Plan，并保存 Timeline。
- AC-003 录入考试与成绩，得分率和趋势来自真实 Room 数据。
- AC-004 知识点掌握变化产生历史事件；作业、问题、沟通、计划和待办可保存。
- AC-005 Dashboard、学生工作台、时间线、日历、统计、备份恢复和 CSV 导出可操作。

## 质量验收

- AC-006 Unit、DAO/Repository、Compose/UI、性能和 instrumentation 测试通过。
- AC-007 Room 使用显式迁移，禁止默认破坏性迁移。
- AC-008 OPPO Pad 横屏/竖屏截图无明显错位、截断或白屏；完成两轮功能/真实教师流程回归。
- AC-009 Debug APK、Release APK、README、架构/数据库说明、测试记录、已知问题和备份说明交付。

## 当前证据规则

每项验收必须关联命令输出、测试结果、截图/UI dump 或可复现的设备操作；未验证项保持 `PARTIAL`/`NOT_VERIFIED`，不得用设计文档替代运行证据。
