# TeachTrack 开发记录：2026-09-08 / Round 14

## 本轮目标

让标签、学生标签和附件这三个已建模实体进入完整本地备份链路，避免恢复时遗漏扩展数据。

## 修改

- `TeachTrackDao` 新增 tags、student_tags、attachments 的全量读取与 upsert 接口。
- `TeachTrackRepository.importBackupJson` 新增三类数据的兼容导入；旧备份没有这些数组时安全跳过。
- `TeachTrackRepository.exportBackupJson` 新增三类数据数组。
- 不修改数据库版本，因这些实体和表已经在 schema v3 与 migration 1→2 中存在。
- 重新归档 Debug、Release unsigned、AndroidTest APK。

## 验证

- `testDebugUnitTest`：PASS。
- `assembleDebug`：PASS。
- `assembleRelease`：PASS。
- `connectedDebugAndroidTest`：PASS，`Finished 3 tests on PKG110 - 16`。
- 一加设备仍为唯一真机目标；ColorOS 安装确认由 adb 自动处理。

## 风险与遗留

- 当前没有 UI 创建标签或选择附件，但备份链路已具备向后兼容的存储能力；附件选择器属于后续 P1 UI 工作。
- 正式签名仍需要外部签名材料。
