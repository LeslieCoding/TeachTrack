# TeachTrack 3.0.0-alpha.1 交付状态

## 已验证

- Kotlin / Compose / Room / MVVM 基础架构
- 学生、课程、成绩、考试、知识点、作业、沟通、计划、待办、Timeline
- Local First 与 Workspace 导入/导出基础能力
- Dashboard 限量查询
- 自动 SyncEvent、revision、冲突文件、Tombstone
- 备份往返、损坏备份回滚、Workspace 健康检查
- 一加真机 Android instrumentation：11 项通过
- Debug APK 与 Release 未签名 APK 已生成

## 当前交付物

- `artifacts/deliverables/TeachTrack-3.0.0-alpha.1-debug.apk`
- `artifacts/deliverables/TeachTrack-3.0.0-alpha.1-release-unsigned.apk`

## 外部依赖 / 未宣称完成

- OPPO Pad 当前未连接，不能新增 OPPO 回归证据；已有历史证据保留。
- Release 签名需要用户提供签名密钥。
- 真实云端 Provider/OAuth 需要厂商账户、客户端注册和凭据。
- 完整冲突编辑 UI、桌面客户端、云同步和 AES 加密属于后续阶段。
