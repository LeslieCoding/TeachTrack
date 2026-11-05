# TeachTrack 开发记录：2026-09-08 / Round 12

## 本轮目标

完成最后一轮质量验证：补齐 Compose 首页自动化测试、确认一加手机真实安装路径、归档 Debug/Release/Test APK、截取最终屏幕并记录遗留风险。

## 开发内容

1. 在 `app/build.gradle.kts` 为 AndroidTest 源集补充 Compose BOM、`ui-test-junit4` 和 debug test manifest。
2. 新增 `TeachTrackComposeInstrumentedTest`：在真实 Android Activity 上断言首页标题“今天关注谁？”和“快速开始”可见。
3. 保留并继续运行 Room 内存数据库测试，覆盖学生、知识点、掌握状态、知识历史事件的写入/读取闭环。
4. 重新构建并归档：
   - `artifacts/apk/TeachTrack-debug.apk`
   - `artifacts/apk/TeachTrack-release-unsigned.apk`
   - `artifacts/apk/TeachTrack-androidTest-debug.apk`
5. 在唯一指定测试设备 OnePlus PKG110（serial `92f73a16`）上处理 ColorOS 安装引导并启动最新 Debug APK。
6. 截取最终首页：`artifacts/screenshots/oneplus-final-dashboard.png`；保存 UI 树：`artifacts/oneplus-final-ui.xml`。
7. 修正 AndroidManifest 中曾被错误编码的应用显示名称，改为 XML 字符实体形式的“师迹 · TeachTrack”，并重新通过 Debug/Release 构建。

## 验证结果

- `testDebugUnitTest`：PASS。
- `assembleDebug`：PASS。
- `assembleDebugAndroidTest`：PASS。
- `assembleRelease`：PASS，输出为 unsigned release APK。
- `connectedDebugAndroidTest`：PASS，输出 `Finished 2 tests on PKG110 - 16`。
- 真实设备截图确认：绿色主题、首页 Dashboard、今日课程空状态、在读/待处理统计、快速开始、底部导航均正常显示；未出现白屏、崩溃或布局截断。
- 监测脚本持续运行，固定目标为 OnePlus `92f73a16`，间隔 30 分钟并写入 `logs/monitor.log`。

## 解决的问题

- 之前 AndroidTest 安装被 ColorOS 安装确认页拦截，已通过 adb 自动完成继续安装/完成操作，随后两个测试均通过。
- Compose UI 依赖第一次缺少 BOM 版本，已补充测试源集 BOM，构建恢复成功。

## 当前遗留

- 未配置正式签名密钥，因此 Release 仍是 unsigned；不能在没有用户签名材料的情况下伪造可发布签名。
- 反馈模板目前支持创建和展示基础模板，变量替换、编辑、删除仍可作为后续增强。
- ProblemRecord 已从课程问题自动生成并在学生工作台展示，独立状态编辑仍可继续增强。
- 本轮遵循最新指令只测试 OnePlus，未再次操作用户需要保留使用的平板。
