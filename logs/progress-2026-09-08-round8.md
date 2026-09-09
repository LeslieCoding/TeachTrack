# TeachTrack 开发记录：2026-09-08 / Round 8

## 本轮实现

- 计划页读取 Room 中的全部 `LessonPlanEntity`。
- 日历同时展示课程、下一节课计划和待办，不再只展示课程/待办。
- 待办列表在存在计划时展示计划数量摘要。

## 验证

- `testDebugUnitTest assembleDebug`：PASS。
- 最新 Debug APK 安装到 OnePlus `92f73a16`：Success。
- 一加真机点击底部“计划”，UI dump 显示“计划 / 待办 / 日历 / 新待办”：PASS。
- 真机 `AndroidRuntime:E` / `Room:E`：无错误。

## 当前剩余

- 课后反馈模板、家长联系人独立编辑、ProblemRecord 独立列表和自动化 instrumentation 测试仍需继续。
