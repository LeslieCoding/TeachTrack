# TeachTrack 持续开发记录（第三轮）

## 完成内容

- 完整 JSON 备份导出：通过 Android SAF 保存。
- JSON 备份恢复：按 ID 合并写入 Room，不清空当前数据。
- 学生成绩 CSV 导出：包含学生、考试、日期、科目、得分、满分、得分率、排名。
- 设置页接入导出备份、恢复备份、导出成绩 CSV。
- Room schemaLocation 已配置到 schemas/，不再依赖破坏性迁移。
- 增加 ScoreRateTest：3 个单元测试全部通过。

## 构建结果

- `testDebugUnitTest`：通过。
- `assembleDebug`：通过。
- `assembleRelease`：通过，当前 Release 为 unsigned。
- OPPO Pad `2d64f765 / OPD2101`：最新 Debug 已安装，设置页入口通过 UI dump 验证。

## 仍需继续

- 恢复前显示备份日期和数据数量的二次确认 Dialog。
- DAO/Repository/ViewModel 测试覆盖扩展。
- 成绩趋势图、知识点树与历史可视化。
- 日历聚合课程/考试/作业截止/待办。
- 迁移版本测试、性能数据集测试、两轮完整教师流程测试。
- 正式 Release 签名需要 keystore 策略。
