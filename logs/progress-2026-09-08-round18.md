# TeachTrack 开发记录：2026-09-08 / Round 18

## 本轮目标

把学生级附件从“数据库/备份预留”推进到可操作的本地文件选择和列表展示。

## 修改

- DAO 新增按学生观察附件的 Flow。
- Repository 和 ViewModel 新增附件读取/保存路径。
- 学生工作台快速记录区增加“附件”。
- 接入 Android SAF `OpenDocument` 文件选择器，保存本地 URI、文件名和 MIME 类型。
- 请求持久化读取权限；不上传网络。
- 学生概览显示最近附件列表。

## 验证

- `testDebugUnitTest`：PASS。
- `assembleDebug`：PASS。
- `assembleRelease`：PASS。
- `connectedDebugAndroidTest`：PASS，`Finished 3 tests on PKG110 - 16`。
- 测试安装引导由 adb 自动处理，无需用户操作。

## 遗留

- 附件预览/删除尚未加入；当前先保证本地选择、持久化、备份和恢复链路完整。
- 正式 Release 签名仍需外部签名材料。
