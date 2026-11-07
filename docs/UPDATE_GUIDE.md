# App 更新与发布

## App 内检查更新

入口：设置 → 关于 → 检查更新。

- 检查的是 `https://github.com/LeslieCoding/TeachTrack/releases`
- 只接受附带 `.apk` 资产的 Release
- 版本比较支持 `3.0.0-alpha.4` 这类预发布版本，并正确处理数字顺序
- 发现新版本后显示更新说明，下载完成后交给 Android 系统安装器
- 下载的 APK 保存在应用私有目录，安装完成前不会被系统缓存清理
- GitHub 被拦截或网络不可达时，会显示明确错误和 Releases 链接，不会误报“已是最新”

## 发布新版本

1. 修改版本号（默认值在 `app/build.gradle.kts`，也可用 Gradle 参数覆盖）：
   ```powershell
   ./gradlew.bat -PteachTrackVersionCode=300005 -PteachTrackVersionName=3.0.0-alpha.5 :app:assembleDebug
   ```
2. 构建并验证：
   ```powershell
   ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
   ```
3. 创建 GitHub Release，tag 使用 `v` 前缀，并上传 APK：
   ```powershell
   gh release create v3.0.0-alpha.5 app/build/outputs/apk/debug/app-debug.apk `
     --repo LeslieCoding/TeachTrack --title "TeachTrack 3.0.0-alpha.5" --prerelease
   ```
4. 在旧版本设备打开“检查更新”，确认弹窗显示新版本，再完成下载和安装。

## 注意事项

- Release 必须带 APK，只有源码压缩包不会触发更新
- tag 必须随版本递增；不要复用旧 tag 重发不同 APK
- 正式签名尚未配置，当前 APK 使用 debug 签名，仅适合内部设备
- Quick Tunnel 重启会换地址，更新 App 不会自动修改题库服务器地址
