# TeachTrack 开发记录：2026-09-08 / Round 17

## 本轮目标

补齐学生列表的真实筛选/排序交互，并将问题记录状态编辑接入学生工作台。

## 修改

- 学生搜索支持姓名、学校、科目。
- 增加在读、暂停、结束筛选。
- 增加按姓名/最近更新时间排序。
- 问题记录点击后打开状态编辑弹窗，可选待复习、复习中、已改善、已解决并保存备注。
- 状态更新通过 Room DAO → Repository → ViewModel 持久化，不使用临时 UI 状态冒充保存。

## 验证

- 首次构建发现缺少 `horizontalScroll` import，已定位并修复。
- `testDebugUnitTest`：PASS。
- `assembleDebug`：PASS。
- `assembleRelease`：PASS。
- `connectedDebugAndroidTest`：PASS，`Finished 3 tests on PKG110 - 16`。
- 最新 Debug APK 已安装到 OnePlus；首页截图和学生筛选页截图均已人工查看。

## 证据

- `artifacts/screenshots/oneplus-final-dashboard.png`
- `artifacts/screenshots/oneplus-students-filter.png`
- `artifacts/oneplus-final-ui.xml`
- `artifacts/oneplus-students-ui.xml`

## 遗留

- 模板编辑/删除、附件选择/预览和完整两轮手工业务流仍需继续收敛。
